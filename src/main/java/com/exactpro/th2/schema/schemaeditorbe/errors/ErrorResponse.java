package com.exactpro.th2.schema.schemaeditorbe.errors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;

public class ErrorResponse {
    public static final String STATUS_CODE = "status_code";
    public static final String ERROR_CODE = "error_code";
    public static final String MESSAGE = "message";

    private HttpStatus httpStatus;
    private String errorCode;
    private String message;

    public ErrorResponse(HttpStatus httpStatus, String errorCode) {
        this(httpStatus, errorCode, null);
    }

    public ErrorResponse(HttpStatus httpStatus, String errorCode, String message) {
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.message = message;
    }

    @JsonIgnore
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    @JsonProperty(STATUS_CODE)
    public int getStatusCode() {
        return httpStatus.value();
    }

    @JsonProperty(ERROR_CODE)
    public String getErrorCode() {
        return errorCode;
    }

    @JsonProperty(MESSAGE)
    public String getMessage() {
        return message;
    }
}
