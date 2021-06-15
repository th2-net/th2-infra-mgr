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

package com.exactpro.th2.inframgr.k8s;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Group("")
@Version("")
@JsonIgnoreProperties(ignoreUnknown = true)
public class K8sCustomResource extends CustomResource {

    public static final String KEY_SOURCE_HASH = "th2.exactpro.com/source-hash";
    public static final String KEY_COMMIT_HASH = "th2.exactpro.com/git-commit-hash";
    public static final String RESOURCE_NAME_REGEXP = "[a-z0-9]([-a-z0-9]*[a-z0-9])?";
    public static final int RESOURCE_NAME_MAX_LENGTH = 64;
    public static final int SCHEMA_NAME_MAX_LENGTH = 21;
    private Object spec;
    public Object status;

    private String apiVersion;
    private String kind;

    @Override
    public void setKind(String kind) {
        this.kind = kind;
    }

    @Override
    public String getKind() {
        return this.kind;
    }

    @Override
    public String getApiVersion() {
        return apiVersion;
    }

    @Override
    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

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

        if (hash != null) {
            map.put(KEY_SOURCE_HASH, hash);
        } else {
            map.remove(KEY_SOURCE_HASH);
        }
    }

    @JsonIgnore
    public void setCommitHash(String hash) {

        // Metadata object should already be present!!!
        Map<String, String> annotations = getMetadata().getAnnotations();

        if (hash != null) {
            annotations.put(KEY_COMMIT_HASH, hash);
        } else {
            annotations.remove(KEY_COMMIT_HASH);
        }
    }

    @JsonIgnore
    public static boolean isNameValid(String name) {
        return (name.length() < RESOURCE_NAME_MAX_LENGTH && pattern.matcher(name).matches());
    }

    @JsonIgnore
    public static boolean isSchemaNameValid(String name) {
        return (name.length() < SCHEMA_NAME_MAX_LENGTH && pattern.matcher(name).matches());
    }

    private static final Pattern pattern;

    static {
        pattern = Pattern.compile(K8sCustomResource.RESOURCE_NAME_REGEXP);
    }
}
