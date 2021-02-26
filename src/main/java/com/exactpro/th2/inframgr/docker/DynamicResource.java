package com.exactpro.th2.inframgr.docker;

public class DynamicResource {
    private String resourceName;
    private String image;
    private String versionRange;
    private String schema;


    public DynamicResource(String resourceName, String image, String versionRange, String schema) {
        this.resourceName = resourceName;
        this.versionRange = versionRange;
        this.schema = schema;
        this.image = image;
    }

    public String getImage() {
        return image;
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

    public String getName() {
        return resourceName;
    }
}
