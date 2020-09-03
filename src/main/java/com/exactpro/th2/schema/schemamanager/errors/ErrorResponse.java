/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.th2.schema.schemamanager.errors;

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
