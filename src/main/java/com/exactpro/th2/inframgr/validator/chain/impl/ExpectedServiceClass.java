package com.exactpro.th2.inframgr.validator.chain.impl;

import com.exactpro.th2.inframgr.validator.chain.AbstractValidator;
import com.exactpro.th2.inframgr.validator.enums.SchemaConnectionType;
import com.exactpro.th2.inframgr.validator.enums.ValidationStatus;
import com.exactpro.th2.inframgr.validator.model.BoxLinkContext;
import com.exactpro.th2.inframgr.validator.model.PinSpec;
import com.exactpro.th2.inframgr.validator.model.Th2Spec;
import com.exactpro.th2.infrarepo.RepositoryResource;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Set;

public final class ExpectedServiceClass extends AbstractValidator {

    private final RepositoryResource linkedResource;
    private final String linkedPinName;
    private final SchemaConnectionType connectionType;

    public ExpectedServiceClass(BoxLinkContext context) {
        this.linkedResource = context.getLinkedResource();
        this.linkedPinName = context.getLinkedPinName();
        this.connectionType = context.getConnectionType();
    }

    @Override
    public ValidationStatus validate(Object object, Object... additional) {

        if(connectionType == SchemaConnectionType.grpc_server){
            return super.validate(object, additional);
        }
        if (linkedResource == null) {
            return ValidationStatus.LINKED_RESOURCE_NOT_EXIST;
        }

        ObjectMapper mapper = new ObjectMapper();
        Th2Spec linkedResSpec = mapper.convertValue(linkedResource.getSpec(), Th2Spec.class);
        PinSpec linkedPin = linkedResSpec.getPin(linkedPinName);

        if (linkedPin == null) {
            return ValidationStatus.LINKED_PIN_NOT_EXIST;
        }

        Set<String> serviceClasses = linkedPin.getServiceClasses();

        if(serviceClasses == null || serviceClasses.isEmpty()){
            return ValidationStatus.SERVICE_CLASSES_NOT_DEFINED;
        }

        var pin = (PinSpec) object;
        if(serviceClasses.contains(pin.getServiceClass())){
            return super.validate(object, additional);
        }
        return ValidationStatus.SERVICE_CLASS_MISMATCH;

    }
}
