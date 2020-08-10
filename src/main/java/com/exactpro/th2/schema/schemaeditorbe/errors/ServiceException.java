package com.exactpro.th2.schema.schemaeditorbe.errors;

import org.springframework.http.HttpStatus;

public class ServiceException extends RuntimeException {

    public ErrorResponse getErrorResponse() {
        return errorResponse;
    }

    private ErrorResponse errorResponse;

    public ServiceException(HttpStatus statusCode, String errorCode, String message) {
        errorResponse = new ErrorResponse(statusCode, errorCode, message);
    }

}
