package com.exactpro.th2.schema.schemaeditorbe.errors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
public class ServiceExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ErrorResponse> handleServiceException(Exception ex, WebRequest request) {

        ErrorResponse response = ((ServiceException) ex).getErrorResponse();
        return new ResponseEntity<>(response, response.getHttpStatus());

    }

}
