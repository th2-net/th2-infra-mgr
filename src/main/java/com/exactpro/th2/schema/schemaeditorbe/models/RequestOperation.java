package com.exactpro.th2.schema.schemaeditorbe.models;

import java.util.HashMap;
import java.util.Map;

public enum RequestOperation {
    add("add"),
    update("update"),
    remove("remove");

    private String type;

    RequestOperation(String type) {
        this.type = type;
    }
    public String type() {
        return type;
    }

    public static RequestOperation forType(String type) {
        return types.get(type);
    }

    private static Map<String, RequestOperation> types = new HashMap<>();

    static {
        for (RequestOperation t : RequestOperation.values()) {
            types.put(t.type(), t);
        }
    }
}
