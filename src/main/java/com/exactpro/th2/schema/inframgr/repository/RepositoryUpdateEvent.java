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
package com.exactpro.th2.schema.inframgr.repository;

import com.exactpro.th2.schema.inframgr.SchemaEvent;

public class RepositoryUpdateEvent extends SchemaEvent {
    public static final String EVENT_TYPE="repositoryUpdate";
    private String commitRef;
    private boolean syncingK8s;

    public RepositoryUpdateEvent(String branch, String commitRef) {
        super(branch);
        this.commitRef = commitRef;
    }

    public boolean isSyncingK8s() {
        return syncingK8s;
    }

    public void setSyncingK8s(boolean syncingK8s) {
        this.syncingK8s = syncingK8s;
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

    @Override
    public String getEventKey() {
        return new StringBuilder()
                .append(getSchema())
                .append(":")
                .append(commitRef)
                .toString();
    }

}
