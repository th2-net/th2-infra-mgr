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
package com.exactpro.th2.schema.schemamanager.models;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RepositorySettings {
    private Boolean k8sPropagationEnabled;
    private Boolean k8sGovernanceEnabled;

    @JsonProperty("k8s-propagation")
    public boolean isK8sPropagationEnabled() {
        return k8sPropagationEnabled == null ? false : k8sPropagationEnabled;
    }

    @JsonProperty("k8s-propagation")
    public void setK8sPropagationEnabled(boolean k8sPropagationEnabled) {
        this.k8sPropagationEnabled = k8sPropagationEnabled;
    }

    @JsonProperty("k8s-governance")
    public Boolean isK8sGovernanceEnabled() {
        return k8sGovernanceEnabled == null ? false : k8sGovernanceEnabled;
    }

    @JsonProperty("k8s-governance")
    public void setK8sGovernanceEnabled(Boolean k8sGovernanceEnabled) {
        this.k8sGovernanceEnabled = k8sGovernanceEnabled;
    }
}
