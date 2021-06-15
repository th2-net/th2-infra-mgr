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

import com.exactpro.th2.inframgr.validator.enums.ValidationStatus;
import com.exactpro.th2.inframgr.validator.model.Th2Spec;
import com.exactpro.th2.inframgr.validator.chain.AbstractValidator;
import com.exactpro.th2.inframgr.validator.model.BoxLinkContext;
import com.exactpro.th2.infrarepo.RepositoryResource;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;

public final class PinExist extends AbstractValidator {

    private final String pinName;

    public PinExist(BoxLinkContext context) {
        this.pinName = context.getBoxPinName();
    }

    @Override
    public ValidationStatus validate(Object object, Object... additional) {
        if (!(object instanceof RepositoryResource)) {
            throw new IllegalStateException("Expected target of type Th2CustomResource");
        }
        ObjectMapper mapper = new ObjectMapper();
        var resource = (RepositoryResource) object;

        Th2Spec spec = mapper.convertValue(resource.getSpec(), Th2Spec.class);
        var pin = spec.getPin(pinName);
        if (Objects.nonNull(pin)) {
            return super.validate(pin, additional);
        }
        return ValidationStatus.PIN_NOT_EXIST;
    }

}
