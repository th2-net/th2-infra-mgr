/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
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

package com.exactpro.th2.inframgr.k8s;

import com.exactpro.th2.inframgr.SchemaEvent;

import java.util.concurrent.atomic.AtomicLong;

public class SynchronizationRequestEvent extends SchemaEvent {

    public static final String EVENT_TYPE="synchronizationRequest";

    private static final AtomicLong eventCounter = new AtomicLong();
    private final long eventId;

    public SynchronizationRequestEvent(String schema) {
        super(schema);
        eventId = eventCounter.incrementAndGet();
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    @Override
    public String getEventBody() {
        return new StringBuilder("{")
                .append("\"eventType\":\"").append(EVENT_TYPE).append("\",")
                .append("\"id\":\"").append(eventId).append("\"}")
                .append("\"schema\":\"").append(getSchema()).append("\",")
                .toString();
    }

    @Override
    public String getEventKey() {
        return getEventType() + ":" + eventId;
    }
}
