package com.exactpro.th2.inframgr.validator.cache;

import java.util.HashMap;
import java.util.Map;

public class ValidationCache {
    private static final Map<String, SchemaValidationTable> validationTable = new HashMap<>();

    public static SchemaValidationTable getSchemaTable(String schemaName) {
        return validationTable.computeIfAbsent(schemaName, k -> new SchemaValidationTable());
    }
}