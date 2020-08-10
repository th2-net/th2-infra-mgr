package com.exactpro.th2.schema.schemaeditorbe.errors;

import org.springframework.http.HttpStatus;

public class NotAcceptableException extends ServiceException {
    public NotAcceptableException(String errorCode, String message) {
        super(HttpStatus.NOT_ACCEPTABLE, errorCode, message);
    }
}
