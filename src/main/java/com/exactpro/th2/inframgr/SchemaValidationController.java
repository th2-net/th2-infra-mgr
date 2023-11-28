/*
 * Copyright 2020-2023 Exactpro (Exactpro Systems Limited)
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
import com.exactpro.th2.inframgr.models.RequestEntry;
import com.exactpro.th2.inframgr.models.RequestOperation;
import com.exactpro.th2.inframgr.models.ResourceEntry;
import com.exactpro.th2.inframgr.util.cfg.GitCfg;
import com.exactpro.th2.infrarepo.SchemaUtils;
import com.exactpro.th2.infrarepo.git.Gitter;
import com.exactpro.th2.infrarepo.git.GitterContext;
import com.exactpro.th2.infrarepo.repo.Repository;
import com.exactpro.th2.infrarepo.repo.RepositoryResource;
import com.exactpro.th2.infrarepo.repo.RepositorySnapshot;
import com.exactpro.th2.validator.SchemaValidationContext;
import com.exactpro.th2.validator.SchemaValidator;
import com.exactpro.th2.validator.ValidationReport;
import com.exactpro.th2.validator.errormessages.BoxResourceErrorMessage;
import com.exactpro.th2.validator.errormessages.LinkErrorMessage;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static com.exactpro.th2.inframgr.SchemaController.*;

@Controller
@SuppressWarnings("unused")
public class SchemaValidationController {

    private static final Logger logger = LoggerFactory.getLogger(SchemaController.class);

    private static final String UNSUPPORTED_OPERATION = "UNSUPPORTED_OPERATION";

    private static final String REPOSITORY_ERROR = "REPOSITORY_ERROR";

    @PostMapping("/validation/{schemaName}")
    @ResponseBody
    public String validateRequestedSchema(
            @PathVariable String schemaName,
            @RequestBody String allResourcesStr
    ) throws Exception {

        if (schemaName.equals(SOURCE_BRANCH)) {
            throw new NotAcceptableException(REPOSITORY_ERROR, "Not Allowed");
        }

        Config config = Config.getInstance();
        var fullRepositoryMap = toRepositoryMap(allResourcesStr);
        SchemaValidationContext validationContext = SchemaValidator.validate(
                schemaName,
                config.getKubernetes().getNamespacePrefix(),
                config.getKubernetes().getStorageServiceUrl(),
                SchemaUtils.findSettingsResource(fullRepositoryMap),
                fullRepositoryMap
        );

        if (validationContext.isValid()) {
            return "";
        }

        return allErrorMessages(validationContext.getReport());
    }

    private String allErrorMessages(ValidationReport report) {
        final var allErrors = new StringBuilder();

        List<String> linkErrors = report.getLinkErrorMessages().stream()
                .map(LinkErrorMessage::toPrintableMessage).toList();

        appendErrors(allErrors, linkErrors);

        List<String> boxErrors = report.getBoxResourceErrorMessages().stream()
                .map(BoxResourceErrorMessage::toPrintableMessage).toList();

        appendErrors(allErrors, boxErrors);

        List<String> exceptions = Collections.unmodifiableList(report.getExceptionMessages());

        appendErrors(allErrors, exceptions);

        return allErrors.toString();
    }

    private void appendErrors(StringBuilder allErrors, List<String> curErrorList) {
        curErrorList.forEach(error -> allErrors.append("\n").append(error));
    }

    private Map<String, Map<String, RepositoryResource>> toRepositoryMap(String allResourcesStr)
            throws JsonProcessingException {

        Map<String, Map<String, RepositoryResource>> repoMap = new HashMap<>();
        final String delimiter = "\nEOF\n";
        String[] yamlResources = allResourcesStr.split(delimiter);
        ObjectMapper mapper = new YAMLMapper();
        for (String yamlRes : yamlResources) {
            RepositoryResource res = mapper.readValue(yamlRes, RepositoryResource.class);
            repoMap.computeIfAbsent(res.getKind(), kind -> new HashMap<>()).put(res.getMetadata().getName(), res);
        }

        return repoMap;
    }

    @GetMapping("/validation/{schemaName}")
    @ResponseBody
    public SchemaValidationContext validateSchema(@PathVariable(name = "schemaName") String schemaName,
                                                  @RequestBody String requestBody
    ) throws Exception {
        if (schemaName.equals(SOURCE_BRANCH)) {
            throw new NotAcceptableException(REPOSITORY_ERROR, "Not Allowed");
        }

        // deserialize request body
        ValidationRequest request;
        SchemaValidationContext validationContext;
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .registerModule(new KotlinModule.Builder().build());
            request = mapper.readValue(requestBody, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BadRequestException(e);
        }

        List<RequestEntry> operations = request.operations;

        validateResourceNames(operations);

        Config config = Config.getInstance();

        var fullRepositoryMap = toRepositoryMap(operations);

        if (request.fullSchema) {
            return SchemaValidator.validate(
                    schemaName,
                    config.getKubernetes().getNamespacePrefix(),
                    config.getKubernetes().getStorageServiceUrl(),
                    SchemaUtils.findSettingsResource(fullRepositoryMap),
                    fullRepositoryMap);
        }

        // Combine received changes to existing schema and validate them together.
        GitCfg gitConfig = config.getGit();
        GitterContext ctx = GitterContext.getContext(gitConfig);

        // check if the schema exists
        Set<String> branches;
        try {
            branches = ctx.getBranches();
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e);
        }
        if (!branches.contains(schemaName)) {
            throw new ServiceException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.name(), "schema does not exists");
        }

        //validate combined schema
        try {
            final Gitter gitter = ctx.getGitter(schemaName);
            RepositorySnapshot snapshot;
            try {
                gitter.lock();
                snapshot = Repository.getSnapshot(gitter);
                var combinedRepositoryMap = toCombinedRepositoryMap(snapshot, operations);
                validationContext = SchemaValidator.validate(
                        schemaName,
                        config.getKubernetes().getNamespacePrefix(),
                        config.getKubernetes().getStorageServiceUrl(),
                        SchemaUtils.findSettingsResource(combinedRepositoryMap),
                        combinedRepositoryMap
                );
            } finally {
                gitter.unlock();
            }
            return validationContext;
        } catch (Exception e) {
            logger.error("Exception updating schema \"{}\" request", schemaName, e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e);
        }
    }

    private Map<String, Map<String, RepositoryResource>> toRepositoryMap(List<RequestEntry> operations) {
        Set<RepositoryResource> resources = new HashSet<>();
        for (RequestEntry entry : operations) {
            RequestOperation operation = entry.getOperation();
            ResourceEntry payload = entry.getPayload();
            switch (operation) {
                case add:
                    resources.add(payload.toRepositoryResource());
                    break;
                case update:
                case remove:
                    throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, UNSUPPORTED_OPERATION,
                            "update and remove operations are not supported for full schema validation request", null);
            }
        }
        return SchemaUtils.convertToRepositoryMap(resources);
    }

    private static class ValidationRequest {

        private boolean fullSchema;

        private List<RequestEntry> operations;

        public void setFullSchema(boolean fullSchema) {
            this.fullSchema = fullSchema;
        }

        public void setOperations(List<RequestEntry> operations) {
            this.operations = operations;
        }
    }
}
