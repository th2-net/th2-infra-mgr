/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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
import com.exactpro.th2.inframgr.errors.NotAcceptableException;
import com.exactpro.th2.inframgr.errors.ServiceException;
import com.exactpro.th2.inframgr.k8s.K8sCustomResource;
import com.exactpro.th2.inframgr.models.RequestEntry;
import com.exactpro.th2.inframgr.models.RequestOperation;
import com.exactpro.th2.inframgr.models.ResourceEntry;
import com.exactpro.th2.inframgr.repository.RepositoryUpdateEvent;
import com.exactpro.th2.inframgr.util.SchemaErrorPrinter;
import com.exactpro.th2.inframgr.util.cfg.GitCfg;
import com.exactpro.th2.infrarepo.InconsistentRepositoryStateException;
import com.exactpro.th2.infrarepo.SchemaUtils;
import com.exactpro.th2.infrarepo.git.Gitter;
import com.exactpro.th2.infrarepo.git.GitterContext;
import com.exactpro.th2.infrarepo.repo.Repository;
import com.exactpro.th2.infrarepo.repo.RepositoryResource;
import com.exactpro.th2.infrarepo.repo.RepositorySnapshot;
import com.exactpro.th2.infrarepo.settings.RepositorySettingsSpec;
import com.exactpro.th2.validator.SchemaValidator;
import com.exactpro.th2.validator.ValidationReport;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
public class SchemaController {

    public static final String SCHEMA_EXISTS = "SCHEMA_EXISTS";

    private static final String REPOSITORY_ERROR = "REPOSITORY_ERROR";

    public static final String BAD_RESOURCE_NAME = "BAD_RESOURCE_NAME";

    public static final String SOURCE_BRANCH = "master";

    private static final Logger logger = LoggerFactory.getLogger(SchemaController.class);

    @GetMapping("/schemas")
    @ResponseBody
    public Set<String> getAvailableSchemas() throws ServiceException {

        try {
            GitterContext ctx = GitterContext.getContext(Config.getInstance().getGit());
            Set<String> schemas = ctx.getBranches();
            schemas.remove(SOURCE_BRANCH);
            return schemas;
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
    }

    @GetMapping("/schema/{name}")
    @ResponseBody
    public SchemaControllerResponse getSchemaFiles(@PathVariable(name = "name") String schemaName) throws Exception {

        if (schemaName.equals(SOURCE_BRANCH)) {
            throw new NotAcceptableException(REPOSITORY_ERROR, "Not Allowed");
        }

        GitCfg gitConfig = Config.getInstance().getGit();
        GitterContext ctx = GitterContext.getContext(gitConfig);
        final Gitter gitter = ctx.getGitter(schemaName);
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
    public SchemaControllerResponse createSchema(@PathVariable(name = "name") String schemaName) throws Exception {

        if (schemaName.equals(SOURCE_BRANCH)) {
            throw new NotAcceptableException(REPOSITORY_ERROR, "Not Allowed");
        }

        if (!K8sCustomResource.isSchemaNameValid(schemaName)) {
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid schema name");
        }

        Config config = Config.getInstance();
        GitCfg gitConfig = config.getGit();
        GitterContext ctx = GitterContext.getContext(gitConfig);

        if (schemaAlreadyExists(schemaName, ctx)) {
            throw new NotAcceptableException(SCHEMA_EXISTS, "Error creating schema. schema already exists");
        }

        // create schema
        final Gitter gitter = ctx.getGitter(schemaName);
        try {
            RepositorySnapshot snapshot;
            try {
                gitter.lock();
                gitter.createBranch(SOURCE_BRANCH);
                snapshot = Repository.getSnapshot(gitter);
            } finally {
                gitter.unlock();
            }

            issueRepoUpdateEvent(schemaName, snapshot);

            return new SchemaControllerResponse(snapshot);

        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            logger.error("Exception creating schema \"{}\"", schemaName, e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
    }

    private boolean schemaAlreadyExists(String schemaName, GitterContext ctx) {
        Set<String> branches;
        try {
            branches = ctx.getBranches();
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
        return branches.contains(schemaName);
    }

    @PostMapping("/schema/{name}")
    @ResponseBody
    public SchemaControllerResponse updateSchema(@PathVariable(name = "name") String schemaName,
                                                 @RequestBody String requestBody
    ) throws Exception {

        if (schemaName.equals(SOURCE_BRANCH)) {
            throw new NotAcceptableException(REPOSITORY_ERROR, "Not Allowed");
        }

        // deserialize request body
        List<RequestEntry> operations;
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .registerModule(new KotlinModule.Builder().build());
            operations = mapper.readValue(requestBody, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }

        validateResourceNames(operations);

        Config config = Config.getInstance();
        GitCfg gitConfig = config.getGit();
        GitterContext ctx = GitterContext.getContext(gitConfig);

        if (!schemaAlreadyExists(schemaName, ctx)) {
            throw new ServiceException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.name(), "Schema does not exist");
        }

        //validate schema and apply updates if valid
        try {
            final Gitter gitter = ctx.getGitter(schemaName);
            RepositorySnapshot snapshot;
            String commitRef;
            try {
                gitter.lock();
                snapshot = Repository.getSnapshot(gitter);
                // combine recent validations and current snapshot and validate potential schema.
                var validationContext = SchemaValidator.validate(
                        schemaName,
                        config.getKubernetes().getNamespacePrefix(),
                        toCombinedRepositoryMap(snapshot, operations)
                );
                if (!validationContext.isValid()) {
                    // do not update repository and kubernetes if requested changes contain errors.
                    logger.error("Schema \"{}\" contains errors, update request will be ignored", schemaName);
                    ValidationReport report = validationContext.getReport();
                    SchemaErrorPrinter.printErrors(report);
                    return new SchemaControllerResponse(report);
                }
                // continue with update if schema is validated
                commitRef = updateRepository(gitter, operations);
                snapshot = Repository.getSnapshot(gitter);
            } finally {
                gitter.unlock();
            }

            if (commitRef == null) {
                logger.info("Nothing changed, leaving");
            } else {
                issueRepoUpdateEvent(schemaName, snapshot);
            }
            return new SchemaControllerResponse(snapshot);
        } catch (ServiceException se) {
            throw se;
        } catch (Exception e) {
            logger.error("Exception updating schema \"{}\" request", schemaName, e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
        }
    }

    private void issueRepoUpdateEvent(String schemaName, RepositorySnapshot snapshot)
            throws JsonProcessingException {
        SchemaEventRouter router = SchemaEventRouter.getInstance();
        RepositoryUpdateEvent event = new RepositoryUpdateEvent(schemaName, snapshot.getCommitRef());
        RepositorySettingsSpec rs = snapshot.getRepositorySettingsSpec();
        event.setSyncingK8s(!(rs != null && (rs.isK8sPropagationDenied()
                || rs.isK8sSynchronizationRequired())));
        router.addEvent(schemaName, event);
    }

    private String updateRepository(Gitter gitter, List<RequestEntry> operations) throws ServiceException {

        String branchName = gitter.getBranch();
        try {
            for (RequestEntry entry : operations) {
                switch (entry.getOperation()) {
                    case add:
                        Repository.add(gitter, entry.getPayload().toRepositoryResource());
                        break;
                    case update:
                        Repository.update(gitter, entry.getPayload().toRepositoryResource());
                        break;
                    case remove:
                        Repository.remove(gitter, entry.getPayload().toRepositoryResource());
                        break;
                }
            }
            return gitter.commitAndPush("schema update");

        } catch (InconsistentRepositoryStateException irse) {
            // this exception is thrown when inconsistent state of git repository is expected
            // discard local cache and re-download repository
            logger.error("Inconsistent repository state exception for branch \"{}\"", branchName, irse);

            var se = new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, irse.getMessage());
            se.addSuppressed(irse);

            try {
                gitter.recreateCache();
            } catch (Exception re) {
                logger.error("Exception recreating repository's local cache for branch \"{}\"", branchName, re);
                se.addSuppressed(re);
            }
            throw se;

        } catch (Exception e) {
            logger.error("Exception updating repository for branch \"{}\"", branchName, e);
            gitter.reset();
            throw new NotAcceptableException(REPOSITORY_ERROR, e.getMessage());
        }
    }

    public static void validateResourceNames(List<RequestEntry> operations) {

        Set<String> names = new HashSet<>();

        for (RequestEntry entry : operations) {
            String resourceName = entry.getPayload().getName();

            if (!K8sCustomResource.isNameValid(resourceName)) {
                logger.error("Invalid resource name: \"{}\"", resourceName);
                throw new NotAcceptableException(BAD_RESOURCE_NAME, String.format(
                        "Invalid resource name : \"%s\" (%s)"
                        , entry.getPayload().getName()
                        , entry.getPayload().getKind().kind()
                ));
            }

            if (!names.add(resourceName)) {
                logger.error("Multiple operations on the same resource: \"{}\"", resourceName);
                throw new NotAcceptableException(REPOSITORY_ERROR, "Multiple operation on the resource");
            }
        }
    }

    public static Map<String, Map<String, RepositoryResource>> toCombinedRepositoryMap(RepositorySnapshot snapshot,
                                                                                       List<RequestEntry> operations) {
        Set<RepositoryResource> resources = snapshot.getResources();
        Map<String, Map<String, RepositoryResource>> repositoryMap = SchemaUtils.convertToRepositoryMap(resources);
        for (RequestEntry entry : operations) {
            RequestOperation operation = entry.getOperation();
            ResourceEntry payload = entry.getPayload();
            String entryName = payload.getName();
            String entryKind = payload.getKind().kind();
            if (operation.equals(RequestOperation.add) || operation.equals(RequestOperation.update)) {
                repositoryMap
                        .computeIfAbsent(entryKind, k -> new HashMap<>())
                        .put(entryName, payload.toRepositoryResource());
            } else if (operation.equals(RequestOperation.remove)) {
                repositoryMap
                        .computeIfAbsent(entryKind, k -> new HashMap<>())
                        .remove(entryName);
            }
        }
        return repositoryMap;
    }
}
