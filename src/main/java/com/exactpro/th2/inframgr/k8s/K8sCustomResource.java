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
package com.exactpro.th2.inframgr.k8s;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.kubernetes.client.CustomResource;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class K8sCustomResource extends CustomResource {

    public static final String KEY_SOURCE_HASH = "th2.exactpro.com/source-hash";
    public static final String RESOURCE_NAME_REGEXP = "[a-z0-9]([-a-z0-9]*[a-z0-9])?";
    private Object spec;
    public Object status;
    public void setSpec(Object spec) {
        this.spec = spec;
    }
    public Object getSpec() {
        return spec;
    }

    public Object getStatus() {
        return status;
    }

    public void setStatus(Object status) {
        this.status = status;
    }

    @JsonIgnore
    public String getSourceHash() {
        Map<String, String> map = getMetadata().getAnnotations();
        return map == null ? null : map.get(KEY_SOURCE_HASH);
    }

    @JsonIgnore
    public void setSourceHash(String hash) {

        // Metadata object should already be present!!!
        Map<String, String> map = getMetadata().getAnnotations();

        if (map == null) {
            map = new HashMap<>();
            getMetadata().setAnnotations(map);
        }

        if (hash != null)
            map.put(KEY_SOURCE_HASH, hash);
        else
            map.remove(KEY_SOURCE_HASH);
    }
}
