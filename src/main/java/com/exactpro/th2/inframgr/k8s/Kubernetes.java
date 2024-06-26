/*
 * Copyright 2020-2023 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.inframgr.k8s.cr.*;
import com.exactpro.th2.inframgr.util.cfg.BehaviourCfg;
import com.exactpro.th2.inframgr.util.cfg.K8sConfig;
import com.exactpro.th2.infrarepo.ResourceType;
import com.exactpro.th2.infrarepo.repo.RepositoryResource;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.locks.Lock;

import static com.exactpro.th2.inframgr.initializer.SchemaInitializer.HELM_ANNOTATION_KEY_PREFIX;
import static io.fabric8.kubernetes.internal.KubernetesDeserializer.registerCustomKind;

public class Kubernetes implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Kubernetes.class);

    public static final String KIND_SECRET = "Secret";

    public static final String KIND_CONFIGMAP = "ConfigMap";

    public static final String KIND_INGRESS = "Ingress";

    public static final String KIND_SERVICE_MONITOR = "ServiceMonitor";

    public static final String KIND_POD = "Pod";

    public static final String PHASE_ACTIVE = "Active";

    public static final String SECRET_TYPE_OPAQUE = "Opaque";

    public static final String API_VERSION_V1 = "v1";

    private static final String ANTECEDENT_ANNOTATION_KEY = "th2.exactpro.com/antecedent";

    private final boolean permittedToRemoveNamespace;

    private final String namespacePrefix;

    public String formatNamespaceName(String schemaName) {
        return namespacePrefix + schemaName;
    }

    public static ObjectMeta createMetaDataWithNewAnnotations(String name, String antecedentAnnotationValue) {
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(name);
        Map<String, String> annotations = new HashMap<>();
        annotations.put(ANTECEDENT_ANNOTATION_KEY, antecedentAnnotationValue);
        metadata.setAnnotations(annotations);

        return metadata;
    }

    public static ObjectMeta createMetadataWithPreviousAnnotations(
            String name,
            String antecedentAnnotationValue,
            Map<String, String> originalAnnotations
    ) {
        ObjectMeta metadata = new ObjectMeta();
        metadata.setName(name);
        Map<String, String> newAnnotations = new HashMap<>();
        if (originalAnnotations != null) {
            for (var entry : originalAnnotations.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                if (!entry.getKey().startsWith(HELM_ANNOTATION_KEY_PREFIX)) {
                    newAnnotations.put(entry.getKey(), entry.getValue());
                }
            }
        }
        newAnnotations.put(ANTECEDENT_ANNOTATION_KEY, antecedentAnnotationValue);
        metadata.setAnnotations(newAnnotations);

        return metadata;
    }

    public String extractSchemaName(String namespaceName) {
        if (!namespaceName.startsWith(namespacePrefix)) {
            throw new IllegalArgumentException("Malformed namespace name");
        }
        String schemaName = namespaceName.substring(namespacePrefix.length());
        if (StringUtils.isEmpty(schemaName)) {
            throw new IllegalArgumentException("Malformed namespace name");
        }
        return schemaName;
    }

    public void createOrReplaceCustomResource(RepositoryResource repoResource) {
        createOrReplaceCustomResource(repoResource, namespace);
    }

    public <T extends K8sCustomResource, L extends DefaultKubernetesResourceList<T>> void createOrReplaceCustomResource(
            RepositoryResource repoResource,
            String namespace) {

        K8sResourceCache cache = K8sResourceCache.INSTANCE;
        Lock lock = cache.lockFor(namespace, repoResource.getKind(), repoResource.getMetadata().getName());

        try {
            lock.lock();

            MixedOperation<T, L, Resource<T>> operation = operations.get(repoResource.getKind());
            var customResourceList = operation.inNamespace(namespace).list();
            List<T> customResources = customResourceList.getItems();
            boolean resourceUpdated = false;

            if (!customResources.isEmpty()) {
                for (T k8sResource : customResources) {
                    if (k8sResource.getMetadata().getName().equals(repoResource.getMetadata().getName())) {
                        k8sResource.getMetadata().setAnnotations(repoResource.getMetadata().getAnnotations());
                        k8sResource.setSpec(repoResource.getSpec());
                        k8sResource.setSourceHash(repoResource.getSourceHash());
                        operation.inNamespace(namespace).resource(k8sResource).createOrReplace();

                        cache.add(namespace, k8sResource);
                        resourceUpdated = true;
                        break;
                    }
                }
            }

            if (!resourceUpdated) {
                K8sCustomResource k8sResource = buildCustomResource(repoResource, namespace);
                operation.inNamespace(namespace).resource((T) k8sResource).create();

                cache.add(namespace, k8sResource);
            }

        } finally {
            lock.unlock();
        }
    }

    private K8sCustomResource buildCustomResource(RepositoryResource repoResource, String namespace) {
        K8sCustomResource k8sResource = new K8sCustomResource();
        ObjectMeta metaData = repoResource.getMetadata();
        metaData.setNamespace(namespace);
        k8sResource.setMetadata(metaData);
        k8sResource.setSourceHash(repoResource.getSourceHash());
        k8sResource.setCommitHash(repoResource.getCommitHash());
        k8sResource.setDetectionTime(repoResource.getDetectionTime());
        k8sResource.setKind(repoResource.getKind());
        k8sResource.setSpec(repoResource.getSpec());

        return k8sResource;
    }

    public void createCustomResource(RepositoryResource repoResource) {
        createCustomResource(repoResource, namespace);
    }

    public <T extends K8sCustomResource, L extends DefaultKubernetesResourceList<T>> void createCustomResource(
            RepositoryResource repoResource,
            String namespace) {

        K8sResourceCache cache = K8sResourceCache.INSTANCE;
        Lock lock = cache.lockFor(namespace, repoResource.getKind(), repoResource.getMetadata().getName());

        try {
            lock.lock();

            MixedOperation<T, L, Resource<T>> operation = operations.get(repoResource.getKind());
            K8sCustomResource k8sResource = buildCustomResource(repoResource, namespace);
            operation.inNamespace(namespace).resource((T) k8sResource).create();

            cache.add(namespace, k8sResource);

        } finally {
            lock.unlock();
        }
    }

    public void replaceCustomResource(RepositoryResource repoResource) throws ResourceNotFoundException {
        replaceCustomResource(repoResource, namespace);
    }

    public <T extends K8sCustomResource, L extends DefaultKubernetesResourceList<T>> void replaceCustomResource(
            RepositoryResource repoResource,
            String namespace) throws ResourceNotFoundException {

        K8sResourceCache cache = K8sResourceCache.INSTANCE;
        Lock lock = cache.lockFor(namespace, repoResource.getKind(), repoResource.getMetadata().getName());

        try {
            lock.lock();

            MixedOperation<T, L, Resource<T>> operation = operations.get(repoResource.getKind());
            var customResourceList = operation.inNamespace(namespace).list();
            List<T> customResources = customResourceList.getItems();

            if (!customResources.isEmpty()) {
                for (T k8sResource : customResources) {
                    if (k8sResource.getMetadata().getName().equals(repoResource.getMetadata().getName())) {

                        k8sResource.getMetadata().setAnnotations(repoResource.getMetadata().getAnnotations());
                        k8sResource.setSpec(repoResource.getSpec());
                        k8sResource.setSourceHash(repoResource.getSourceHash());
                        k8sResource.setCommitHash(repoResource.getCommitHash());
                        k8sResource.setDetectionTime(repoResource.getDetectionTime());
                        operation.inNamespace(namespace).resource(k8sResource).createOrReplace();

                        cache.add(namespace, k8sResource);
                        return;
                    }
                }
            }
            throw new ResourceNotFoundException("Resource to replace does not exists");

        } finally {
            lock.unlock();
        }
    }

    public ServiceMonitor.Type loadServiceMonitor(String namespace, String name) {
        var mixedOperation = client.resources(ServiceMonitor.Type.class);
        return mixedOperation.inNamespace(namespace).withName(name).get();
    }

    public void createServiceMonitor(ServiceMonitor.Type serviceMonitor) {
        var mixedOperation = client.resources(ServiceMonitor.Type.class);
        mixedOperation.inNamespace(namespace).resource(serviceMonitor).createOrReplace();
    }

    private <T extends K8sCustomResource, L extends DefaultKubernetesResourceList<T>>
    K8sCustomResource loadCustomResource(String namespace, ResourceType type, String name) {
        MixedOperation<T, ? extends L, Resource<T>> mixedOperation = operations.get(type.kind());
        return mixedOperation.inNamespace(namespace).withName(name).get();
    }

    public K8sCustomResource loadCustomResource(ResourceType type, String name) {
        return loadCustomResource(namespace, type, name);
    }

    public <T extends K8sCustomResource, L extends DefaultKubernetesResourceList<T>>
    Map<String, K8sCustomResource> loadCustomResources(ResourceType type) {

        MixedOperation<T, ? extends L, Resource<T>> mixedOperation = operations.get(type.kind());
        var customResourceList = mixedOperation.inNamespace(namespace).list();
        List<T> customResources = customResourceList.getItems();

        Map<String, K8sCustomResource> resources = new HashMap<>();
        for (K8sCustomResource k8sResource : customResources) {
            resources.put(k8sResource.getMetadata().getName(), k8sResource);
        }
        return resources;
    }

    public List<StatusDetails> deleteCustomResource(RepositoryResource repoResource) {
        return deleteCustomResource(repoResource, namespace);
    }

    public <T extends K8sCustomResource, L extends DefaultKubernetesResourceList<T>>
    List<StatusDetails> deleteCustomResource(RepositoryResource repoResource, String namespace) {

        K8sResourceCache cache = K8sResourceCache.INSTANCE;
        Lock lock = cache.lockFor(namespace, repoResource.getKind(), repoResource.getMetadata().getName());

        try {
            lock.lock();

            MixedOperation<T, L, Resource<T>> operation = operations.get(repoResource.getKind());
            Resource<T> r = operation.inNamespace(namespace).withName(repoResource.getMetadata().getName());
            List<StatusDetails> result = r.delete();

            cache.remove(namespace, repoResource.getKind(), repoResource.getMetadata().getName());
            return result;

        } finally {
            lock.unlock();
        }

    }

    public boolean existsNamespace() {

        NamespaceList list = client.namespaces().list();
        for (Namespace ns : list.getItems()) {
            if (ns.getMetadata().getName().equals(namespace)) {
                return true;
            }
        }
        return false;
    }

    public boolean namespaceActive() {
        String namespacePhase = client.namespaces().withName(namespace).get().getStatus().getPhase();
        return namespacePhase.equals(Kubernetes.PHASE_ACTIVE);
    }

    public Namespace getNamespace(String namespace) {
        return client.namespaces().withName(namespace).get();
    }

    public void deleteNamespace() {
        if (permittedToRemoveNamespace) {
            Collection<StatusDetails> statusDetails = client.namespaces().withName(namespace).delete();
            LOGGER.info("Deleted '{}' namespace, status details: {}", namespace, statusDetails);
        } else {
            LOGGER.warn("Stopping namespace \"{}\" maintenance", namespace);
        }
    }

    public void createNamespace() {

        ObjectMeta meta = new ObjectMeta();
        meta.setName(namespace);

        Namespace ns = new Namespace();
        ns.setMetadata(meta);

        client.resource(ns).create();
    }

    public ConfigMap getConfigMap(String configMapName) {
        return client.configMaps().inNamespace(namespace).withName(configMapName).get();
    }

    public Secret getSecret(String secretName) {
        return client.secrets().inNamespace(namespace).withName(secretName).get();
    }

    public List<Secret> getDockerRegistrySecrets() {
        return client.secrets()
                .withField("type", "kubernetes.io/dockerconfigjson")
                .list()
                .getItems();
    }

    public void createOrReplaceConfigMap(ConfigMap configMap) {
        configMap.getMetadata().setNamespace(namespace);
        client.resource(configMap).inNamespace(namespace).createOrReplace();
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

    private SharedIndexInformer<K8sCustomResource> registerSharedInformerForCustomResource(
            ResourceEventHandler<K8sCustomResource> eventHandler,
            Class type) {

        SharedIndexInformer<K8sCustomResource> customResourceInformer = informerFactory.sharedIndexInformerFor(
                type,
                0
        );

        customResourceInformer.addEventHandler(new FilteringResourceEventHandler().wrap(eventHandler));

        return customResourceInformer;
    }

    public void registerCustomResourceSharedInformers(ResourceEventHandler eventHandler) {
        getInformerFactory();

        registerCustomKind(ResourceType.Th2Box.k8sApiVersion(), ResourceType.Th2Box.kind(), Th2Box.Type.class);
        registerCustomKind(
                ResourceType.Th2CoreBox.k8sApiVersion(), ResourceType.Th2CoreBox.kind(), Th2CoreBox.Type.class
        );
        registerCustomKind(
                ResourceType.Th2Dictionary.k8sApiVersion(), ResourceType.Th2Dictionary.kind(), Th2Dictionary.Type.class
        );
        registerCustomKind(ResourceType.Th2Estore.k8sApiVersion(), ResourceType.Th2Estore.kind(), Th2Estore.Type.class);
        registerCustomKind(ResourceType.Th2Mstore.k8sApiVersion(), ResourceType.Th2Mstore.kind(), Th2Mstore.Type.class);

        //Register informers for custom resources
        registerSharedInformerForCustomResource(eventHandler, Th2Box.Type.class);
        registerSharedInformerForCustomResource(eventHandler, Th2CoreBox.Type.class);
        registerSharedInformerForCustomResource(eventHandler, Th2Dictionary.Type.class);
        registerSharedInformerForCustomResource(eventHandler, Th2Estore.Type.class);
        registerSharedInformerForCustomResource(eventHandler, Th2Mstore.Type.class);
    }

    public void registerSharedInformersAll(ResourceEventHandler eventHandler) {
        registerCustomResourceSharedInformers(eventHandler);
        SharedInformerFactory factory = getInformerFactory();

        var filteringEventHandled = new FilteringResourceEventHandler().wrap(eventHandler);
        factory.sharedIndexInformerFor(Deployment.class, 0)
                .addEventHandler(filteringEventHandled);

        factory.sharedIndexInformerFor(Pod.class, 0)
                .addEventHandler(filteringEventHandled);

        factory.sharedIndexInformerFor(Service.class, 0)
                .addEventHandler(filteringEventHandled);

        factory.sharedIndexInformerFor(ConfigMap.class, 0)
                .addEventHandler(filteringEventHandled);
    }

    private Map<String, Secret> mapOf(List<Secret> secrets) {
        Map<String, Secret> map = new HashMap<>();
        if (secrets != null) {
            secrets.forEach(secret -> map.put(secret.getMetadata().getName(), secret));
        }
        return map;
    }

    private SharedInformerFactory informerFactory;

    private synchronized SharedInformerFactory getInformerFactory() {
        if (informerFactory == null) {
            informerFactory = client.informers();
        }

        return informerFactory;
    }

    public void startInformers() {
        informerFactory.startAllRegisteredInformers();
    }

    private final KubernetesClient client;

    private Map<String, MixedOperation> operations;

    private final String schemaName;

    private final String namespace;

    public Kubernetes(BehaviourCfg behaviour, K8sConfig config, String schemaName) {
        this.permittedToRemoveNamespace = behaviour.isPermittedToRemoveNamespace();
        // if we are not using custom configuration, let fabric8 handle initialization
        this.namespacePrefix = config.getNamespacePrefix();
        this.schemaName = schemaName;
        this.namespace = formatNamespaceName(schemaName);
        this.currentNamespace = new CurrentNamespace();

        if (!config.useCustomConfig()) {
            client = new KubernetesClientBuilder().build();
            generateMixedOperations();
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
        if (config.getClientCertificate() != null && !config.getClientCertificate().isEmpty()) {
            configBuilder.withClientCertData(new String(
                    Base64.getEncoder().encode(config.getClientCertificate().getBytes()))
            );
        } else {
            configBuilder.withClientCertFile(config.getClientCertificateFile());
        }

        if (config.getClientKey() != null && !config.getClientKey().isEmpty()) {
            configBuilder.withClientKeyData(new String(
                    Base64.getEncoder().encode(config.getClientKey().getBytes()))
            );
        } else {
            configBuilder.withClientKeyFile(config.getClientKeyFile());
        }

        client = new KubernetesClientBuilder().withConfig(configBuilder.build()).build();
        generateMixedOperations();
    }

    private void generateMixedOperations() {
        operations = Map.of(
                ResourceType.Th2Box.kind(), client.resources(Th2Box.Type.class),
                ResourceType.Th2CoreBox.kind(), client.resources(Th2CoreBox.Type.class),
                ResourceType.Th2Estore.kind(), client.resources(Th2Estore.Type.class),
                ResourceType.Th2Mstore.kind(), client.resources(Th2Mstore.Type.class),
                ResourceType.Th2Dictionary.kind(), client.resources(Th2Dictionary.Type.class),
                ResourceType.Th2Job.kind(), client.resources(Th2Job.Type.class)
        );
    }

    public Map<String, Secret> getSecrets() {
        return mapOf(client.secrets().inNamespace(namespace).list().getItems());
    }

    public Secret createOrReplaceSecret(Secret secret) {
        secret.getMetadata().setNamespace(namespace);
        return client.resource(secret).inNamespace(namespace).createOrReplace();
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getNamespaceName() {
        return namespace;
    }

    @Override
    public void close() {
        client.close();
    }

    private final CurrentNamespace currentNamespace;

    public CurrentNamespace currentNamespace() {
        return currentNamespace;
    }

    public List<StatusDetails> deletePodWithName(String podName, boolean force) {
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

        public ServiceMonitor.Type loadServiceMonitor(String name) {
            return Kubernetes.this.loadServiceMonitor(client.getNamespace(), name);
        }
    }
}
