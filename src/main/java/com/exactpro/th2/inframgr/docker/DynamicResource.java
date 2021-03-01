package com.exactpro.th2.inframgr.docker;

public class DynamicResource {
    private final String resourceName;
    private final String image;
    private final String currentVersion;
    private final String versionRange;
    private final String schema;


    public DynamicResource(String resourceName, String image, String currentVersion, String versionRange, String schema) {
        this.resourceName = resourceName;
        this.image = image;
        this.currentVersion = currentVersion;
        this.versionRange = versionRange;
        this.schema = schema;
    }

    public String getName() {
        return resourceName;
    }

    public String getImage() {
        return image;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getVersionRange() {
        return versionRange;
    }

    public String getSchema() {
        return schema;
    }

    public String getAnnotation() {
        return String.format("%s.%s", schema, getName());
    }


}
