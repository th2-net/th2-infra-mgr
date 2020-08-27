package com.exactpro.th2.schema.schemaeditorbe.models;

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
