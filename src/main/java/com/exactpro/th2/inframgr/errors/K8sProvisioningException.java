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
package com.exactpro.th2.inframgr.errors;

import com.exactpro.th2.inframgr.models.ResourceEntry;
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
