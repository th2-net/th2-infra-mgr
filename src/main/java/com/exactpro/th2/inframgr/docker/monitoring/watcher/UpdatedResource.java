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

package com.exactpro.th2.inframgr.docker.monitoring.watcher;

class UpdatedResource {

    private final String name;

    private final String kind;

    private final String latestVersion;

    public String getName() {
        return name;
    }

    public String getKind() {
        return kind;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public UpdatedResource(String name, String kind, String latestVersion) {
        this.name = name;
        this.kind = kind;
        this.latestVersion = latestVersion;
    }
}
