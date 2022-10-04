/*
 * Copyright 2022 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.inframgr.util;

import com.exactpro.th2.validator.ValidationReport;
import com.exactpro.th2.validator.errormessages.BoxResourceErrorMessage;
import com.exactpro.th2.validator.errormessages.LinkErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class SchemaErrorPrinter {

    private SchemaErrorPrinter() {
    }

    private static final Logger logger = LoggerFactory.getLogger(SchemaErrorPrinter.class);

    public static void printErrors(ValidationReport report) {
        List<LinkErrorMessage> linkErrors = report.getLinkErrorMessages();
        if (!linkErrors.isEmpty()) {
            logger.error("Link related errors: ");
            for (LinkErrorMessage message : linkErrors) {
                logger.error(message.toPrintableMessage());
            }
        }

        List<BoxResourceErrorMessage> boxResourceErrorMessages = report.getBoxResourceErrorMessages();
        if (!boxResourceErrorMessages.isEmpty()) {
            logger.error("Box related errors: ");
            for (BoxResourceErrorMessage errorMessage : boxResourceErrorMessages) {
                logger.error(errorMessage.toPrintableMessage());
            }
        }

        List<String> exceptionMessages = report.getExceptionMessages();
        if (!exceptionMessages.isEmpty()) {
            logger.error("Runtime exceptions errors: ");
            for (String errorMessage : exceptionMessages) {
                logger.error(errorMessage);
            }
        }
    }
}
