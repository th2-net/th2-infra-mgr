package com.exactpro.th2.inframgr.cassandra.template.helmrelease;

public class Component {
    private String imageName;
    private String imageVersion;

    public Component(String imageName, String imageVersion) {
        this.imageName = imageName;
        this.imageVersion = imageVersion;
    }

    public String getImageName() {
        return imageName;
    }

    public String getImageVersion() {
        return imageVersion;
    }
}
