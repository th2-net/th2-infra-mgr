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

package com.exactpro.th2.inframgr.validator;

import com.exactpro.th2.inframgr.k8s.SecretsManager;
import com.exactpro.th2.inframgr.util.SourceHashUtil;
import com.exactpro.th2.inframgr.validator.cache.SchemaValidationTable;
import com.exactpro.th2.inframgr.validator.cache.ValidationCache;
import com.exactpro.th2.inframgr.validator.model.link.DictionaryLink;
import com.exactpro.th2.inframgr.validator.model.link.MessageLink;
import com.exactpro.th2.inframgr.validator.model.Th2LinkSpec;
import com.exactpro.th2.infrarepo.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Secret;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.lookup.StringLookup;
import org.apache.commons.text.lookup.StringLookupFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static com.exactpro.th2.inframgr.statuswatcher.ResourcePath.annotationFor;

public class SchemaValidator {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Logger logger = LoggerFactory.getLogger(SchemaValidator.class);

    public static boolean validate(String schemaName,
                                   Map<String, Map<String, RepositoryResource>> repositoryMap,
                                   String commitRef) {
        SchemaValidationTable schemaValidationTable = ValidationCache.getSchemaTable(schemaName);
        schemaValidationTable.reset();
        try {
            validateLinks(schemaName, commitRef, schemaValidationTable, repositoryMap);
            validateSecrets(schemaName, commitRef, schemaValidationTable, repositoryMap);
        } catch (IOException e) {
            logger.error("Exception while validating \"{}\"", schemaName, e);
            return false;
        }
        return schemaValidationTable.isValid();
    }

    private static void validateSecrets(String schemaName, String commitRef,
                                        SchemaValidationTable schemaValidationTable,
                                        Map<String, Map<String, RepositoryResource>> repositoryMap) throws IOException {
        Map<String, RepositoryResource> boxes = repositoryMap.get(ResourceType.Th2Box.kind());
        Map<String, RepositoryResource> coreBoxes = repositoryMap.get(ResourceType.Th2CoreBox.kind());
        List<RepositoryResource> allBoxes = new ArrayList<>(boxes.values());
        allBoxes.addAll(coreBoxes.values());
        SecretsManager secretsManager = new SecretsManager();
        Secret secret = secretsManager.getCustomSecret(schemaName);
        Map<String, String> secretData = secret.getData();
        for (var res : allBoxes) {
            Map<String, Object> customConfig = extractCustomConfig(res);
            Set<String> secretsConfig = generateSecretsConfig(customConfig);
            if (!secretsConfig.isEmpty()) {
                for (String secretKey : secretsConfig) {
                    if (secretData == null || !secretData.containsKey(secretKey)) {
                        String resName = res.getMetadata().getName();
                        String errorMessage = String.format("Resource \"%s\" is invalid, value \"%s\" from " +
                                "\"secret-custom-config\" is not present in Kubernetes", resName, secretKey);
                        schemaValidationTable.setInvalid(resName);
                        schemaValidationTable.addErrorMessage(resName, errorMessage, commitRef);
                    }
                }
            }
        }
    }

    private static void validateLinks(String schemaName, String commitRef,
                                      SchemaValidationTable schemaValidationTable,
                                      Map<String, Map<String, RepositoryResource>> repositoryMap) {
        Collection<RepositoryResource> links = repositoryMap.get(ResourceType.Th2Link.kind()).values();
        Map<String, RepositoryResource> boxes = repositoryMap.get(ResourceType.Th2Box.kind());
        Map<String, RepositoryResource> coreBoxes = repositoryMap.get(ResourceType.Th2CoreBox.kind());
        Map<String, RepositoryResource> dictionaries = repositoryMap.get(ResourceType.Th2Dictionary.kind());

        Map<String, RepositoryResource> allBoxes = new HashMap<>(boxes);
        allBoxes.putAll(coreBoxes);

        SchemaContext schemaContext = new SchemaContext(
                schemaName,
                commitRef,
                allBoxes,
                dictionaries,
                schemaValidationTable
        );

        MqLinkValidator mqLinkValidator = new MqLinkValidator(schemaContext);
        GrpcLinkValidator grpcLinkValidator = new GrpcLinkValidator(schemaContext);
        DictionaryLinkValidator dictionaryLinkValidator = new DictionaryLinkValidator(schemaContext);

        logger.debug("Proceeding with validating schema: \"{}\"", schemaName);

        for (RepositoryResource linkRes : links) {
            Th2LinkSpec spec = mapper.convertValue(linkRes.getSpec(), Th2LinkSpec.class);
            for (MessageLink mqLink : spec.getBoxesRelation().getRouterMq()) {
                mqLinkValidator.validateLink(linkRes, mqLink);
            }
            for (MessageLink grpcLink : spec.getBoxesRelation().getRouterGrpc()) {
                grpcLinkValidator.validateLink(linkRes, grpcLink);
            }
            for (DictionaryLink dictionaryLink : spec.getDictionariesRelation()) {
                dictionaryLinkValidator.validateLink(linkRes, dictionaryLink);
            }
            schemaValidationTable.removeInvalidLinks(linkRes.getMetadata().getName(), spec);
            linkRes.setSpec(spec);
            try {
                String specStr = mapper.writeValueAsString(spec);
                linkRes.setSourceHash(SourceHashUtil.digest(specStr));
            } catch (JsonProcessingException e) {
                logger.error("Couldn't update source hash for \"{}\"", annotationFor(linkRes, schemaName));
            }
        }
        logger.debug("Finished validating schema: \"{}\"", schemaName);
    }

    private static Map<String, Object> extractCustomConfig(RepositoryResource resource) {
        String customConfigAlias = "custom-config";
        Map<String, Object> spec = (Map<String, Object>) resource.getSpec();
        return (Map<String, Object>) spec.get(customConfigAlias);
    }

    public static Set<String> generateSecretsConfig(Map<String, Object> customConfig) {
        Set<String> collector = new HashSet<>();
        CustomLookup customLookup = new CustomLookup(collector);
        StringSubstitutor stringSubstitutor = new StringSubstitutor(
                StringLookupFactory.INSTANCE.interpolatorStringLookup(
                        Map.of("secret_value", customLookup,
                                "secret_path", customLookup
                        ), null, false
                ));
        if (customConfig == null) {
            return Collections.emptySet();
        }
        for (var entry : customConfig.entrySet()) {
            var value = entry.getValue();
            if (value instanceof String) {
                String valueStr = (String) value;
                stringSubstitutor.replace(valueStr);
            } else if (value instanceof Map) {
                collector.addAll(generateSecretsConfig((Map<String, Object>) value));
            }
        }
        return collector;
    }

    static class CustomLookup implements StringLookup {

        private Set<String> collector;

        public CustomLookup(Set<String> collector) {
            this.collector = collector;
        }

        @Override
        public String lookup(String key) {
            collector.add(key);
            return null;
        }
    }

}
