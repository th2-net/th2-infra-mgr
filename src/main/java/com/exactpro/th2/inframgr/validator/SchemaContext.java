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

import com.exactpro.th2.inframgr.validator.cache.SchemaValidationTable;
import com.exactpro.th2.inframgr.validator.enums.ValidationStatus;
import com.exactpro.th2.infrarepo.RepositoryResource;

import java.util.ArrayList;
import java.util.Map;

public class SchemaContext {

    private final String schemaName;

    private String commitRef;

    private final Map<String, RepositoryResource> allBoxes;

    private final Map<String, RepositoryResource> dictionaries;

    private SchemaValidationTable schemaValidationTable;

    public SchemaContext(String schemaName, String commitRef,
                         Map<String, RepositoryResource> allBoxes,
                         Map<String, RepositoryResource> dictionaries,
                         SchemaValidationTable schemaValidationTable) {
        this.schemaName = schemaName;
        this.commitRef = commitRef;
        this.allBoxes = allBoxes;
        this.dictionaries = dictionaries;
        this.schemaValidationTable = schemaValidationTable;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getCommitRef() {
        return commitRef;
    }

    public RepositoryResource getBox(String boxName) {
        return allBoxes.get(boxName);
    }

    public RepositoryResource getDictionary(String dictionaryName) {
        return dictionaries.get(dictionaryName);
    }

    public SchemaValidationTable getSchemaValidationTable() {
        return schemaValidationTable;
    }

    private static class ResourcesList {

        private ValidationStatus status = ValidationStatus.VALID;

        private final ArrayList<RepositoryResource> linkedResources = new ArrayList<>();
    }
}
