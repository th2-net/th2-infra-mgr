package com.exactpro.th2.inframgr.cassandra.template.helmrelease;

public class Keyspace {
    private String keyspaceName;

    private String schemaVersion;

    public Keyspace() {
    }

    public Keyspace(String keyspace, String schemaVersion) {
        this.keyspaceName = keyspace;
        this.schemaVersion = schemaVersion;
    }

    public String getKeyspaceName() {
        return keyspaceName == null ? "" : keyspaceName;
    }

    public String getSchemaVersion() {
        return schemaVersion == null ? "" : schemaVersion;
    }
}
