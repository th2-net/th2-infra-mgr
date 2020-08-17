package com.exactpro.th2.schema.schemaeditorbe.k8s;

import io.fabric8.kubernetes.client.CustomResource;

public class K8sCustomResource extends CustomResource {
    private Object spec;
    public void setSpec(Object spec) {
        this.spec = spec;
    }
    public Object getSpec() {
        return spec;
    }
}
