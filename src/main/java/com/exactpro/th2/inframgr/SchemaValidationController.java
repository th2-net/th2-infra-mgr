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
import com.exactpro.th2.inframgr.models.RequestEntry;
import com.exactpro.th2.inframgr.models.RequestOperation;
import com.exactpro.th2.inframgr.models.ResourceEntry;
import com.exactpro.th2.inframgr.util.cfg.GitCfg;
import com.exactpro.th2.infrarepo.*;
import com.exactpro.th2.validator.SchemaValidationContext;
import com.exactpro.th2.validator.SchemaValidator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static com.exactpro.th2.inframgr.SchemaController.*;

@Controller
public class SchemaValidationController {

    private static final Logger logger = LoggerFactory.getLogger(SchemaController.class);

    private static final String UNSUPPORTED_OPERATION = "UNSUPPORTED_OPERATION";

    private static final String REPOSITORY_ERROR = "REPOSITORY_ERROR";

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
            throw new BadRequestException(e.getMessage());
        }

        List<RequestEntry> operations = request.operations;

        validateResourceNames(operations);

        Config config = Config.getInstance();

        if (request.fullSchema) {
            return SchemaValidator.validate(
                    schemaName,
                    config.getKubernetes().getNamespacePrefix(),
                    toRepositoryMap(operations));
        }

        // Combine received changes to existing schema and validate them together.
        GitCfg gitConfig = config.getGit();
        GitterContext ctx = GitterContext.getContext(gitConfig);

        // check if the schema exists
        Set<String> branches;
        try {
            branches = ctx.getBranches();
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
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
                validationContext = SchemaValidator.validate(
                        schemaName,
                        config.getKubernetes().getNamespacePrefix(),
                        toCombinedRepositoryMap(snapshot, operations)
                );
            } finally {
                gitter.unlock();
            }
            return validationContext;
        } catch (Exception e) {
            logger.error("Exception updating schema \"{}\" request", schemaName, e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, REPOSITORY_ERROR, e.getMessage());
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
                            "update and remove operations are not supported for full schema validation request");
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
