package com.exactpro.th2.schema.schemaeditorbe.models;

import com.fasterxml.jackson.databind.JsonNode;

public class Th2CustomResource {
    public static String API_VERSION="th2.exactpro.com/v1";

    public static class Metadata {
        private String name;
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
    }
    private String apiVersion;
    private String kind;
    private Metadata metadata;
    private JsonNode spec;

    public Th2CustomResource() {
    }

    public Th2CustomResource(ResponseDataUnit data) {
        setApiVersion(Th2CustomResource.API_VERSION);
        setMetadata(new Th2CustomResource.Metadata());
        getMetadata().setName(data.getName());
        setKind(data.getKind().kind());
        setSpec(data.getSpec());
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public JsonNode getSpec() {
        return spec;
    }

    public void setSpec(JsonNode spec) {
        this.spec = spec;
    }
}
