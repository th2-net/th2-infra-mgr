package com.exactpro.th2.schema.schemaeditorbe.errors;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ServiceException {
    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.name(), message);
    }
}
