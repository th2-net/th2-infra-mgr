package com.exactpro.th2.schema.schemaeditorbe.models;

public class ResourceEntry {

    private ResourceType kind;
    private String name;
    private Object spec;

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
}
