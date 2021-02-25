package com.exactpro.th2.inframgr.docker;

public class DynamicResource {
    private String resourceName;
    private String image;
    private String tag;
    private String mask;
    private String schema;


    public DynamicResource(String resourceName, String image, String tag, String mask, String schema) {
        this.resourceName = resourceName;
        this.image = image;
        this.tag = tag;
        this.mask = mask;
        this.schema = schema;
    }

    public String getImage() {
        return image;
    }

    public String getTag() {
        return tag;
    }

    public String getMask() {
        return mask;
    }

    public String getSchema() {
        return schema;
    }

    public String getAnnotation() {
        return String.format("%s.%s", schema, getResourceName());
    }

    public String getResourceName() {
        return resourceName;
    }
}
