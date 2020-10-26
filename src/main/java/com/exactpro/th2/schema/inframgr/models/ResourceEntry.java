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
package com.exactpro.th2.schema.inframgr.models;

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

    public String getSourceHash() {
        return hash;
    }

    public void setSourceHash(String hash) {
        this.hash = hash;
    }
}
