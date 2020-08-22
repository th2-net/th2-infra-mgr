package com.exactpro.th2.schema.schemaeditorbe.repository;

import com.exactpro.th2.schema.schemaeditorbe.SchemaEvent;

public class RepositoryUpdateEvent extends SchemaEvent {
    private static final String EVENT_TYPE="repositoryUpdate";
    private String commitRef;

    public RepositoryUpdateEvent(String branch, String commitRef) {
        super(branch);
        this.commitRef = commitRef;
    }
    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @Override
    public String getEventBody() {

        return new StringBuilder("{")
                .append("\"eventType\":\"").append(EVENT_TYPE).append("\",")
                .append("\"schema\":\"").append(getSchema()).append("\",")
                .append("\"commit\":\"").append(commitRef).append("\"}")
                .toString();
    }
}
