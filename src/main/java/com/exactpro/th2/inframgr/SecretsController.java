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
import com.exactpro.th2.inframgr.k8s.SecretsManager;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class SecretsController {

    private static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";

    private static final String BAD_RESOURCE_NAME = "BAD_RESOURCE_NAME";

    @GetMapping("/secrets/{schemaName}")
    @ResponseBody
    public Set<String> getSecrets(@PathVariable(name = "schemaName") String schemaName) throws ServiceException {
        if (!K8sCustomResource.isSchemaNameValid(schemaName)) {
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid schema name");
        }
        try {
            SecretsManager secretsManager = new SecretsManager();
            Map<String, String> secretData = secretsManager.getCustomSecret(schemaName).getData();
            return secretData != null ? secretData.keySet() : Collections.emptySet();
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, e.getMessage());
        }
    }

    @PutMapping("/secrets/{schemaName}")
    @ResponseBody
    public Set<String> putSecrets(@PathVariable(name = "schemaName") String schemaName,
                                  @RequestBody String requestBody) {
        if (!K8sCustomResource.isSchemaNameValid(schemaName)) {
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid schema name");
        }
        List<SecretsRequestEntry> secretEntries;
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .registerModule(new KotlinModule.Builder().build());
            secretEntries = mapper.readValue(requestBody, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }

        try {
            SecretsManager secretsManager = new SecretsManager();
            return secretsManager.createOrReplaceSecrets(schemaName, secretEntries);
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, e.getMessage());
        }
    }

    @DeleteMapping("/secrets/{schemaName}")
    @ResponseBody
    public Set<String> deleteSecrets(@PathVariable(name = "schemaName") String schemaName,
                                     @RequestBody String requestBody) {
        if (!K8sCustomResource.isSchemaNameValid(schemaName)) {
            throw new NotAcceptableException(BAD_RESOURCE_NAME, "Invalid schema name");
        }
        Set<String> secretsNames;
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .registerModule(new KotlinModule.Builder().build());
            secretsNames = mapper.readValue(requestBody, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new BadRequestException(e.getMessage());
        }

        try {
            SecretsManager secretsManager = new SecretsManager();
            return secretsManager.deleteSecrets(schemaName, secretsNames);
        } catch (Exception e) {
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, UNKNOWN_ERROR, e.getMessage());
        }
    }

    public static class SecretsRequestEntry {

        private String key;

        private String data;

        public String getKey() {
            return key;
        }

        public String getData() {
            return data;
        }
    }

}
