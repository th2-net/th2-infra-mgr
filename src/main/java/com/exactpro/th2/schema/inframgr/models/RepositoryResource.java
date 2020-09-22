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

import com.fasterxml.jackson.annotation.JsonIgnore;

public class RepositoryResource {

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
    private Object spec;
    private String hash;

    public RepositoryResource() {
    }

    public RepositoryResource(ResourceType type) {
        this.apiVersion = type.k8sApiVersion();
        this.kind = type.name();
    }

    public RepositoryResource(ResourceEntry data) {
        setApiVersion(data.getKind().k8sApiVersion());
        setKind(data.getKind().kind());
        setSpec(data.getSpec());
        setSourceHash(data.getSourceHash());

        setMetadata(new RepositoryResource.Metadata());
        getMetadata().setName(data.getName());
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
        this.apiVersion = ResourceType.forKind(kind).k8sApiVersion();
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public Object getSpec() {
        return spec;
    }

    public void setSpec(Object spec) {
        this.spec = spec;
    }

    @JsonIgnore
    public String getApiGroup() {
        return RepositoryResource.getApiGroup(apiVersion);
    }

    @JsonIgnore
    public static String getApiGroup(String apiVersion) {
        return apiVersion.substring(0, apiVersion.indexOf("/"));
    }

    @JsonIgnore
    public String getVersion() {
        return RepositoryResource.getVersion(apiVersion);
    }

    @JsonIgnore
    public static String getVersion(String apiVersion) {
        return apiVersion.substring(apiVersion.indexOf("/") + 1);
    }

    @JsonIgnore
    public String getSourceHash() {
        return hash;
    }

    @JsonIgnore
    public void setSourceHash(String hash) {
        this.hash = hash;
    }
}
