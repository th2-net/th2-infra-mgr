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
package com.exactpro.th2.inframgr.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Set;

public class RepositorySnapshot {
    private String commitRef;
    private Set<ResourceEntry> resources;


    public RepositorySnapshot(String commitRef) {
        this.commitRef = commitRef;
    }

    public String getCommitRef() {
        return commitRef;
    }

    public Set<ResourceEntry> getResources() {
        return resources;
    }

    public void setResources(Set<ResourceEntry> resources) {
        this.resources = resources;
    }

    @JsonIgnore
    public RepositorySettings getRepositorySettings() throws JsonProcessingException {
        for (ResourceEntry entry : resources)
            if (entry.getKind() == ResourceType.SettingsFile) {
                ObjectMapper mapper = new ObjectMapper();
                RepositorySettings s = mapper.readValue(
                        mapper.writeValueAsString(entry.getSpec()),
                        RepositorySettings.class
                );
                return s;
            }
        return null;
    }

}