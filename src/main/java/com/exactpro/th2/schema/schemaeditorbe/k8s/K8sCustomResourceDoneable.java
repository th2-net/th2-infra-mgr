package com.exactpro.th2.schema.schemaeditorbe.k8s;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;

public class K8sCustomResourceDoneable extends CustomResourceDoneable<K8sCustomResource> {
    public K8sCustomResourceDoneable(K8sCustomResource resource, Function<K8sCustomResource, K8sCustomResource> function) {
        super(resource, function);
    }
}


