/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.WebRequest;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class ExceptionHandlerConfiguration {

    private static final String DEFAULT_KEY_STATUS = "status";
    private static final String DEFAULT_KEY_ERROR = "error";
    private static final String DEFAULT_KEY_ERRORS = "errors";
    private static final String DEFAULT_KEY_MESSAGE = "message";
    private static final String DEFAULT_KEY_PATH = "path";

    @Bean
    public ErrorAttributes errorAttributes() {
        return new DefaultErrorAttributes() {

            @Override
            public Map<String ,Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {

                Map<String ,Object> defaultMap = super.getErrorAttributes(webRequest, options);
                Map<String ,Object> errorAttributes = new LinkedHashMap<>();

                int statusCode = Integer.valueOf(defaultMap.get(DEFAULT_KEY_STATUS).toString());
                errorAttributes.put(ErrorResponse.STATUS_CODE, statusCode);
                errorAttributes.put(ErrorResponse.ERROR_CODE, HttpStatus.resolve(statusCode));
                errorAttributes.put(ErrorResponse.MESSAGE, defaultMap.get(DEFAULT_KEY_ERROR));

                return errorAttributes;
            }
        };
    }
}