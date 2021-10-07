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

package com.exactpro.th2.inframgr.validator.chain.impl;

import com.exactpro.th2.inframgr.validator.chain.AbstractValidator;
import com.exactpro.th2.inframgr.validator.enums.BoxDirection;
import com.exactpro.th2.inframgr.validator.enums.ValidationStatus;
import com.exactpro.th2.inframgr.validator.model.BoxLinkContext;
import com.exactpro.th2.inframgr.validator.model.PinSpec;
import com.exactpro.th2.inframgr.validator.model.Th2Spec;
import com.exactpro.th2.infrarepo.RepositoryResource;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Collectors;

public class ExpectedMessageFormatAttr extends AbstractValidator {
    private final String attributePrefix;
    private final String[] excludedAttributePrefixes;
    private final RepositoryResource linkedResource;
    private final String linkedPinName;
    private final BoxDirection boxDirection;

    public ExpectedMessageFormatAttr(BoxLinkContext context, String attributePrefix, String... excludedAttributePrefixes) {
        this.attributePrefix = attributePrefix;
        this.excludedAttributePrefixes = excludedAttributePrefixes;
        this.linkedResource = context.getLinkedResource();
        this.linkedPinName = context.getLinkedPinName();
        this.boxDirection = context.getBoxDirection();
    }

    @Override
    public ValidationStatus validate(Object object, Object... additional) {
        if (!(object instanceof PinSpec)) {
            throw new IllegalStateException("Expected target of type PinSpec");
        }
        var pin = (PinSpec) object;
        List<String> attributesFilteredList = pin.getAttributes()
                .stream()
                .filter(attribute -> attribute.startsWith(attributePrefix))
                .collect(Collectors.toList());
        //step 1: if attribute is present check that there are no contradicting attributes on the same pin.
        ValidationStatus uniquenessStatus = attributeUnique(pin, attributesFilteredList);
        if (uniquenessStatus == null) {
            // attribute no present, no need for further checks
            return super.validate(pin, additional);
        } else if (!uniquenessStatus.equals(ValidationStatus.VALID)) {
            // contradicting attribute has been detected.
            return uniquenessStatus;
        }
        if (boxDirection == BoxDirection.to) {
            return super.validate(object, additional);
        }
        if (linkedResource == null) {
            return ValidationStatus.LINKED_RESOURCE_NOT_EXIST;
        }
        ValidationStatus oppositePinStatus = oppositePinAttributeMatch(attributesFilteredList.get(0));
        if (!oppositePinStatus.equals(ValidationStatus.VALID)) {
            return oppositePinStatus;
        }
        return super.validate(pin, additional);
    }

    private ValidationStatus attributeUnique(PinSpec pin, List<String> attributesList) {
        if (attributesList.isEmpty()) {
            return null;
        }
        if (attributesList.size() > 1) {
            return ValidationStatus.INVALID_PIN_FORMAT_ATTR_COUNT;
        }
        for (String excludedPrefix : excludedAttributePrefixes) {
            var attrFilter = pin.getAttributes().stream().filter(attribute -> attribute.startsWith(excludedPrefix)).collect(Collectors.toList());
            if (attrFilter.size() > 0) {
                return ValidationStatus.CONTRADICTING_ATTRIBUTES;
            }
        }
        return ValidationStatus.VALID;
    }

    private ValidationStatus oppositePinAttributeMatch(String attribute) {
        ObjectMapper mapper = new ObjectMapper();
        Th2Spec linkedResSpec = mapper.convertValue(linkedResource.getSpec(), Th2Spec.class);
        PinSpec linkedPin = linkedResSpec.getPin(linkedPinName);

        if (linkedPin == null) {
            return ValidationStatus.LINKED_PIN_NOT_EXIST;
        }
        if (!linkedPin.getAttributes().contains(attribute)) {
            return ValidationStatus.MESSAGE_FORMAT_ATTR_MISMATCH;
        }
        return ValidationStatus.VALID;
    }
}
