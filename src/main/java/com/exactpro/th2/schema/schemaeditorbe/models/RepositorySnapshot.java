package com.exactpro.th2.schema.schemaeditorbe.models;

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
}
