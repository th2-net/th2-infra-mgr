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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ExpectedMessageFormatAttr extends AbstractValidator {

    private final RepositoryResource linkedResource;
    private final String linkedPinName;
    private final BoxDirection boxDirection;

    private String mainAttributePrefix;

    private List<String> otherMatchingAttributePrefixes;
    private List<String> contradictingAttributePrefixes;

    public ExpectedMessageFormatAttr(
            BoxLinkContext context,
            String mainAttributePrefix,
            List<String> contradictingAttributePrefixes,
            List<String> otherMatchingAttributePrefixes
    ) {
        this.linkedResource = context.getLinkedResource();
        this.linkedPinName = context.getLinkedPinName();
        this.boxDirection = context.getBoxDirection();

        this.mainAttributePrefix = mainAttributePrefix;

        this.otherMatchingAttributePrefixes = otherMatchingAttributePrefixes;
        this.contradictingAttributePrefixes = contradictingAttributePrefixes;
    }

    @Override
    public ValidationStatus validate(Object object, Object... additional) {
        if (!(object instanceof PinSpec)) {
            throw new IllegalStateException("Expected target of type PinSpec");
        }
        var pin = (PinSpec) object;
        List<String> attributesFilteredList = pin.getAttributes()
                .stream()
                .filter(attribute -> attribute.startsWith(mainAttributePrefix))
                .collect(Collectors.toList());

        if (attributesFilteredList.isEmpty()) {
            // attribute no present, no need for further checks
            return super.validate(pin, additional);
        }

        if (attributesFilteredList.size() > 1) {
            // error. more then 1 attribute with the same prefix.
            return ValidationStatus.DUPLICATED_ATTRIBUTE;
        }

        String exactAttribute = attributesFilteredList.get(0);

        //check that there are no contradicting attributes on the same pin.
        ValidationStatus contradictingAttributesStatus = checkContradictingAttributes(pin, contradictingAttributePrefixes);
        if (!contradictingAttributesStatus.equals(ValidationStatus.VALID)) {
            // contradicting attribute has been detected.
            return contradictingAttributesStatus;
        }

        if (boxDirection == BoxDirection.to) {
            return super.validate(object, additional);
        }
        if (linkedResource == null) {
            return ValidationStatus.LINKED_RESOURCE_NOT_EXIST;
        }
        //step 2: check if linked pin contains matching attributes
        ObjectMapper mapper = new ObjectMapper();
        Th2Spec linkedResSpec = mapper.convertValue(linkedResource.getSpec(), Th2Spec.class);
        PinSpec linkedPin = linkedResSpec.getPin(linkedPinName);
        ValidationStatus oppositePinStatus = oppositePinAttributeMatch(linkedPin, exactAttribute, otherMatchingAttributePrefixes);
        if (!oppositePinStatus.equals(ValidationStatus.VALID)) {
            return oppositePinStatus;
        }
        return super.validate(pin, additional);
    }

    protected ValidationStatus checkContradictingAttributes(PinSpec pin, List<String> excludedAttributePrefixes) {
        for (String excludedPrefix : excludedAttributePrefixes) {
            var contradictingAttributes = pin.getAttributes()
                    .stream()
                    .filter(attribute -> attribute.startsWith(excludedPrefix)).collect(Collectors.toList());
            if (contradictingAttributes.size() > 0) {
                return ValidationStatus.CONTRADICTING_ATTRIBUTES;
            }
        }
        return ValidationStatus.VALID;
    }

    protected ValidationStatus oppositePinAttributeMatch(PinSpec linkedPin, String exactAttribute, List<String> otherMatchingAttributePrefixes) {
        if (linkedPin == null) {
            return ValidationStatus.LINKED_PIN_NOT_EXIST;
        }

        List<String> otherMatchingAttributes = new ArrayList<>();
        for (String matchingPrefix : otherMatchingAttributePrefixes) {
            var attrFilter = linkedPin.getAttributes().stream().filter(attr -> attr.startsWith(matchingPrefix)).collect(Collectors.toList());
            otherMatchingAttributes.addAll(attrFilter);
        }
        if (linkedPin.getAttributes().contains(exactAttribute) || otherMatchingAttributes.size() > 0) {
            return ValidationStatus.VALID;
        }
        return ValidationStatus.MESSAGE_FORMAT_ATTR_MISMATCH;

    }
}
