package com.exactpro.th2.inframgr.docker;

import com.exactpro.th2.infrarepo.RepositoryResource;

public class DynamicResource {
    private RepositoryResource repositoryResource;
    private String image;
    private String tag;
    private String mask;
    private String schema;


    public DynamicResource(String image, String tag, String mask, String schema, RepositoryResource repositoryResource) {
        this.image = image;
        this.tag = tag;
        this.mask = mask;
        this.schema = schema;
        this.repositoryResource = repositoryResource;
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

    public RepositoryResource getRepositoryResource() {
        return repositoryResource;
    }

    public String getAnnotation(){
        return String.format("%s.%s", schema, getResourceName());
    }

    public String getResourceName(){
        return repositoryResource.getMetadata().getName();
    }
}
