/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.exactpro.th2.inframgr.statuswatcher;

import com.exactpro.th2.inframgr.SchemaEvent;

import java.util.concurrent.atomic.AtomicInteger;

public class StatusUpdateEvent extends SchemaEvent {
    public static final String EVENT_TYPE="statusUpdate";

    private String kind;
    private String name;
    private String status;

    private static volatile AtomicInteger counter = new AtomicInteger();
    private int eventId;

    private StatusUpdateEvent(String schema) {
        super(schema);
        eventId = counter.getAndIncrement();
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
                .append("\"kind\":\"").append(getKind()).append("\",")
                .append("\"name\":\"").append(getName()).append("\",")
                .append("\"status\":\"").append(getStatus()).append("\"}")
                .toString();
    }

    @Override
    public String getEventKey() {
        return String.format("%s:%d/%s", EVENT_TYPE, eventId, getSchema());
    }

    public String getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }


    public static class Builder {
        String schema;
        String name;
        String kind;
        String status;
        public Builder(String schema) {
            this.schema = schema;
        }

        public Builder withKind(String kind) {
            this.kind = kind;
            return this;
        }

        public Builder withResourceName(String name) {
            this.name = name;
            return this;
        }

        public Builder withStatus(String status) {
            this.status = status;
            return this;
        }

        public StatusUpdateEvent build() {
            if (schema == null || kind == null || name == null)
                throw new IllegalStateException("Event is not full described");

            StatusUpdateEvent event = new StatusUpdateEvent(schema);
            event.kind = kind;
            event.name = name;
            event.status = status;
            return event;
        }
    }
}
