package com.exactpro.th2.schema.schemaeditorbe.models;

import com.fasterxml.jackson.databind.JsonNode;

public class ResponseDataUnit {

    private RepositoryDataType kind;
    private String name;
    private JsonNode spec;

    public RepositoryDataType getKind() {
        return kind;
    }

    public void setKind(RepositoryDataType kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JsonNode getSpec() {
        return spec;
    }

    public void setSpec(JsonNode spec) {
        this.spec = spec;
    }
}
