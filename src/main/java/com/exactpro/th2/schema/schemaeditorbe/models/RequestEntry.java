package com.exactpro.th2.schema.schemaeditorbe.models;

public class RequestEntry {
    private RequestOperation operation;
    private ResponseDataUnit payload;

    public RequestOperation getOperation() {
        return operation;
    }

    public void setOperation(RequestOperation operation) {
        this.operation = operation;
    }

    public ResponseDataUnit getPayload() {
        return payload;
    }

    public void setPayload(ResponseDataUnit payload) {
        this.payload = payload;
    }
}
