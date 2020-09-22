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

import java.util.HashMap;
import java.util.Map;

public enum ResourceType {
    HelmRelease("HelmRelease", null, "helmreleases", "helm.fluxcd.io/v1"),
    Th2Link("Th2Link", "links", "th2links", "th2.exactpro.com/v1"),
    Th2Act("Th2Act", "acts", "th2acts", "th2.exactpro.com/v1"),
    Th2BookChecker("Th2BookChecker", "book-checkers", "th2bookcheckers", "th2.exactpro.com/v1"),
    Th2Codec("Th2Codec", "codecs", "th2codecs", "th2.exactpro.com/v1"),
    Th2Connector("Th2Connector", "connectors", "th2connectors", "th2.exactpro.com/v1"),
    Th2Recon("Th2Recon", "recons", "th2recons", "th2.exactpro.com/v1"),
    Th2Verifier("Th2Verifier", "verifiers", "th2verifiers", "th2.exactpro.com/v1"),
    Th2Dictionaries("Th2Dictionary", "dictionaries", "th2dictionaries", "th2.exactpro.com/v1"),
    SettingsFile("SettingsFile", "", null, null),
    UIFile("UIFile", "ui-files", null, null);

    private String kind;
    private String path;
    private String k8sName;
    private String k8sApiVersion;

    ResourceType(String value, String path, String k8sName, String k8sApiVersion) {
        this.kind = value;
        this.path = path;
        this.k8sName = k8sName;
        this.k8sApiVersion = k8sApiVersion;
    }
    public String kind() {
        return kind;
    }
    public String path() {
        return path;
    }
    public String k8sName() {
        return k8sName;
    }
    public String k8sApiVersion() {
        return k8sApiVersion;
    }
    public static ResourceType forKind(String value) {
        return kinds.get(value);
    }

    public static ResourceType forPath(String path) {
        return pathes.get(path);
    }

    public boolean isRepositoryResource() {
        return path != null;
    }
    public boolean isK8sResource() {
        return k8sName != null;
    }

    private static Map<String, ResourceType> kinds = new HashMap<>();
    private static Map<String, ResourceType> pathes = new HashMap<>();
    static {
        for (ResourceType t : ResourceType.values()) {
            kinds.put(t.kind(), t);
            pathes.put(t.path(), t);
        }
    }
}
