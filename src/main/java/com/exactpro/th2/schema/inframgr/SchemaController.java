/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.exactpro.th2.schema.inframgr;

import com.exactpro.th2.schema.inframgr.errors.BadRequestException;
import com.exactpro.th2.schema.inframgr.errors.K8sProvisioningException;
import com.exactpro.th2.schema.inframgr.errors.NotAcceptableException;
import com.exactpro.th2.schema.inframgr.errors.ServiceException;
import com.exactpro.th2.schema.inframgr.initializer.SchemaInitializer;
import com.exactpro.th2.schema.inframgr.k8s.K8sCustomResource;
import com.exactpro.th2.schema.inframgr.k8s.Kubernetes;
import com.exactpro.th2.schema.inframgr.models.*;
import com.exactpro.th2.schema.inframgr.repository.Gitter;
import com.exactpro.th2.schema.inframgr.repository.Repository;
import com.exactpro.th2.schema.inframgr.repository.RepositoryUpdateEvent;
import com.exactpro.th2.schema.inframgr.util.Stringifier;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Controller
public class SchemaController {

    public static final String SCHEMA_EXISTS = "SCHEMA_EXISTS";
    public static final String REPOSITORY_ERROR = "REPOSITORY_ERROR";
    public static final String BAD_RESOURCE_NAME = "BAD_RESOURCE_NAME";
    private static final String SOURCE_BRANCH = "master";

    private Logger logger = LoggerFactory.getLogger(SchemaController.class);

    @GetMapping("/schemas")
    @ResponseBody
    public Set<String> getAvailableSchemas() throws ServiceException  {

        try {
            Set<String> schemas = Gitter.getBranches(Config.getInstance().getGit());
            schemas.remove(SOURCE_BRANCH);
            return schemas;
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
    }

    @GetMapping("/schema/{name}")
    @ResponseBody
    public RepositorySnapshot getSchemaFiles(@PathVariable(name="name") String name) throws Exception {

        if (name.equals(SOURCE_BRANCH))
            throw new NotAcceptableException(REPOSITORY_ERROR, "Not Allowed");

        Config.GitConfig config = Config.getInstance().getGit();
        Gitter gitter = Gitter.getBranch(config, name);
        try {
            gitter.lock();
            RepositorySnapshot snapshot = Repository.getSnapshot(gitter);
            return snapshot;
        } catch (Exception e) {
            throw new NotAcceptableException(REPOSITORY_ERROR, e.getMessage());
        } finally {
            gitter.unlock();
        }
    }

    @PutMapping("/schema/{name}")
    @ResponseBody
    public RepositorySnapshot createSchema(@PathVariable(name="name") String name) throws Exception {

        if (name.equals(SOURCE_BRANCH))
            throw new NotAcceptableException(REPOSITORY_ERROR, "Not Allowed");

        Pattern pattern = Pattern.compile(K8sCustomResource.RESOURCE_NAME_REGEXP);
        if (!pattern.matcher(name).matches())
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid schema name");


        Config config = Config.getInstance();
        Config.GitConfig git = config.getGit();

        // check if the schema already exists
        Set<String> branches;
        try {
             branches = Gitter.getBranches(git);
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
        if (branches.contains(name))
                throw new NotAcceptableException(SCHEMA_EXISTS, "error crating schema. schema already exists");


        // create schema
        Gitter gitter = Gitter.getBranch(git, name);
        try {
            RepositorySnapshot snapshot;
            Set<ResourceEntry> resources;
            String commitRef;
            try {
                gitter.lock();
                commitRef = gitter.createBranch(SOURCE_BRANCH);

                snapshot = Repository.getSnapshot(gitter);
                resources = snapshot.getResources();
            } finally {
                gitter.unlock();
            }

            // send repository update event
            SchemaEventRouter router = SchemaEventRouter.getInstance();
            RepositoryUpdateEvent event = new RepositoryUpdateEvent(name, commitRef);
            event.setSyncingK8s(true);
            router.addEvent(event);


            RepositorySettings repoSettings = snapshot.getRepositorySettings();
            if (repoSettings == null || !repoSettings.isK8sPropagationEnabled())
                return snapshot;

            //synchronize with k8s
            try (Kubernetes kube = new Kubernetes(config.getKubernetes(), name);) {

                SchemaInitializer.ensureSchema(name, kube);
                K8sProvisioningException k8se = null;

                for (ResourceEntry entry : resources)
                    if (entry.getKind().isK8sResource()) {
                        try {
                            Stringifier.stringify(entry.getSpec());
                            kube.createOrReplaceCustomResource(new RepositoryResource(entry));
                        } catch (Exception e) {
                            if (k8se == null)
                                k8se = new K8sProvisioningException("Exception provisioning resource(s) to Kubernetes");
                            k8se.addItem(entry);
                            logger.error("Exception provisioning {} resource \"{}\" to Kubernetes ({})"
                                    , entry.getKind().kind()
                                    , entry.getName()
                                    , e.getMessage());
                        }
                    }
                if (k8se != null)
                    throw k8se;

            } catch (Exception e) {
                logger.error("Exception provisioning resource(s) to Kubernetes ({})", e.getMessage());
                throw e;
            }

            return snapshot;
        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
    }


    @PostMapping("/schema/{name}")
    @ResponseBody
    public RepositorySnapshot updateSchema(@PathVariable(name="name") String name, @RequestBody String requestBody) throws Exception {

        if (name.equals(SOURCE_BRANCH))
            throw new NotAcceptableException(REPOSITORY_ERROR, "Not Allowed");

        // deserialize request body
        List<RequestEntry> operations = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            operations = mapper.readValue(requestBody, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }

        // validate resource names
        Pattern regex = Pattern.compile(K8sCustomResource.RESOURCE_NAME_REGEXP);
        for (RequestEntry entry : operations)
            if (!regex.matcher(entry.getPayload().getName()).matches())
                throw new NotAcceptableException(BAD_RESOURCE_NAME, String.format(
                        "Invalid resource name : \"%s\" (%s)"
                        , entry.getPayload().getName()
                        , entry.getPayload().getKind().kind()
                ));

        Config config = Config.getInstance();
        Config.GitConfig git = config.getGit();

        // check if the schema exists
        Set<String> branches;
        try {
            branches = Gitter.getBranches(git);
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
        if (!branches.contains(name))
            throw new ServiceException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.name(), "schema does not exists");

        // update schema
        try {
            Gitter gitter = Gitter.getBranch(git, name);

            String commitRef;
            RepositorySnapshot snapshot;

            boolean wasPropagated = false;
            try {
                gitter.lock();
                gitter.checkout();
                snapshot = Repository.getSnapshot(gitter);
                RepositorySettings repoSettings = snapshot.getRepositorySettings();
                if (repoSettings != null && repoSettings.isK8sPropagationEnabled())
                    wasPropagated = true;

                // apply operations
                try {
                    for (RequestEntry entry : operations) {
                        switch (entry.getOperation()) {
                            case add:
                                Repository.add(git, name, entry.getPayload());
                                break;
                            case update:
                                Repository.update(git, name, entry.getPayload());
                                break;
                            case remove:
                                Repository.remove(git, name, entry.getPayload());
                                break;
                        }
                    }
                } catch (Exception e) {
                    gitter.reset();
                    throw new NotAcceptableException(REPOSITORY_ERROR, e.getMessage());
                }

                commitRef = gitter.commit("schema update");
                snapshot = Repository.getSnapshot(gitter);
            } finally {
                gitter.unlock();
            }

            if (commitRef == null) {
                logger.info("Nothing changed, leaving");
            } else {
                RepositorySettings repoSettings = snapshot.getRepositorySettings();

                SchemaEventRouter router = SchemaEventRouter.getInstance();
                RepositoryUpdateEvent event = new RepositoryUpdateEvent(name, commitRef);

                if (repoSettings != null && repoSettings.isK8sPropagationEnabled() && !wasPropagated) {
                    // we need to resynchronize whole schema
                    // delegate this job to K8sSynchronization
                    event.setSyncingK8s(false);
                    router.addEvent(event);
                    return snapshot;
                }

                event.setSyncingK8s(true);
                router.addEvent(event);

                if (repoSettings == null || !repoSettings.isK8sPropagationEnabled())
                    return snapshot;

                //synchronize with k8s
                try (Kubernetes kube = new Kubernetes(config.getKubernetes(), name);) {

                    SchemaInitializer.ensureSchema(name, kube);
                    K8sProvisioningException k8se = null;

                    for (RequestEntry entry : operations)
                        if (entry.getPayload().getKind().isK8sResource()) {
                            try {
                                Stringifier.stringify(entry.getPayload().getSpec());
                                RepositoryResource resource = new RepositoryResource(entry.getPayload());
                                switch (entry.getOperation()) {
                                    case add:
                                        kube.createCustomResource(resource);
                                        break;
                                    case update:
                                        kube.replaceCustomResource(resource);
                                        break;
                                    case remove:
                                        kube.deleteCustomResource(resource);
                                        break;
                                }
                            } catch (Exception e) {
                                if (k8se == null)
                                    k8se = new K8sProvisioningException("Exception provisioning resource(s) to Kubernetes");
                                k8se.addItem(entry.getPayload());
                                logger.error("Exception provisioning {} resource \"{}\" to Kubernetes"
                                        , entry.getPayload().getKind().kind()
                                        , entry.getPayload().getName()
                                        , e);
                            }
                        }
                    if (k8se != null)
                        throw k8se;
                } catch (Exception e) {
                    logger.error("Exception provisioning resource(s) to Kubernetes ({})", e);
                    throw e;
                }
            }

            return snapshot;
        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            logger.error("Exception provisioning request", e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
    }
}