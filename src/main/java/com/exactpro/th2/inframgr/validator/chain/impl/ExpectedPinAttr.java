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

import com.exactpro.th2.inframgr.validator.enums.BoxDirection;
import com.exactpro.th2.inframgr.validator.enums.PinAttribute;
import com.exactpro.th2.inframgr.validator.enums.ValidationStatus;
import com.exactpro.th2.inframgr.validator.model.PinSpec;
import com.exactpro.th2.inframgr.validator.chain.AbstractValidator;
import com.exactpro.th2.inframgr.validator.model.BoxLinkContext;

public final class ExpectedPinAttr extends AbstractValidator {

    private final BoxDirection boxDirection;

    public ExpectedPinAttr(BoxLinkContext context) {
        this.boxDirection = context.getBoxDirection();
    }

    @Override
    public ValidationStatus validate(Object object, Object... additional) {
        if (!(object instanceof PinSpec)) {
            throw new IllegalStateException("Expected target of type PinSpec");
        }
        var pin = (PinSpec) object;
        switch (boxDirection) {
            case to:
                if (pin.getAttributes().contains(PinAttribute.publish.name())) {
                    return ValidationStatus.INVALID_PIN_DIRECTION_ATTR;
                }
                break;

            case from:
                if (!pin.getAttributes().contains(PinAttribute.publish.name())
                        || pin.getAttributes().contains(PinAttribute.subscribe.name())) {
                    return ValidationStatus.INVALID_PIN_DIRECTION_ATTR;
                }
                break;
        }
        return super.validate(pin, additional);
    }
}
