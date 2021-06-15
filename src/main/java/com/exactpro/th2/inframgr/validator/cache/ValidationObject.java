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

    public void addErrorMessage(String message) {
        this.errorMessages.add(message);
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
