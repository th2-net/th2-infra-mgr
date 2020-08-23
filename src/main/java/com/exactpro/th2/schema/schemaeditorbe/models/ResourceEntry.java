package com.exactpro.th2.schema.schemaeditorbe.models;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ResourceEntry {

    private ResourceType kind;
    private String name;
    private Object spec;
    private String hash;

    public ResourceType getKind() {
        return kind;
    }

    public void setKind(ResourceType kind) {
        this.kind = kind;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getSpec() {
        return spec;
    }

    public void setSpec(Object spec) {
        this.spec = spec;
    }

    @JsonIgnore
    public String getSourceHash() {
        return hash;
    }

    public void setSourceHash(String hash) {
        this.hash = hash;
    }
}
