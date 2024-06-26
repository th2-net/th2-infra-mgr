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

import org.springframework.http.HttpStatus;

public class NotAcceptableException extends ServiceException {
    public NotAcceptableException(String errorCode, String message, Exception e) {
        super(HttpStatus.NOT_ACCEPTABLE, errorCode, message, e);
    }

    public NotAcceptableException(String errorCode, String message) {
        this(errorCode, message, null);
    }

    public NotAcceptableException(String errorCode, Exception e) {
        this(errorCode, e.getMessage(), e);
    }
}
