package com.exactpro.th2.schema.schemaeditorbe.k8s;

import com.exactpro.th2.schema.schemaeditorbe.models.ResourceType;
import com.exactpro.th2.schema.schemaeditorbe.models.Th2CustomResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;

import java.util.List;

public class Kubernetes {
    public static void createOrReplace(String nameSpace, Th2CustomResource repoResource){

        CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                .withGroup(repoResource.getApiGroup())
                .withVersion(repoResource.getVersion())
                .withScope("Namespaced")
                .withPlural(ResourceType.forKind(repoResource.getKind()).k8sName())
                .build();

        try (KubernetesClient client = new DefaultKubernetesClient()) {

            var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
            K8sCustomResourceList customResourceList  = mixedOperation.list();
            List<K8sCustomResource> customResources = customResourceList.getItems();

            boolean resourceUpdated = false;

            if (customResources.size() > 0)
                for (K8sCustomResource k8sResource : customResources)
                    if (k8sResource.getMetadata().getName().equals(repoResource.getMetadata().getName())) {

                        k8sResource.setSpec(repoResource.getSpec());
                        k8sResource.getMetadata().setResourceVersion(null);
                        mixedOperation.createOrReplace(k8sResource);

                        resourceUpdated = true;
                        break;
                    }

            if (!resourceUpdated) {

                K8sCustomResource res = new K8sCustomResource();
                ObjectMeta metaData = new ObjectMetaBuilder()
                        .withName(repoResource.getMetadata().getName())
                        .withNamespace(nameSpace)
                        .build();
                res.setMetadata(metaData);
                res.setKind(repoResource.getKind());
                res.setSpec(repoResource.getSpec());
                mixedOperation.create(res);

            }
        }
    }


    public static boolean delete(String nameSpace, Th2CustomResource repoResource){

        CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                .withGroup(repoResource.getApiGroup())
                .withVersion(repoResource.getVersion())
                .withScope("Namespaced")
                .withPlural(ResourceType.forKind(repoResource.getKind()).k8sName())
                .build();

        try (KubernetesClient client = new DefaultKubernetesClient()) {
            var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
            Resource<K8sCustomResource, K8sCustomResourceDoneable> r = mixedOperation.inNamespace(nameSpace).withName(repoResource.getMetadata().getName());
            return r.delete();
        }
    }
}
