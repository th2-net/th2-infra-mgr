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

package com.exactpro.th2.inframgr.validator.cache;

import com.exactpro.th2.inframgr.validator.enums.ValidationStatus;
import com.exactpro.th2.inframgr.validator.model.BoxesRelation;
import com.exactpro.th2.inframgr.validator.model.Th2LinkSpec;
import com.exactpro.th2.inframgr.validator.model.link.DictionaryLink;
import com.exactpro.th2.inframgr.validator.model.link.MessageLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.exactpro.th2.inframgr.validator.enums.ValidationStatus.VALID;

public class SchemaValidationTable {

    private static final Logger logger = LoggerFactory.getLogger(SchemaValidationTable.class);

    private boolean valid = true;

    private Map<String, ValidationObject> resources = new HashMap<>();

    public void setInvalid(String resourceName) {
        this.resources.computeIfAbsent(resourceName, k -> new ValidationObject()).setStatus(ValidationStatus.INVALID);
        this.valid = false;
    }

    public void addErrorMessage(String resourceName, String message, String commitRef) {
        this.resources.computeIfAbsent(resourceName, k -> new ValidationObject()).addErrorMessage(message, commitRef);
    }

    public void addValidMqLink(String resourceName, MessageLink link) {
        this.resources.computeIfAbsent(resourceName, k -> new ValidationObject()).addValidMqLink(link);
    }

    public void addValidGrpcLink(String resourceName, MessageLink link) {
        this.resources.computeIfAbsent(resourceName, k -> new ValidationObject()).addValidGrpcLink(link);
    }

    public void addValidDictionaryLink(String resourceName, DictionaryLink dictionaryLink) {
        this.resources.computeIfAbsent(resourceName, k -> new ValidationObject())
                .addValidDictionaryLink(dictionaryLink);
    }

    public void reset() {
        this.valid = true;
        this.resources = new HashMap<>();
    }

    public void printErrors() {
        this.resources.values().forEach(res -> res.getErrorMessages().forEach(logger::error));
    }

    public boolean isValid() {
        return valid;
    }

    public void removeInvalidLinks(String linkResName, Th2LinkSpec spec) {
        ValidationObject validationObject = resources.get(linkResName);
        if (validationObject != null && !validationObject.getStatus().equals(VALID)) {
            BoxesRelation boxesRelation = spec.getBoxesRelation();
            boxesRelation.setMqLinks(validationObject.getValidMqLinks());
            boxesRelation.setGrpcLinks(validationObject.getValidGrpcLinks());
            spec.setDictionariesRelation(validationObject.getValidDictionaryLinks());
        }
    }
}