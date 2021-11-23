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
import com.exactpro.th2.inframgr.validator.model.link.DictionaryLink;
import com.exactpro.th2.infrarepo.RepositoryResource;

import static java.lang.String.format;

public class DictionaryLinkValidator {
    private SchemaContext schemaContext;

    public DictionaryLinkValidator(SchemaContext schemaContext) {
        this.schemaContext = schemaContext;
    }

    void validateLink(RepositoryResource linkRes, DictionaryLink link) {
        String boxName = link.getBox();
        String dicName = link.getDictionary().getName();
        String linkResName = linkRes.getMetadata().getName();
        SchemaValidationTable schemaValidationTable = schemaContext.getSchemaValidationTable();

        RepositoryResource boxResource = schemaContext.getBox(boxName);
        //First check if box is present
        if (boxResource != null) {
            //if box is present validate that required dictionary also exists
            if (schemaContext.getDictionary(dicName) != null) {
                schemaValidationTable.addValidDictionaryLink(linkResName, link);
                return;
            }
            String message = format("link: \"%s\" from: \"%s\" is invalid and will be ignored. Resource: \"%s:[%s]\"",
                    link.getName(), linkResName, link.getDictionary().getName(), ValidationStatus.RESOURCE_NOT_EXIST);
            schemaValidationTable.setInvalid(linkResName);
            schemaValidationTable.addErrorMessage(linkResName, message, schemaContext.getCommitRef());
        }
    }
}
