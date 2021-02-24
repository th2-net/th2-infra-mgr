package com.exactpro.th2.inframgr.docker;

public class DynamicResource {
    private String image;
    private String tag;
    private String pattern;
    private String schema;


    public DynamicResource(String image, String tag, String pattern, String schema) {
        this.image = image;
        this.tag = tag;
        this.pattern = pattern;
        this.schema = schema;
    }

    public String getImage() {
        return image;
    }

    public String getTag() {
        return tag;
    }

    public String getPattern() {
        return pattern;
    }

    public String getSchema() {
        return schema;
    }
}
