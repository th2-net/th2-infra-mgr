package com.exactpro.th2.inframgr.docker;

public class DynamicResource {
    private String resourceName;
    private String image;
    private String currentVersion;
    private String versionRange;
    private String schema;


    public DynamicResource(String resourceName, String image, String currentVersion, String versionRange, String schema) {
        this.resourceName = resourceName;
        this.image = image;
        this.versionRange = versionRange;
        this.currentVersion = currentVersion;
        this.schema = schema;
    }

    public String getName() {
        return resourceName;
    }
    public String getImage() {
        return image;
    }

    public String getVersionRange() {
        return versionRange;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getSchema() {
        return schema;
    }

    public String getAnnotation() {
        return String.format("%s.%s", schema, getName());
    }


}
