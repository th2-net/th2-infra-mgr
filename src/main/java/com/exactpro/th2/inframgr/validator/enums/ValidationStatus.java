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

package com.exactpro.th2.inframgr.validator.enums;

public enum ValidationStatus {
    WAITING_FOR_PROCESSING,
    INVALID,
    VALID,
    RESOURCE_NOT_EXIST,
    LINKED_RESOURCE_NOT_EXIST,
    STRATEGY_NOT_EXIST,
    PIN_NOT_EXIST,
    LINKED_PIN_NOT_EXIST,
    WRONG_CONNECTION_TYPE,
    INVALID_PIN_DIRECTION_ATTR,
    DUPLICATED_ATTRIBUTE,
    INVALID_LINKED_PIN_FORMAT_ATTR_COUNT,
    CONTRADICTING_ATTRIBUTES,
    MESSAGE_FORMAT_ATTR_MISMATCH,
    SERVICE_CLASS_MISMATCH,
    SERVICE_CLASSES_NOT_DEFINED
}
