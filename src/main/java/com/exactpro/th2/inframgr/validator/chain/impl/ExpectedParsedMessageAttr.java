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

import com.exactpro.th2.inframgr.validator.enums.MessageFormatAttribute;
import com.exactpro.th2.inframgr.validator.enums.ValidationStatus;
import com.exactpro.th2.inframgr.validator.model.BoxLinkContext;

import java.util.List;

public final class ExpectedParsedMessageAttr extends ExpectedMessageFormatAttr {
    public ExpectedParsedMessageAttr(BoxLinkContext context) {
        super(
                context,
                MessageFormatAttribute.parsed.getPrefix(),
                //contradictingAttributePrefixes
                List.of(
                        MessageFormatAttribute.raw.getPrefix(),
                        MessageFormatAttribute.event.getPrefix()
                ),
                //otherMatchingAttributePrefixes
                List.of(MessageFormatAttribute.group.getPrefix()),
                ValidationStatus.PARSED_MESSAGE_FORMAT_ATTR_MISMATCH
        );
    }
}
