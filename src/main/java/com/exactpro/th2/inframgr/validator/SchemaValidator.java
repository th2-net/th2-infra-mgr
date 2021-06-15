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

import com.exactpro.th2.inframgr.util.SourceHashUtil;
import com.exactpro.th2.inframgr.validator.cache.SchemaValidationTable;
import com.exactpro.th2.inframgr.validator.cache.ValidationCache;
import com.exactpro.th2.inframgr.validator.model.link.DictionaryLink;
import com.exactpro.th2.inframgr.validator.model.link.MessageLink;
import com.exactpro.th2.inframgr.validator.model.Th2LinkSpec;
import com.exactpro.th2.infrarepo.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.exactpro.th2.inframgr.statuswatcher.ResourcePath.annotationFor;

public class SchemaValidator {

    private static final ObjectMapper mapper = new ObjectMapper();

    protected static final Logger logger = LoggerFactory.getLogger(SchemaValidator.class);

    public static boolean validate(String schemaName, Map<String, Map<String, RepositoryResource>> repositoryMap) {
        SchemaValidationTable schemaValidationTable = ValidationCache.getSchemaTable(schemaName);
        Collection<RepositoryResource> links = repositoryMap.get(ResourceType.Th2Link.kind()).values();
        Map<String, RepositoryResource> boxes = repositoryMap.get(ResourceType.Th2Box.kind());
        Map<String, RepositoryResource> coreBoxes = repositoryMap.get(ResourceType.Th2CoreBox.kind());
        Map<String, RepositoryResource> dictionaries = repositoryMap.get(ResourceType.Th2Dictionary.kind());

        SchemaContext schemaContext = new SchemaContext(schemaName, boxes, coreBoxes, dictionaries, schemaValidationTable);

        MqLinkValidator mqLinkValidator = new MqLinkValidator(schemaContext);
        GrpcLinkValidator grpcLinkValidator = new GrpcLinkValidator(schemaContext);
        DictionaryLinkValidator dictionaryLinkValidator = new DictionaryLinkValidator(schemaContext);

        logger.debug("Proceeding with validating schema: \"{}\"", schemaName);
        schemaValidationTable.reset();

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
        return schemaValidationTable.isValid();
    }
}
