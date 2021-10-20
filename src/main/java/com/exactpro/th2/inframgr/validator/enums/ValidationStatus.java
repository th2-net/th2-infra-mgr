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
