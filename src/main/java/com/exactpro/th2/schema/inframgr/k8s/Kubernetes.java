/*
 * Copyright 2020-2020 Exactpro (Exactpro Systems Limited)
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
package com.exactpro.th2.schema.inframgr.k8s;

import com.exactpro.th2.schema.inframgr.Config;
import com.exactpro.th2.schema.inframgr.models.RepositoryResource;
import com.exactpro.th2.schema.inframgr.models.ResourceType;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.internal.KubernetesDeserializer;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.locks.Lock;

public class Kubernetes implements Closeable {

    private String namespacePrefix;

    public String formatNamespaceName(String schemaName) {
        return namespacePrefix + schemaName;
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

            var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
            K8sCustomResourceList customResourceList = mixedOperation.inNamespace(namespace).list();
            List<K8sCustomResource> customResources = customResourceList.getItems();
            boolean resourceUpdated = false;

            if (customResources.size() > 0)
                for (K8sCustomResource k8sResource : customResources)
                    if (k8sResource.getMetadata().getName().equals(repoResource.getMetadata().getName())) {

                        k8sResource.setSpec(repoResource.getSpec());
                        k8sResource.setSourceHash(repoResource.getSourceHash());
                        k8sResource.getMetadata().setResourceVersion(null);
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
            var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);

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

            var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
            K8sCustomResourceList customResourceList = mixedOperation.inNamespace(namespace).list();
            List<K8sCustomResource> customResources = customResourceList.getItems();

            if (customResources.size() > 0)
                for (K8sCustomResource k8sResource : customResources)
                    if (k8sResource.getMetadata().getName().equals(repoResource.getMetadata().getName())) {

                        k8sResource.setSpec(repoResource.getSpec());
                        k8sResource.setSourceHash(repoResource.getSourceHash());
                        k8sResource.getMetadata().setResourceVersion(null);
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

        var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
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

        var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
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

            var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
            Resource<K8sCustomResource, K8sCustomResourceDoneable> r = mixedOperation.inNamespace(namespace).withName(repoResource.getMetadata().getName());
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

    public void createNamespace() {

        ObjectMeta meta = new ObjectMeta();
        meta.setName(namespace);

        Namespace ns = new Namespace();
        ns.setMetadata(meta);

        client.namespaces().create(ns);
    }


    public void createOrReplaceConfigMap(ConfigMap configMap) {
        configMap.getMetadata().setResourceVersion(null);
        configMap.getMetadata().setNamespace(namespace);
        client.configMaps().inNamespace(namespace).createOrReplace(configMap);
    }


    public List<Watch> registerWatchers(Watcher watcher) {

        List<Watch> watches = new LinkedList<>();

        for (ResourceType t : ResourceType.values())
            if (t.isK8sResource() && !t.equals(ResourceType.HelmRelease)) {
                RepositoryResource resource = new RepositoryResource(t);
                resource.setKind(t.kind());

                KubernetesDeserializer.registerCustomKind(resource.getApiVersion(), resource.getKind(), K8sCustomResource.class);
                CustomResourceDefinitionContext crdContext = getCrdContext(resource);

                var mixedOperation = client.customResources(crdContext, K8sCustomResource.class, K8sCustomResourceList.class, K8sCustomResourceDoneable.class);
                Watch watch =  mixedOperation.inAnyNamespace().watch(new Watcher<>() {
                    @Override
                    public void eventReceived(Action action, K8sCustomResource resource) {
                        if (resource.getMetadata().getNamespace().startsWith(namespacePrefix))
                            watcher.eventReceived(action, resource);
                    }

                    @Override
                    public void onClose(KubernetesClientException cause) {
                        watcher.onClose(cause);
                    }
                });
                watches.add(watch);
            }

        return watches;
    }


    private Map<String, Secret> mapOf(List<Secret> secrets) {
        Map<String, Secret> map = new HashMap<>();
        if (secrets != null)
            secrets.forEach(secret -> map.put(secret.getMetadata().getName(), secret));
        return map;
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
        secret.getMetadata().setResourceVersion(null);
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

    public final class CurrentNamespace {
        public ConfigMap getConfigMap(String name) {
            ConfigMap configMap = client.configMaps().withName(name).get();
            return configMap;
        }

        public Map<String, Secret> getSecrets() {
            return mapOf(client.secrets().list().getItems());
        }
        public K8sCustomResource loadCustomResource(ResourceType type, String name) {
            return Kubernetes.this.loadCustomResource(client.getNamespace(), type, name);
        }

    }
}
