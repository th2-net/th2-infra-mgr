package com.exactpro.th2.inframgr.validator.cache;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ValidationCache {
    private static final Map<String, SchemaValidationTable> validationTable = new ConcurrentHashMap<>();

    public static SchemaValidationTable getSchemaTable(String schemaName) {
        return validationTable.computeIfAbsent(schemaName, k -> new SchemaValidationTable());
    }
}