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
package com.exactpro.th2.inframgr;

import com.exactpro.th2.inframgr.errors.BadRequestException;
import com.exactpro.th2.inframgr.errors.K8sProvisioningException;
import com.exactpro.th2.inframgr.errors.NotAcceptableException;
import com.exactpro.th2.inframgr.errors.ServiceException;
import com.exactpro.th2.inframgr.initializer.SchemaInitializer;
import com.exactpro.th2.inframgr.k8s.K8sCustomResource;
import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.inframgr.models.RequestEntry;
import com.exactpro.th2.inframgr.repository.RepositoryUpdateEvent;
import com.exactpro.th2.inframgr.util.Stringifier;
import com.exactpro.th2.infrarepo.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Controller
public class SchemaController {

    public static final String SCHEMA_EXISTS = "SCHEMA_EXISTS";
    public static final String REPOSITORY_ERROR = "REPOSITORY_ERROR";
    public static final String BAD_RESOURCE_NAME = "BAD_RESOURCE_NAME";
    private static final String SOURCE_BRANCH = "master";

    private final Logger logger = LoggerFactory.getLogger(SchemaController.class);

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
    public SchemaControllerResponse getSchemaFiles(@PathVariable(name="name") String schemaName) throws Exception {

        if (schemaName.equals(SOURCE_BRANCH))
            throw new NotAcceptableException(REPOSITORY_ERROR, "Not Allowed");

        Config.GitConfig gitConfig = Config.getInstance().getGit();
        final Gitter gitter = Gitter.getBranch(gitConfig, schemaName);
        try {
            gitter.lock();
            return new SchemaControllerResponse(Repository.getSnapshot(gitter));
        } catch (RefNotAdvertisedException | RefNotFoundException e) {
            throw new ServiceException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.name(), "schema does not exists");
        } catch (Exception e) {
            logger.error("Exception retrieving schema {} from repository", schemaName, e);
            throw new NotAcceptableException(REPOSITORY_ERROR, e.getMessage());
        } finally {
            gitter.unlock();
        }
    }

    @PutMapping("/schema/{name}")
    @ResponseBody
    public SchemaControllerResponse createSchema(@PathVariable(name="name") String schemaName) throws Exception {

        if (schemaName.equals(SOURCE_BRANCH))
            throw new NotAcceptableException(REPOSITORY_ERROR, "Not Allowed");

        if (!K8sCustomResource.isNameValid(schemaName))
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid schema name");

        Config config = Config.getInstance();
        Config.GitConfig gitConfig = config.getGit();

        // check if the schema already exists
        Set<String> branches;
        try {
             branches = Gitter.getBranches(gitConfig);
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
        if (branches.contains(schemaName))
            throw new NotAcceptableException(SCHEMA_EXISTS, "error crating schema. schema already exists");

        // create schema
        final Gitter gitter = Gitter.getBranch(gitConfig, schemaName);
        try {
            RepositorySnapshot snapshot;
            try {
                gitter.lock();
                gitter.createBranch(SOURCE_BRANCH);
                snapshot = Repository.getSnapshot(gitter);
            } finally {
                gitter.unlock();
            }


            SchemaEventRouter router = SchemaEventRouter.getInstance();
            RepositoryUpdateEvent event = new RepositoryUpdateEvent(schemaName, snapshot.getCommitRef());
            RepositorySettings rs = snapshot.getRepositorySettings();
            event.setSyncingK8s(!(rs != null && (rs.isK8sPropagationDenied() || rs.isK8sSynchronizationRequired())));
            router.addEvent(event);

            return new SchemaControllerResponse(snapshot);

        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            logger.error("Exception creating schema \"{}\"", schemaName, e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
    }


    @PostMapping("/schema/{name}")
    @ResponseBody
    public SchemaControllerResponse updateSchema(@PathVariable(name="name") String schemaName, @RequestBody String requestBody)
            throws Exception {

        if (schemaName.equals(SOURCE_BRANCH))
            throw new NotAcceptableException(REPOSITORY_ERROR, "Not Allowed");

        // deserialize request body
        List<RequestEntry> operations;
        try {
            ObjectMapper mapper = new ObjectMapper();
            operations = mapper.readValue(requestBody, new TypeReference<>() {});
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }

        validateResourceNames(operations);

        Config config = Config.getInstance();
        Config.GitConfig gitConfig = config.getGit();

        // check if the schema exists
        Set<String> branches;
        try {
            branches = Gitter.getBranches(gitConfig);
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
        if (!branches.contains(schemaName))
            throw new ServiceException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.name(), "schema does not exists");

        // apply updates
        try {
            final Gitter gitter = Gitter.getBranch(gitConfig, schemaName);
            RepositorySnapshot snapshot;
            String commitRef;
            boolean wasPropagated = false;

            try {
                gitter.lock();

                snapshot = Repository.getSnapshot(gitter);
                RepositorySettings repoSettings = snapshot.getRepositorySettings();
                if (repoSettings != null)
                    wasPropagated = repoSettings.isK8sSynchronizationRequired();

                commitRef = updateRepository(gitter, operations);
                snapshot = Repository.getSnapshot(gitter);
            } finally {
                gitter.unlock();
            }

            if (commitRef == null) {
                logger.info("Nothing changed, leaving");
            } else {
                SchemaEventRouter router = SchemaEventRouter.getInstance();
                RepositoryUpdateEvent event = new RepositoryUpdateEvent(schemaName, commitRef);

                RepositorySettings repoSettings = snapshot.getRepositorySettings();
                boolean propagating = (repoSettings != null) && repoSettings.isK8sSynchronizationRequired();
                if ((propagating && !wasPropagated) || (repoSettings != null && repoSettings.isK8sPropagationDenied())) {
                    // we need to resynchronize whole schema
                    // delegate this job to K8sSynchronization
                    event.setSyncingK8s(false);
                    router.addEvent(event);
                    return new SchemaControllerResponse(snapshot);
                }

                event.setSyncingK8s(true);
                router.addEvent(event);

                if (propagating)
                    synchronizeWithK8s(config.getKubernetes(), operations, schemaName);
            }

            return new SchemaControllerResponse(snapshot);
        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            logger.error("Exception updating schema \"{}\" request", schemaName, e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
    }


    private void synchronizeWithK8s(Config.K8sConfig k8sConfig, List<RequestEntry> operations, String schemaName)
            throws ServiceException {

        try (Kubernetes kube = new Kubernetes(k8sConfig, schemaName)) {

            SchemaInitializer.ensureSchema(schemaName, kube);
            K8sProvisioningException k8se = null;

            for (RequestEntry entry : operations)
                if (entry.getPayload().getKind().isK8sResource()) {
                    try {
                        Stringifier.stringify(entry.getPayload().getSpec());
                        RepositoryResource resource = entry.getPayload().toRepositoryResource();
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
            logger.error("Exception provisioning resource(s) to Kubernetes", e);
            ServiceException se = new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
            se.addSuppressed(e);
        }
    }


    private String updateRepository(Gitter gitter, List<RequestEntry> operations) throws ServiceException {

        String branchName = gitter.getBranch();
        try {

            try {
                for (RequestEntry entry : operations)
                    switch (entry.getOperation()) {
                        case add:
                            Repository.add(gitter.getConfig(), branchName, entry.getPayload().toRepositoryResource());
                            break;
                        case update:
                            Repository.update(gitter.getConfig(), branchName, entry.getPayload().toRepositoryResource());
                            break;
                        case remove:
                            Repository.remove(gitter.getConfig(), branchName, entry.getPayload().toRepositoryResource());
                            break;
                    }
                return gitter.commitAndPush("schema update");

            } catch (InconsistentRepositoryStateException irsePassThrough) {
                // pass this exception for processing on outer level
                throw irsePassThrough;
            } catch (Exception e) {
                logger.error("Exception updating repository for branch \"{}\"", branchName, e);
                gitter.reset();
                throw new NotAcceptableException(REPOSITORY_ERROR, e.getMessage());
            }

        } catch (InconsistentRepositoryStateException irse) {
            // this exception is thrown when inconsistent state of git repository is expected
            // discard local cache and re-download repository
            logger.error("Inconsistent repository state exception for branch \"{}\"", branchName, irse);

            ServiceException se = new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, irse.getMessage());
            se.addSuppressed(irse);

            try {
                gitter.recreateCache();
            } catch (Exception re) {
                logger.error("Exception recreating repository's local cache for branch \"{}\"", branchName, re);
                se.addSuppressed(re);
            }
            throw se;
        }
    }


    private void validateResourceNames(List<RequestEntry> operations) {

        for (RequestEntry entry : operations)
            if (!K8sCustomResource.isNameValid(entry.getPayload().getName()))
                throw new NotAcceptableException(BAD_RESOURCE_NAME, String.format(
                        "Invalid resource name : \"%s\" (%s)"
                        , entry.getPayload().getName()
                        , entry.getPayload().getKind().kind()
                ));
    }
}