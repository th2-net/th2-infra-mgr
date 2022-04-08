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
import com.exactpro.th2.inframgr.validator.chain.impl.*;
import com.exactpro.th2.inframgr.validator.enums.BoxDirection;
import com.exactpro.th2.inframgr.validator.enums.SchemaConnectionType;
import com.exactpro.th2.inframgr.validator.enums.ValidationStatus;
import com.exactpro.th2.inframgr.validator.model.BoxLinkContext;
import com.exactpro.th2.inframgr.validator.model.link.MessageLink;
import com.exactpro.th2.infrarepo.RepositoryResource;

class MqLinkValidator extends BoxesLinkValidator {

    public MqLinkValidator(SchemaContext schemaContext) {
        super(schemaContext);
    }

    @Override
    void validateLink(RepositoryResource linkRes, MessageLink link) {

        var fromBoxSpec = link.getFrom();
        var toBoxSpec = link.getTo();
        try {
            RepositoryResource toRes = schemaContext.getBox(toBoxSpec.getBox());
            RepositoryResource fromRes = schemaContext.getBox(fromBoxSpec.getBox());


            var fromContext = BoxLinkContext.builder()
                    .boxName(fromBoxSpec.getBox())
                    .boxPinName(fromBoxSpec.getPin())
                    .boxDirection(BoxDirection.from)
                    .connectionType(SchemaConnectionType.mq)
                    .linkedResource(toRes)
                    .linkedPinName(toBoxSpec.getPin())
                    .build();

            var toContext = BoxLinkContext.builder()
                    .boxName(toBoxSpec.getBox())
                    .boxPinName(toBoxSpec.getPin())
                    .boxDirection(BoxDirection.to)
                    .connectionType(SchemaConnectionType.mq)
                    .linkedResource(fromRes)
                    .linkedPinName(fromBoxSpec.getPin())
                    .build();

            validate(fromContext, toContext, linkRes, link);
        } catch (Exception e) {
            String linkResName = linkRes.getMetadata().getName();
            String message = String.format("Exception processing link: \"%s\" from resource \"%s\". %s",
                    link.getName(), linkResName, e.getMessage());
            var schemaValidationTable = schemaContext.getSchemaValidationTable();
            schemaContext.getSchemaValidationTable().setInvalid(linkResName);
            schemaValidationTable.addErrorMessage(linkResName, message, schemaContext.getCommitRef());
        }
    }

    @Override
    void addValidMessageLink(String linkResName, MessageLink link) {
        SchemaValidationTable schemaValidationTable = schemaContext.getSchemaValidationTable();
        schemaValidationTable.addValidMqLink(linkResName, link);
    }

    @Override
    ValidationStatus validateByContext(RepositoryResource resource,
                                       BoxLinkContext context) {
        var resValidator = new ResourceExists();
        var pinExist = new PinExist(context);
        var expectedPinType = new ExpectedConnectionType(context);
        var expectedPinAttr = new ExpectedDirectionalAttr(context);
        var expectedRawAttr = new ExpectedRawMessageAttr(context);
        var expectedParsedAttr = new ExpectedParsedMessageAttr(context);
        var expectedGroupAttr = new ExpectedGroupMessageAttr(context);


        resValidator.setNext(pinExist);
        pinExist.setNext(expectedPinType);
        expectedPinType.setNext(expectedPinAttr);
        expectedPinAttr.setNext(expectedRawAttr);
        expectedRawAttr.setNext(expectedParsedAttr);
        expectedParsedAttr.setNext(expectedGroupAttr);

        return resValidator.validate(resource);
    }
}