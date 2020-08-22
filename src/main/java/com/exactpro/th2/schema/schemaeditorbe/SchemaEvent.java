package com.exactpro.th2.schema.schemaeditorbe;

public abstract class SchemaEvent {
    public abstract String getEventType();
    public abstract String getEventBody();
    
    private String schema;

    public String getSchema() {
        return schema;
    }

    public SchemaEvent(String schema) {
        this.schema = schema;
    }
}
