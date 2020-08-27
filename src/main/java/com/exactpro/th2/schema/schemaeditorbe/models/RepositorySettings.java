package com.exactpro.th2.schema.schemaeditorbe.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RepositorySettings {
    private Boolean k8sPropagationEnabled;

    @JsonProperty("k8s-propagation")
    public boolean isK8sPropagationEnabled() {
        return k8sPropagationEnabled == null ? false : k8sPropagationEnabled;
    }

    @JsonProperty("k8s-propagation")
    public void setK8sPropagationEnabled(boolean k8sPropagationEnabled) {
        this.k8sPropagationEnabled = k8sPropagationEnabled;
    }
}
