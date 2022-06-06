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

package com.exactpro.th2.inframgr.models;

import com.exactpro.th2.infrarepo.ResourceType;
import com.exactpro.th2.infrarepo.repo.RepositoryResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;

public class ResourceEntry {

    private ResourceType kind;

    private String name;

    private Object spec;

    private String hash;

    public ResourceEntry() {}

    public ResourceEntry(RepositoryResource resource) {
        this.kind = ResourceType.forKind(resource.getKind());
        this.name = resource.getMetadata().getName();
        this.spec = resource.getSpec();
        this.hash = resource.getSourceHash();
    }

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

    public RepositoryResource toRepositoryResource() {
        RepositoryResource resource = new RepositoryResource();
        resource.setApiVersion(this.getKind().k8sApiVersion());
        resource.setKind(this.getKind().kind());
        resource.setSpec(this.getSpec());
        resource.setSourceHash(this.getSourceHash());
        ObjectMeta meta = new ObjectMeta();
        meta.setName(this.getName());
        resource.setMetadata(meta);
        return resource;
    }

}
