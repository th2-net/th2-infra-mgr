/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exactpro.th2.inframgr.k8s;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.k8s.cr.*;
import com.exactpro.th2.infrarepo.RepositoryResource;
import com.exactpro.th2.infrarepo.ResourceType;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.api.model.networking.v1beta1.Ingress;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.ResourceNotFoundException;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;

import java.io.Closeable;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;

public class Kubernetes implements Closeable {

    public static final String KIND_SECRET = "Secret";
    public static final String KIND_CONFIGMAP = "ConfigMap";
    public static final String KIND_INGRESS = "Ingress";
    public static final String KIND_POD = "Pod";

    public static final String PHASE_ACTIVE = "Active";
    public static final String SECRET_TYPE_OPAQUE = "Opaque";
    public static final String API_VERSION_V1 = "v1";
    private static final String ANTECEDENT_ANNOTATION_KEY = "th2.exactpro.com/antecedent";


    private final String namespacePrefix;

    public String formatNamespaceName(String schemaName) {
        return namespacePrefix + schemaName;
    }

    public static ObjectMeta createMetadataWithAnnotation(String name, String antecedentAnnotationValue) {
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(name);
        Map <String, String> annotations = new HashMap<>();
        annotations.put(ANTECEDENT_ANNOTATION_KEY, antecedentAnnotationValue);
        metadata.setAnnotations(annotations);

        return metadata;
    }

    public String extractSchemaName(String namespaceName) {
        if (!namespaceName.startsWith(namespacePrefix))
            throw new IllegalArgumentException("Malformed namespace name");
        String schemaName = namespaceName.substring(namespacePrefix.length());
        if (schemaName.equals(""))
            throw new IllegalArgumentException("Malformed namespace name");
        return schemaName;
    }

    public void createOrReplaceCustomResource(RepositoryResource repoResource) {
        createOrReplaceCustomResource(repoResource, namespace);
    }

    public void createOrReplaceCustomResource(RepositoryResource repoResource, String namespace) {

        K8sResourceCache cache = K8sResourceCache.INSTANCE;
        Lock lock = cache.lockFor(namespace, repoResource.getKind(), repoResource.getMetadata().getName());

        try {
            lock.lock();

            CustomResourceDefinitionContext crdContext = getCrdContext(repoResource);

            var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class);
            K8sCustomResourceList customResourceList = mixedOperation.inNamespace(namespace).list();
            List<K8sCustomResource> customResources = customResourceList.getItems();
            boolean resourceUpdated = false;

            if (customResources.size() > 0)
                for (K8sCustomResource k8sResource : customResources)
                    if (k8sResource.getMetadata().getName().equals(repoResource.getMetadata().getName())) {

                        k8sResource.setSpec(repoResource.getSpec());
                        k8sResource.setSourceHash(repoResource.getSourceHash());
                        mixedOperation.inNamespace(namespace).createOrReplace(k8sResource);

                        cache.add(namespace, k8sResource);
                        resourceUpdated = true;
                        break;
                    }

            if (!resourceUpdated) {

                K8sCustomResource k8sResource = new K8sCustomResource();
                ObjectMeta metaData = new ObjectMetaBuilder()
                        .withName(repoResource.getMetadata().getName())
                        .withNamespace(namespace)
                        .build();
                k8sResource.setMetadata(metaData);
                k8sResource.setSourceHash(repoResource.getSourceHash());
                k8sResource.setKind(repoResource.getKind());
                k8sResource.setSpec(repoResource.getSpec());
                mixedOperation.inNamespace(namespace).create(k8sResource);

                cache.add(namespace, k8sResource);
            }

        } finally {
            lock.unlock();
        }
    }

    public void createCustomResource(RepositoryResource repoResource) {
        createCustomResource(repoResource, namespace);
    }

    public void createCustomResource(RepositoryResource repoResource, String namespace) {

        K8sResourceCache cache = K8sResourceCache.INSTANCE;
        Lock lock = cache.lockFor(namespace, repoResource.getKind(), repoResource.getMetadata().getName());

        try {
            lock.lock();

            CustomResourceDefinitionContext crdContext = getCrdContext(repoResource);
            var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class);

            K8sCustomResource k8sResource = new K8sCustomResource();
            ObjectMeta metaData = new ObjectMetaBuilder()
                    .withName(repoResource.getMetadata().getName())
                    .withNamespace(namespace)
                    .build();
            k8sResource.setMetadata(metaData);
            k8sResource.setSourceHash(repoResource.getSourceHash());
            k8sResource.setKind(repoResource.getKind());
            k8sResource.setSpec(repoResource.getSpec());
            mixedOperation.inNamespace(namespace).create(k8sResource);

            cache.add(namespace, k8sResource);

        } finally {
            lock.unlock();
        }
    }


    public void replaceCustomResource(RepositoryResource repoResource) throws ResourceNotFoundException {
        replaceCustomResource(repoResource, namespace);
    }


    public void replaceCustomResource(RepositoryResource repoResource, String namespace) throws ResourceNotFoundException {

        K8sResourceCache cache = K8sResourceCache.INSTANCE;
        Lock lock = cache.lockFor(namespace, repoResource.getKind(), repoResource.getMetadata().getName());

        try {
            lock.lock();
            CustomResourceDefinitionContext crdContext = getCrdContext(repoResource);

            var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class);
            K8sCustomResourceList customResourceList = mixedOperation.inNamespace(namespace).list();
            List<K8sCustomResource> customResources = customResourceList.getItems();

            if (customResources.size() > 0)
                for (K8sCustomResource k8sResource : customResources)
                    if (k8sResource.getMetadata().getName().equals(repoResource.getMetadata().getName())) {

                        k8sResource.setSpec(repoResource.getSpec());
                        k8sResource.setSourceHash(repoResource.getSourceHash());
                        mixedOperation.inNamespace(namespace).createOrReplace(k8sResource);

                        cache.add(namespace, k8sResource);
                        return;
                    }
            throw new ResourceNotFoundException("Resource to replace does not exists");

        } finally {
            lock.unlock();
        }
    }


    private K8sCustomResource loadCustomResource(String namespace, ResourceType type, String name) {
        CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                .withGroup(RepositoryResource.getApiGroup(type.k8sApiVersion()))
                .withVersion(RepositoryResource.getVersion(type.k8sApiVersion()))
                .withScope("Namespaced")
                .withPlural(type.k8sName())
                .build();

        var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class);
        return mixedOperation.inNamespace(namespace).withName(name).get();
    }


    public K8sCustomResource loadCustomResource(ResourceType type, String name) {
        return loadCustomResource(namespace, type, name);
    }


    public Map<String, K8sCustomResource> loadCustomResources(ResourceType type) {

        CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                .withGroup(RepositoryResource.getApiGroup(type.k8sApiVersion()))
                .withVersion(RepositoryResource.getVersion(type.k8sApiVersion()))
                .withScope("Namespaced")
                .withPlural(type.k8sName())
                .build();

        var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class);
        K8sCustomResourceList customResourceList = mixedOperation.inNamespace(namespace).list();
        List<K8sCustomResource> customResources = customResourceList.getItems();

        Map<String, K8sCustomResource> resources = new HashMap<>();
        for (K8sCustomResource k8sResource : customResources)
            resources.put(k8sResource.getMetadata().getName(), k8sResource);
        return resources;
    }


    public boolean deleteCustomResource(RepositoryResource repoResource) {
        return deleteCustomResource(repoResource, namespace);
    }


    public boolean deleteCustomResource(RepositoryResource repoResource, String namespace) {

        K8sResourceCache cache = K8sResourceCache.INSTANCE;
        Lock lock = cache.lockFor(namespace, repoResource.getKind(), repoResource.getMetadata().getName());

        try {
            lock.lock();

            CustomResourceDefinitionContext crdContext = getCrdContext(repoResource);

            var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class);
            Resource<K8sCustomResource> r = mixedOperation.inNamespace(namespace).withName(repoResource.getMetadata().getName());
            boolean result = r.delete();

            cache.remove(namespace, repoResource.getKind(), repoResource.getMetadata().getName());
            return result;

        } finally {
            lock.unlock();
        }

    }


    private CustomResourceDefinitionContext getCrdContext(RepositoryResource resource) {

        CustomResourceDefinitionContext crdContext = new CustomResourceDefinitionContext.Builder()
                .withGroup(resource.getApiGroup())
                .withVersion(resource.getVersion())
                .withScope("Namespaced")
                .withPlural(ResourceType.forKind(resource.getKind()).k8sName())
                .build();

        return crdContext;
    }


    public boolean existsNamespace() {

        NamespaceList list = client.namespaces().list();
        for (Namespace ns : list.getItems())
            if (ns.getMetadata().getName().equals(namespace))
                return true;
        return false;
    }

    public Namespace getNamespace(String namespace) {
        return client.namespaces().withName(namespace).get();
    }

    public boolean deleteNamespace() {
        return client.namespaces().withName(namespace).delete();
    }

    public void createNamespace() {

        ObjectMeta meta = new ObjectMeta();
        meta.setName(namespace);

        Namespace ns = new Namespace();
        ns.setMetadata(meta);

        client.namespaces().create(ns);
    }

    public ConfigMap getConfigMap (String configMapName) {
        return client.configMaps().inNamespace(namespace).withName(configMapName).get();
    }

    public Secret getSecret (String secretName) {
        return client.secrets().inNamespace(namespace).withName(secretName).get();
    }

    public List<Secret> getDockerRegistrySecrets() {
        return client.secrets().inNamespace("service").withField("type", "kubernetes.io/dockerconfigjson").list().getItems();
    }

    public void createOrReplaceConfigMap(ConfigMap configMap) {
        configMap.getMetadata().setNamespace(namespace);
        client.configMaps().inNamespace(namespace).createOrReplace(configMap);
    }

    private class FilteringResourceEventHandler<T extends HasMetadata> {

        private boolean namespacePrefixMatches(T obj) {
            return obj.getMetadata().getNamespace().startsWith(namespacePrefix);
        }

        public ResourceEventHandler<T> wrap(ResourceEventHandler resourceEventHandler) {
            return new ResourceEventHandler<T>() {
                @Override
                public void onAdd(T obj) {
                    if (namespacePrefixMatches(obj)) {
                        resourceEventHandler.onAdd(obj);
                    }
                }

                @Override
                public void onUpdate(T oldObj, T newObj) {
                    if (namespacePrefixMatches(oldObj) || namespacePrefixMatches(newObj)) {
                        resourceEventHandler.onUpdate(oldObj, newObj);
                    }
                }

                @Override
                public void onDelete(T obj, boolean deletedFinalStateUnknown) {
                    if (namespacePrefixMatches(obj)) {
                        resourceEventHandler.onDelete(obj, deletedFinalStateUnknown);
                    }
                }
            };
        }
    }

    private SharedIndexInformer<K8sCustomResource> registerSharedInformerForCustomResource(ResourceEventHandler<K8sCustomResource> eventHandler,
                                                                                           SharedInformerFactory factory,
                                                                                           Class type,
                                                                                           Class list) {
        CustomResourceDefinitionContext crdContext = CustomResourceDefinitionContext.fromCustomResourceType(type);

        SharedIndexInformer<K8sCustomResource> customResourceInformer = informerFactory.sharedIndexInformerForCustomResource(
                crdContext,
                type,
                list,
                0);

        customResourceInformer.addEventHandler(new FilteringResourceEventHandler().wrap(eventHandler));

        return customResourceInformer;
    }

    public void registerCustomResourceSharedInformers(ResourceEventHandler eventHandler) {
        SharedInformerFactory informerFactory = getInformerFactory();


        KubernetesDeserializer.registerCustomKind(ResourceType.Th2Box.k8sApiVersion(), ResourceType.Th2Box.kind(), Th2Box.Type.class);
        KubernetesDeserializer.registerCustomKind(ResourceType.Th2CoreBox.k8sApiVersion(), ResourceType.Th2CoreBox.kind(), Th2CoreBox.Type.class);
        KubernetesDeserializer.registerCustomKind(ResourceType.Th2Dictionary.k8sApiVersion(), ResourceType.Th2Dictionary.kind(), Th2Dictionary.Type.class);
        KubernetesDeserializer.registerCustomKind(ResourceType.Th2Estore.k8sApiVersion(), ResourceType.Th2Estore.kind(), Th2Estore.Type.class);
        KubernetesDeserializer.registerCustomKind(ResourceType.Th2Mstore.k8sApiVersion(), ResourceType.Th2Mstore.kind(), Th2Mstore.Type.class);
        KubernetesDeserializer.registerCustomKind(ResourceType.Th2Link.k8sApiVersion(), ResourceType.Th2Link.kind(), Th2Link.Type.class);

        //Register informers for custom resources
        registerSharedInformerForCustomResource(eventHandler, informerFactory, Th2Box.Type.class, Th2Box.List.class);
        registerSharedInformerForCustomResource(eventHandler, informerFactory, Th2CoreBox.Type.class, Th2CoreBox.List.class);
        registerSharedInformerForCustomResource(eventHandler, informerFactory, Th2Dictionary.Type.class, Th2Dictionary.List.class);
        registerSharedInformerForCustomResource(eventHandler, informerFactory, Th2Estore.Type.class, Th2Estore.List.class);
        registerSharedInformerForCustomResource(eventHandler, informerFactory, Th2Mstore.Type.class, Th2Mstore.List.class);
        registerSharedInformerForCustomResource(eventHandler, informerFactory, Th2Link.Type.class, Th2Link.List.class);
    }

    public void registerSharedInformersAll (ResourceEventHandler eventHandler) {
        registerCustomResourceSharedInformers(eventHandler);
        SharedInformerFactory factory = getInformerFactory();

        var filteringEventHanled = new FilteringResourceEventHandler().wrap(eventHandler);
        factory.sharedIndexInformerFor(Deployment.class, DeploymentList.class, 0)
                .addEventHandler(filteringEventHanled);

        factory.sharedIndexInformerFor(Pod.class, PodList.class, 0)
                .addEventHandler(filteringEventHanled);

        factory.sharedIndexInformerFor(Service.class, ServiceList.class, 0)
                .addEventHandler(filteringEventHanled);

        factory.sharedIndexInformerFor(ConfigMap.class, ConfigMapList.class, 0)
                .addEventHandler(filteringEventHanled);
    }

    private Map<String, Secret> mapOf(List<Secret> secrets) {
        Map<String, Secret> map = new HashMap<>();
        if (secrets != null)
            secrets.forEach(secret -> map.put(secret.getMetadata().getName(), secret));
        return map;
    }

    private SharedInformerFactory informerFactory;

    private synchronized SharedInformerFactory getInformerFactory() {
        if (informerFactory == null) {
            informerFactory = client.informers();
        }

        return informerFactory;
    }

    public void startInformers () {
        informerFactory.startAllRegisteredInformers();
    }

    private KubernetesClient client;
    private String namespace;
    public Kubernetes(Config.K8sConfig config, String schemaName) {

        // if we are not using custom configutation, let fabric8 handle initialization
        this.namespacePrefix = config.getNamespacePrefix();
        this.namespace = formatNamespaceName(schemaName);
        this._currentNamespace = new CurrentNamespace();

        if (!config.useCustomConfig()) {
            client = new DefaultKubernetesClient();
            return;
        }

        // customize client according to configuration specified
        ConfigBuilder configBuilder = new ConfigBuilder();
        configBuilder
                .withMasterUrl(config.getMasterURL())
                .withNamespace(config.getDefaultNamespace())
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


    public Map<String, Secret> getSecrets() {
        return mapOf(client.secrets().inNamespace(namespace).list().getItems());
    }


    public Secret createOrReplaceSecret(Secret secret) {
        secret.getMetadata().setNamespace(namespace);
        return client.secrets().inNamespace(namespace).createOrReplace(secret);
    }


    public String getNamespaceName() {
        return namespace;
    }

    @Override
    public void close() {
        client.close();
    }

    private CurrentNamespace _currentNamespace;
    public  CurrentNamespace currentNamespace() {
        return _currentNamespace;
    }

    public void createOrRepaceIngress(Ingress ingress) {
        client.network().ingresses().inNamespace(namespace).createOrReplace(ingress);
    }

    public Ingress getIngress(String ingressName) {
        return client.network().ingresses().inNamespace(namespace).withName(ingressName).get();
    }

    public boolean deletePodWithName(String podName, boolean force) {
        if (force) {
            return client.pods().inNamespace(namespace).withName(podName).withGracePeriod(0).delete();
        } else {
            return client.pods().inNamespace(namespace).withName(podName).delete();
        }
    }

    public final class CurrentNamespace {
        public ConfigMap getConfigMap(String name) {
            return client.configMaps().withName(name).get();
        }

        public Map<String, Secret> getSecrets() {
            return mapOf(client.secrets().list().getItems());
        }
        public K8sCustomResource loadCustomResource(ResourceType type, String name) {
            return Kubernetes.this.loadCustomResource(client.getNamespace(), type, name);
        }

        public Ingress getIngress(String ingressName) {
            return client.network().ingresses().withName(ingressName).get();
        }
    }
}