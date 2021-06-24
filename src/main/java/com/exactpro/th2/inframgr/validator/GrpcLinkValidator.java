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
import com.exactpro.th2.inframgr.validator.chain.impl.ExpectedConnectionType;
import com.exactpro.th2.inframgr.validator.chain.impl.PinExist;
import com.exactpro.th2.inframgr.validator.chain.impl.ResourceExists;
import com.exactpro.th2.inframgr.validator.enums.BoxDirection;
import com.exactpro.th2.inframgr.validator.enums.SchemaConnectionType;
import com.exactpro.th2.inframgr.validator.enums.ValidationStatus;
import com.exactpro.th2.inframgr.validator.model.BoxLinkContext;
import com.exactpro.th2.inframgr.validator.model.link.MessageLink;
import com.exactpro.th2.infrarepo.RepositoryResource;

class GrpcLinkValidator extends BoxesLinkValidator {

    public GrpcLinkValidator(SchemaContext schemaContext) {
        super(schemaContext);
    }

    @Override
    void validateLink(RepositoryResource linkRes, MessageLink link) {

        var fromBoxSpec = link.getFrom();

        var fromContext = BoxLinkContext.builder()
                .boxName(fromBoxSpec.getBox())
                .boxPinName(fromBoxSpec.getPin())
                .boxDirection(BoxDirection.from)
                .connectionType(SchemaConnectionType.grpc_client)
                .build();

        var toBoxSpec = link.getTo();

        var toContext = fromContext.toBuilder()
                .boxName(toBoxSpec.getBox())
                .boxPinName(toBoxSpec.getPin())
                .boxDirection(BoxDirection.to)
                .connectionType(SchemaConnectionType.grpc_server)
                .build();

        validate(fromContext, toContext, linkRes, link);

    }

    @Override
    void addValidMessageLink(String linkResName, MessageLink link) {
        SchemaValidationTable schemaValidationTable = schemaContext.getSchemaValidationTable();
        schemaValidationTable.addValidGrpcLink(linkResName, link);
    }

    @Override
    ValidationStatus validateByContext(RepositoryResource resource,
                                       BoxLinkContext context) {
        var resValidator = new ResourceExists();
        var pinExist = new PinExist(context);
        var expectedPin = new ExpectedConnectionType(context);

        resValidator.setNext(pinExist);
        pinExist.setNext(expectedPin);

        return resValidator.validate(resource);
    }
}
