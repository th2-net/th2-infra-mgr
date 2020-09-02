package com.exactpro.th2.schema.schemaeditorbe.k8s;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.fabric8.kubernetes.client.CustomResource;

import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class K8sCustomResource extends CustomResource {

    public static final String LABEL_SOURCE_HASH = "th2.exactpro.com/source_hash";
    public static final String RESOURCE_NAME_REGEXP = "[a-z0-9]([-a-z0-9]*[a-z0-9])?";
    private Object spec;
    public void setSpec(Object spec) {
        this.spec = spec;
    }
    public Object getSpec() {
        return spec;
    }

    @JsonIgnore
    public String getSourceHashLabel() {
        Map<String, String> labels = getMetadata().getLabels();

        if (labels != null)
            return labels.get(LABEL_SOURCE_HASH);
        else
            return null;
    }

    @JsonIgnore
    public void setSourceHashLabel(String hash) {

        // Metadata object should already be present!!!
        Map<String, String> labels = getMetadata().getLabels();

        if (labels == null) {
            labels = new HashMap<>();
            getMetadata().setLabels(labels);
        }

        if (hash != null)
            labels.put(LABEL_SOURCE_HASH, hash);
        else
            labels.remove(LABEL_SOURCE_HASH);
    }

}
