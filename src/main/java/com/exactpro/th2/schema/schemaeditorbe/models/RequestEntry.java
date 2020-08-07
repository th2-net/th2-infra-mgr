package com.exactpro.th2.schema.schemaeditorbe.models;

public class RequestEntry {
    private RequestOperation operation;
    private ResourceEntry payload;

    public RequestOperation getOperation() {
        return operation;
    }

    public void setOperation(RequestOperation operation) {
        this.operation = operation;
    }

    public ResourceEntry getPayload() {
        return payload;
    }

    public void setPayload(ResourceEntry payload) {
        this.payload = payload;
    }
}
