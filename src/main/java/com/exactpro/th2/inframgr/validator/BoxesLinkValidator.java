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
import com.exactpro.th2.inframgr.validator.model.BoxLinkContext;
import com.exactpro.th2.inframgr.validator.model.link.MessageLink;
import com.exactpro.th2.infrarepo.RepositoryResource;

import static com.exactpro.th2.inframgr.validator.enums.ValidationStatus.VALID;

abstract class BoxesLinkValidator {
    protected SchemaContext schemaContext;

    abstract void validateLink(RepositoryResource linkRes, MessageLink link);

    abstract ValidationStatus validateByContext(RepositoryResource resource, BoxLinkContext context);

    abstract void addValidMessageLink(String linkResName, MessageLink link);

    public BoxesLinkValidator(SchemaContext schemaContext) {
        this.schemaContext = schemaContext;
    }

    protected void validate(BoxLinkContext fromContext, BoxLinkContext toContext,
                            RepositoryResource linkRes, MessageLink link) {
        RepositoryResource fromRes = schemaContext.getBox(fromContext.getBoxName());
        RepositoryResource toRes = schemaContext.getBox(toContext.getBoxName());
        String linkResName = linkRes.getMetadata().getName();
        SchemaValidationTable schemaValidationTable = schemaContext.getSchemaValidationTable();

        ValidationStatus fromResValidationStatus = validateByContext(fromRes, fromContext);
        ValidationStatus toResValidationStatus = validateByContext(toRes, toContext);

        if (fromResValidationStatus.equals(VALID) && toResValidationStatus.equals(VALID)) {
            addValidMessageLink(linkResName, link);
            return;
        }
        //check if "from" resource is valid
        if (!fromResValidationStatus.equals(VALID)) {
            String message = String.format("link: \"%s\" from: \"%s\" is invalid. Resource: \"%s:[%s]\"",
                    link.getName(), linkResName, link.getFrom().getBox(), fromResValidationStatus);
            //Mark "th2link" resource as invalid, since it contains invalid link
            schemaValidationTable.setInvalid(linkResName);
            schemaValidationTable.addErrorMessage(linkResName, message, schemaContext.getCommitRef());
        }
        if (!toResValidationStatus.equals(VALID)) {
            String message = String.format("link: \"%s\" from: \"%s\" is invalid. Resource: \"%s[%s]\"",
                    link.getName(), linkResName, link.getTo().getBox(), toResValidationStatus);
            //Mark "th2link" resource as invalid, since it contains invalid link
            schemaValidationTable.setInvalid(linkResName);
            schemaValidationTable.addErrorMessage(linkResName, message, schemaContext.getCommitRef());
        }
    }
}
