package com.exactpro.th2.schema.schemaeditorbe.k8s;

import com.exactpro.th2.schema.schemaeditorbe.Config;
import com.exactpro.th2.schema.schemaeditorbe.models.ResourceType;
import com.exactpro.th2.schema.schemaeditorbe.models.Th2CustomResource;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;

import java.io.Closeable;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Kubernetes implements Closeable {

    public void createOrReplace(Th2CustomResource repoResource) {

        CustomResourceDefinitionContext crdContext = getCrdContext(repoResource);

        var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
        K8sCustomResourceList customResourceList  = mixedOperation.inNamespace(nameSpace).list();
        List<K8sCustomResource> customResources = customResourceList.getItems();
        boolean resourceUpdated = false;

        if (customResources.size() > 0)
            for (K8sCustomResource k8sResource : customResources)
                if (k8sResource.getMetadata().getName().equals(repoResource.getMetadata().getName())) {

                    k8sResource.setSpec(repoResource.getSpec());
                    k8sResource.setSourceHashLabel(repoResource.getSourceHash());
                    k8sResource.getMetadata().setResourceVersion(null);
                    mixedOperation.inNamespace(nameSpace).createOrReplace(k8sResource);

                    resourceUpdated = true;
                    break;
                }

        if (!resourceUpdated) {

            K8sCustomResource k8sResource = new K8sCustomResource();
            ObjectMeta metaData = new ObjectMetaBuilder()
                    .withName(repoResource.getMetadata().getName())
                    .withNamespace(nameSpace)
                    .build();
            k8sResource.setMetadata(metaData);
            k8sResource.setSourceHashLabel(repoResource.getSourceHash());
            k8sResource.setKind(repoResource.getKind());
            k8sResource.setSpec(repoResource.getSpec());
            mixedOperation.inNamespace(nameSpace).create(k8sResource);

        }
    }

    public void create(Th2CustomResource repoResource) {

        CustomResourceDefinitionContext crdContext = getCrdContext(repoResource);
        var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);

        K8sCustomResource k8sResource = new K8sCustomResource();
        ObjectMeta metaData = new ObjectMetaBuilder()
                .withName(repoResource.getMetadata().getName())
                .withNamespace(nameSpace)
                .build();
        k8sResource.setMetadata(metaData);
        k8sResource.setSourceHashLabel(repoResource.getSourceHash());
        k8sResource.setKind(repoResource.getKind());
        k8sResource.setSpec(repoResource.getSpec());
        mixedOperation.inNamespace(nameSpace).create(k8sResource);
    }


    public void replace(Th2CustomResource repoResource) throws ResourceNotFoundException {

        CustomResourceDefinitionContext crdContext = getCrdContext(repoResource);

        var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
        K8sCustomResourceList customResourceList  = mixedOperation.inNamespace(nameSpace).list();
        List<K8sCustomResource> customResources = customResourceList.getItems();

        if (customResources.size() > 0)
            for (K8sCustomResource k8sResource : customResources)
                if (k8sResource.getMetadata().getName().equals(repoResource.getMetadata().getName())) {

                    k8sResource.setSpec(repoResource.getSpec());
                    k8sResource.setSourceHashLabel(repoResource.getSourceHash());
                    k8sResource.getMetadata().setResourceVersion(null);
                    mixedOperation.inNamespace(nameSpace).createOrReplace(k8sResource);

                    return;
                }
        throw new ResourceNotFoundException("Resource to replace does not exists");
    }


    public Map<String, K8sCustomResource> loadResources(ResourceType type, String apiGroup, String apiVersion) {

        CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                .withGroup(apiGroup)
                .withVersion(apiVersion)
                .withScope("Namespaced")
                .withPlural(type.k8sName())
                .build();

        var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
        K8sCustomResourceList customResourceList  = mixedOperation.inNamespace(nameSpace).list();
        List<K8sCustomResource> customResources = customResourceList.getItems();

        Map<String, K8sCustomResource> resources = new HashMap<>();
        for (K8sCustomResource k8sResource : customResources)
            resources.put(k8sResource.getMetadata().getName(), k8sResource);
        return resources;
    }


    public boolean delete(Th2CustomResource repoResource) {

        CustomResourceDefinitionContext crdContext = getCrdContext(repoResource);

         var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
        Resource<K8sCustomResource, K8sCustomResourceDoneable> r = mixedOperation.inNamespace(nameSpace).withName(repoResource.getMetadata().getName());
        return r.delete();

    }


    public CustomResourceDefinitionContext getCrdContext(Th2CustomResource resource) {
        CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                .withGroup(resource.getApiGroup())
                .withVersion(resource.getVersion())
                .withScope("Namespaced")
                .withPlural(ResourceType.forKind(resource.getKind()).k8sName())
                .build();

        return crdContext;
    }

    private KubernetesClient client;
    private String nameSpace;
    public Kubernetes(Config.K8sConfig config, String nameSpace) {

        // if we are not using custom configutation, let fabric8 handle initialization
        this.nameSpace = nameSpace;
        if (!config.useCustomConfig()) {
            client = new DefaultKubernetesClient();
            return;
        }

        // customize client according to configuration specified
        ConfigBuilder configBuilder = new ConfigBuilder();
        configBuilder
                .withMasterUrl(config.getMasterURL())
                .withApiVersion(config.getApiVersion())
                .withTrustCerts(config.ignoreInsecureHosts());

        // prioritize key & certificate data over files
        if (config.getClientCertificate() != null && config.getClientCertificate().length() > 0)
            configBuilder.withClientCertData(new String(
                    Base64.getEncoder().encode(config.getClientCertificate().getBytes()))
            );
        else
            configBuilder.withClientCertFile(config.getClientCertificateFile());

        if (config.getClientKey() != null && config.getClientKey().length() > 0)
            configBuilder.withClientKeyData(new String(
                    Base64.getEncoder().encode(config.getClientKey().getBytes()))
            );
        else
            configBuilder.withClientKeyFile(config.getClientKeyFile());

        client = new DefaultKubernetesClient(configBuilder.build());
    }

    @Override
    public void close() {
        client.close();
    }
}
