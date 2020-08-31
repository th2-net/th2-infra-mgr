package com.exactpro.th2.schema.schemaeditorbe.k8s;

import com.exactpro.th2.schema.schemaeditorbe.Config;
import com.exactpro.th2.schema.schemaeditorbe.models.ResourceType;
import com.exactpro.th2.schema.schemaeditorbe.models.Th2CustomResource;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;

public class Kubernetes implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(Kubernetes.class);


    private static final String SCHEMA_NAMESPACE_PREFIX = "schema-";

    public static String formatNamespaceName(String schemaName) {
        return SCHEMA_NAMESPACE_PREFIX + schemaName;
    }

    public static String extractSchemaName(String nameSpaceName) {
        if (nameSpaceName.startsWith(SCHEMA_NAMESPACE_PREFIX))
            throw new IllegalArgumentException("Malformed namespace name");
        String schemaName = nameSpaceName.substring(SCHEMA_NAMESPACE_PREFIX.length());
        if (schemaName.equals(""))
            throw new IllegalArgumentException("Malformed namespace name");
        return schemaName;
    }

    public void createOrReplaceCustomResource(Th2CustomResource repoResource) {

        CustomResourceDefinitionContext crdContext = getCrdContext(repoResource);

        var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
        K8sCustomResourceList customResourceList = mixedOperation.inNamespace(nameSpace).list();
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

    public void createCustomResource(Th2CustomResource repoResource) {

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


    public void replaceCustomResource(Th2CustomResource repoResource) throws ResourceNotFoundException {

        CustomResourceDefinitionContext crdContext = getCrdContext(repoResource);

        var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
        K8sCustomResourceList customResourceList = mixedOperation.inNamespace(nameSpace).list();
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


    public Map<String, K8sCustomResource> loadCustomResources(ResourceType type, String apiGroup, String apiVersion) {

        CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                .withGroup(apiGroup)
                .withVersion(apiVersion)
                .withScope("Namespaced")
                .withPlural(type.k8sName())
                .build();

        var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
        K8sCustomResourceList customResourceList = mixedOperation.inNamespace(nameSpace).list();
        List<K8sCustomResource> customResources = customResourceList.getItems();

        Map<String, K8sCustomResource> resources = new HashMap<>();
        for (K8sCustomResource k8sResource : customResources)
            resources.put(k8sResource.getMetadata().getName(), k8sResource);
        return resources;
    }


    public boolean deleteCustomResource(Th2CustomResource repoResource) {

        CustomResourceDefinitionContext crdContext = getCrdContext(repoResource);

        var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
        Resource<K8sCustomResource, K8sCustomResourceDoneable> r = mixedOperation.inNamespace(nameSpace).withName(repoResource.getMetadata().getName());
        return r.delete();

    }


    private CustomResourceDefinitionContext getCrdContext(Th2CustomResource resource) {
        CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                .withGroup(resource.getApiGroup())
                .withVersion(resource.getVersion())
                .withScope("Namespaced")
                .withPlural(ResourceType.forKind(resource.getKind()).k8sName())
                .build();

        return crdContext;
    }

    public void ensureNameSpace() {

        NamespaceList list = client.namespaces().list();
        for (Namespace ns : list.getItems())
            if (ns.getMetadata().getName().equals(nameSpace))
                return;

        // namespace not found, create it
        Namespace n = new Namespace();
        n.setMetadata(new ObjectMeta());
        n.getMetadata().setName(nameSpace);
        client.namespaces().create(n);
    }

    public List<Secret> copySecrets() throws IOException {

        List<Secret> updatedSecrets = new ArrayList<>();

        Map<String, Secret> currentNamespaceSecrets = mapOf(client.secrets().list().getItems());
        Map<String, Secret> targetNamespaceSecrets = mapOf(client.secrets().inNamespace(nameSpace).list().getItems());

        for (String secretName : Config.getInstance().getKubernetes().getSecretNames()) {

            Secret secret = currentNamespaceSecrets.get(secretName);
            if (secret == null)
                throw new IllegalStateException(String.format(
                        "Unable to copy secret '%s' to '%s' namespace, because secret not found in current namespace",
                        secretName, nameSpace
                ));

            if(!targetNamespaceSecrets.containsKey(secretName)){
                secret.getMetadata().setResourceVersion(null);
                secret.getMetadata().setNamespace(nameSpace);
                updatedSecrets.add(client.secrets().inNamespace(nameSpace).createOrReplace(secret));
                logger.info("Secret '{}' has been copied in namespace '{}'", secretName, nameSpace);
            }
        }
        return updatedSecrets;
    }

    private Map<String, Secret> mapOf(List<Secret> secrets) {
        Map<String, Secret> map = new HashMap<>();
        secrets.forEach(secret -> map.put(secret.getMetadata().getName(), secret));
        return map;
    }


    private KubernetesClient client;
    private String nameSpace;
    public Kubernetes(Config.K8sConfig config, String schemaName) {

        // if we are not using custom configutation, let fabric8 handle initialization
        this.nameSpace = Kubernetes.formatNamespaceName(schemaName);
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
