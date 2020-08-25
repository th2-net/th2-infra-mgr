package com.exactpro.th2.schema.schemaeditorbe.errors;

import com.exactpro.th2.schema.schemaeditorbe.models.ResourceEntry;
import org.springframework.http.HttpStatus;

public class K8sProvisioningException extends ServiceException {

    private static final String ERROR_CODE = "K8S_RESOURCE_PROVISIONING_ERROR";
    private static final HttpStatus statusCode = HttpStatus.INTERNAL_SERVER_ERROR;

    private K8sProvisioningErrorResponse errorResponse;
    @Override
    public K8sProvisioningErrorResponse getErrorResponse() {
        return errorResponse;
    }


    public K8sProvisioningException(String message) {
        super(statusCode, ERROR_CODE, message);
        errorResponse = new K8sProvisioningErrorResponse(statusCode, ERROR_CODE, message);
    }

    public void addItem(ResourceEntry entry) {
        errorResponse.addItem(entry);
    }
}
