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

package com.exactpro.th2.inframgr.util

import com.exactpro.th2.validator.ValidationReport
import mu.KotlinLogging

object SchemaErrorPrinter {

    private val logger = KotlinLogging.logger { }

    @JvmStatic
    fun printErrors(report: ValidationReport, commit: String) {
        val linkErrors = report.linkErrorMessages
        if (linkErrors.isNotEmpty()) {
            logger.error("Link related errors. [{}]: ", commit)
            for (message in linkErrors) {
                logger.error("${message.toPrintableMessage()} [$commit]")
            }
        }
        val boxResourceErrorMessages = report.boxResourceErrorMessages
        if (boxResourceErrorMessages.isNotEmpty()) {
            logger.error("Box related errors. [{}]: ", commit)
            for (errorMessage in boxResourceErrorMessages) {
                logger.error("${errorMessage.toPrintableMessage()}  [$commit]")
            }
        }
        val bookErrorMessages = report.bookErrorMessages
        if (bookErrorMessages.isNotEmpty()) {
            logger.error("Book related errors. [{}]: ", commit)
            for (errorMessage in bookErrorMessages) {
                logger.error("${errorMessage.toPrintableMessage()}  [$commit]")
            }
        }
        val exceptionMessages = report.exceptionMessages
        if (exceptionMessages.isNotEmpty()) {
            logger.error("Runtime exceptions errors. [{}]: ", commit)
            for (errorMessage in exceptionMessages) {
                logger.error("$errorMessage  [$commit]")
            }
        }
    }
}
