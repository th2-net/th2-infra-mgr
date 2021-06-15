package com.exactpro.th2.inframgr.validator.cache;

import com.exactpro.th2.inframgr.validator.enums.ValidationStatus;
import com.exactpro.th2.inframgr.validator.model.BoxesRelation;
import com.exactpro.th2.inframgr.validator.model.Th2LinkSpec;
import com.exactpro.th2.inframgr.validator.model.link.DictionaryLink;
import com.exactpro.th2.inframgr.validator.model.link.MessageLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.exactpro.th2.inframgr.validator.enums.ValidationStatus.VALID;

public class SchemaValidationTable {
    protected static final Logger logger = LoggerFactory.getLogger(SchemaValidationTable.class);

    private boolean valid = true;
    private Map<String, ValidationObject> linkResources = new HashMap<>();

    public void setInvalid(String resourceName) {
        this.linkResources.computeIfAbsent(resourceName, k -> new ValidationObject()).setStatus(ValidationStatus.INVALID);
        this.valid = false;
    }

    public void addErrorMessage(String resourceName, String message) {
        this.linkResources.computeIfAbsent(resourceName, k -> new ValidationObject()).addErrorMessage(message);
    }

    public void addValidMqLink(String resourceName, MessageLink link) {
        this.linkResources.computeIfAbsent(resourceName, k -> new ValidationObject()).addValidMqLink(link);
    }

    public void addValidGrpcLink(String resourceName, MessageLink link) {
        this.linkResources.computeIfAbsent(resourceName, k -> new ValidationObject()).addValidGrpcLink(link);
    }

    public void addValidDictionaryLink(String resourceName, DictionaryLink dictionaryLink) {
        this.linkResources.computeIfAbsent(resourceName, k -> new ValidationObject()).addValidDictionaryLink(dictionaryLink);
    }

    public ValidationObject getValidationObject(String resourceName) {
        return this.linkResources.computeIfAbsent(resourceName, k -> new ValidationObject());
    }

    public void reset() {
        this.linkResources = new HashMap<>();
    }

    public void printErrors() {
        this.linkResources.values().forEach(invalidResource -> invalidResource.getErrorMessages().forEach(logger::error));
    }

    public boolean isValid() {
        return valid;
    }

    public void removeInvalidLinks(String linkResName, Th2LinkSpec spec) {
        ValidationObject validationObject = linkResources.get(linkResName);
        if (validationObject != null && !validationObject.getStatus().equals(VALID)) {
            BoxesRelation boxesRelation = spec.getBoxesRelation();
            boxesRelation.setMqLinks(validationObject.getValidMqLinks());
            boxesRelation.setGrpcLinks(validationObject.getValidGrpcLinks());
            spec.setDictionariesRelation(validationObject.getValidDictionaryLinks());
        }
    }
}
