package com.exactpro.th2.schema.schemaeditorbe.models;

import java.util.HashMap;
import java.util.Map;

public enum RepositoryDataType {
    Th2Act("Th2Act", "acts"),
    Th2BookChecker("Th2BookChecker", "book-checkers"),
    Th2Codec("Th2Codec", "codecs"),
    Th2Connector("Th2Connector", "connectors"),
    Th2Link("Th2Link", "links"),
    Th2Recon("Th2Recon", "recons"),
    Th2Verifier("Th2Verifier", "verifiers"),
    UIFile("UIFile", "ui-files");

    private String kind;
    private String path;
    RepositoryDataType(String value, String path) {
        this.kind = value;
        this.path = path;
    }
    public String kind() {
        return kind;
    }
    public String path() {
        return path;
    }
    public static RepositoryDataType forKind(String value) {
        return kinds.get(value);
    }

    public static RepositoryDataType forPath(String path) {
        return pathes.get(path);
    }

    private static Map<String, RepositoryDataType> kinds = new HashMap<>();
    private static Map<String, RepositoryDataType> pathes = new HashMap<>();
    static {
        for (RepositoryDataType t : RepositoryDataType.values()) {
            kinds.put(t.kind(), t);
            pathes.put(t.path(), t);
        }
    }
}
