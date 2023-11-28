/*
 * Copyright 2020-2023 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.inframgr.errors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
public class ErrorResponse {

    public static final String STATUS_CODE = "status_code";

    public static final String ERROR_CODE = "error_code";

    public static final String MESSAGE = "message";

    public static final String CAUSES = "causes";

    private final HttpStatus httpStatus;

    private final String errorCode;

    private final String message;

    private final List<String> causes;

    public ErrorResponse(HttpStatus httpStatus, String errorCode, String message, Exception e) {
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.message = message;
        this.causes = collectCauses(e);
    }

    public ErrorResponse(HttpStatus httpStatus, String errorCode, Exception e) {
        this(httpStatus, errorCode, e.getMessage(), e);
    }

    public ErrorResponse(HttpStatus httpStatus, String errorCode, String message) {
        this(httpStatus, errorCode, message, null);
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

    @JsonProperty(CAUSES)
    public List<String> getCauses() {
        return causes;
    }

    private List<String> collectCauses(Exception e) {
        if (e == null || e.getCause() == null) {
            return Collections.emptyList();
        }

        Throwable cause = e.getCause();
        List<String> causes = new ArrayList<>();
        while (cause != null) {
            causes.add(cause.getMessage());
            cause = cause.getCause();
        }
        return causes;
    }
}
