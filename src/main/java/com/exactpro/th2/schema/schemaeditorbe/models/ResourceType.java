package com.exactpro.th2.schema.schemaeditorbe.models;

import java.util.HashMap;
import java.util.Map;

public enum ResourceType {
    Th2Act("Th2Act", "acts", "th2acts"),
    Th2BookChecker("Th2BookChecker", "book-checkers", "th2bookcheckers"),
    Th2Codec("Th2Codec", "codecs", "th2codecs"),
    Th2Connector("Th2Connector", "connectors", "th2connectors"),
    Th2Link("Th2Link", "links", "th2links"),
    Th2Recon("Th2Recon", "recons", "th2recons"),
    Th2Verifier("Th2Verifier", "verifiers", "th2verifiers"),
    UIFile("UIFile", "ui-files", null);

    private String kind;
    private String path;
    private String k8sName;

    ResourceType(String value, String path, String k8sName) {
        this.kind = value;
        this.path = path;
        this.k8sName = k8sName;
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
    public static ResourceType forKind(String value) {
        return kinds.get(value);
    }

    public static ResourceType forPath(String path) {
        return pathes.get(path);
    }

    public boolean isK8sResource() {
        return this != UIFile;
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
