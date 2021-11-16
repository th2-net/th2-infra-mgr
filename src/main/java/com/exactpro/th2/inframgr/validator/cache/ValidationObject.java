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

package com.exactpro.th2.inframgr.validator.cache;

import com.exactpro.th2.inframgr.validator.enums.ValidationStatus;
import com.exactpro.th2.inframgr.validator.model.link.DictionaryLink;
import com.exactpro.th2.inframgr.validator.model.link.MessageLink;

import java.util.ArrayList;
import java.util.List;

public class ValidationObject {

    private ValidationStatus status = ValidationStatus.VALID;

    private final List<String> errorMessages = new ArrayList<>();

    private final List<MessageLink> validMqLinks = new ArrayList<>();

    private final List<MessageLink> validGrpcLinks = new ArrayList<>();

    private final List<DictionaryLink> validDictionaryLinks = new ArrayList<>();

    public void setStatus(ValidationStatus status) {
        this.status = status;
    }

    public void addErrorMessage(String message, String commitRef) {
        this.errorMessages.add(String.format("%s [commit: %s]", message, commitRef));
    }

    public void addValidMqLink(MessageLink link) {
        this.validMqLinks.add(link);
    }

    public void addValidGrpcLink(MessageLink link) {
        this.validGrpcLinks.add(link);
    }

    public void addValidDictionaryLink(DictionaryLink link) {
        this.validDictionaryLinks.add(link);
    }

    public List<MessageLink> getValidMqLinks() {
        return validMqLinks;
    }

    public List<MessageLink> getValidGrpcLinks() {
        return validGrpcLinks;
    }

    public List<DictionaryLink> getValidDictionaryLinks() {
        return validDictionaryLinks;
    }

    public ValidationStatus getStatus() {
        return status;
    }

    public List<String> getErrorMessages() {
        return errorMessages;
    }
}
