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
import com.exactpro.th2.inframgr.SchemaEventRouter;
import com.exactpro.th2.inframgr.docker.monitoring.DynamicResourceProcessor;
import com.exactpro.th2.inframgr.initializer.LoggingConfigMap;
import com.exactpro.th2.inframgr.initializer.BookConfiguration;
import com.exactpro.th2.inframgr.initializer.Th2BoxConfigurations;
import com.exactpro.th2.inframgr.initializer.SchemaInitializer;
import com.exactpro.th2.inframgr.metrics.ManagerMetrics;
import com.exactpro.th2.inframgr.repository.RepositoryUpdateEvent;
import com.exactpro.th2.inframgr.util.SchemaErrorPrinter;
import com.exactpro.th2.inframgr.util.Strings;
import com.exactpro.th2.inframgr.util.Th2DictionaryProcessor;
import com.exactpro.th2.infrarepo.ResourceType;
import com.exactpro.th2.infrarepo.SchemaUtils;
import com.exactpro.th2.infrarepo.git.Gitter;
import com.exactpro.th2.infrarepo.git.GitterContext;
import com.exactpro.th2.infrarepo.repo.Repository;
import com.exactpro.th2.infrarepo.repo.RepositoryResource;
import com.exactpro.th2.infrarepo.repo.RepositorySnapshot;
import com.exactpro.th2.infrarepo.settings.RepositorySettingsResource;
import com.exactpro.th2.infrarepo.settings.RepositorySettingsSpec;
import com.exactpro.th2.validator.SchemaValidationContext;
import com.exactpro.th2.validator.SchemaValidator;
import com.exactpro.th2.validator.util.ResourceUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.prometheus.client.Histogram;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rx.schedulers.Schedulers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.exactpro.th2.inframgr.statuswatcher.ResourcePath.annotationFor;

@Component
public class K8sSynchronization {

    private static final int SYNC_PARALLELIZATION_THREADS = 3;

    private static final Logger logger = LoggerFactory.getLogger(K8sSynchronization.class);

    private Config config;

    private final K8sSynchronizationJobQueue jobQueue = new K8sSynchronizationJobQueue();

    private void deleteNamespace(String schemaName) {
        try (Kubernetes kube = new Kubernetes(config.getKubernetes(), schemaName)) {
            if (kube.existsNamespace()) {
                logger.info("Removing schema \"{}\" from kubernetes", schemaName);
                DynamicResourceProcessor.deleteSchema(schemaName);
                K8sResourceCache.INSTANCE.removeNamespace(kube.formatNamespaceName(schemaName));
                kube.deleteNamespace();
            }
        } catch (Exception e) {
            logger.error("Exception removing schema \"{}\" from kubernetes", schemaName, e);
        }
    }

    private void synchronizeNamespace(String schemaName,
                                      Map<String, Map<String, RepositoryResource>> repositoryResources,
                                      RepositorySettingsResource repositorySettings,
                                      String fullCommitRef) throws Exception {

        Histogram.Timer timer = ManagerMetrics.getCommitTimer();
        String shortCommitRef = getShortCommitRef(fullCommitRef);
        try (Kubernetes kube = new Kubernetes(config.getKubernetes(), schemaName)) {
            SchemaInitializer.ensureSchema(schemaName, kube);
            validateSchema(schemaName, repositoryResources, repositorySettings, shortCommitRef);

            RepositorySettingsSpec settingsSpec = repositorySettings.getSpec();
            try {
                LoggingConfigMap.copyLoggingConfigMap(
                        settingsSpec.getLogLevelRoot(),
                        settingsSpec.getLogLevelTh2(),
                        fullCommitRef,
                        kube
                );
            } catch (Exception e) {
                logger.error("Exception copying logging config map to schema \"{}\"", schemaName, e);
            }

            BookConfiguration.synchronizeBookConfig(
                    settingsSpec.getBookConfig(),
                    kube,
                    fullCommitRef
            );

            Th2BoxConfigurations.synchronizeBoxConfigMaps(
                    settingsSpec.getMqRouter(),
                    settingsSpec.getGrpcRouter(),
                    settingsSpec.getCradleManager(),
                    fullCommitRef,
                    kube
            );

            syncCustomResourcesWithK8s(schemaName, repositoryResources, kube, shortCommitRef);

        } finally {
            timer.observeDuration();
        }
    }

    private void validateSchema(String schemaName,
                                Map<String, Map<String, RepositoryResource>> repositoryResources,
                                RepositorySettingsResource repositorySettings,
                                String commit)
            throws JsonProcessingException {
        // validate: schema links, urlPaths, secret custom config.
        SchemaValidationContext validationContext = SchemaValidator.validate(
                schemaName,
                config.getKubernetes().getNamespacePrefix(),
                config.getKubernetes().getStorageServiceUrl(),
                repositorySettings,
                repositoryResources
        );
        if (!validationContext.isValid()) {
            logger.warn("Schema \"{}\" contains errors. [{}]", schemaName, commit);
            SchemaErrorPrinter.printErrors(validationContext.getReport(), commit);
            // remove invalid links from boxes
            var allBoxes = ResourceUtils.collectAllBoxes(repositoryResources);
            SchemaValidator.removeInvalidLinks(
                    validationContext,
                    allBoxes.values()
            );
        } else {
            logger.info("Schema \"{}\" validated. Proceeding with namespace synchronization. [{}]", schemaName, commit);
        }
    }

    private void syncCustomResourcesWithK8s(String schemaName,
                                            Map<String, Map<String, RepositoryResource>> repositoryResources,
                                            Kubernetes kube, String shortCommitRef) {
        String namespace = kube.formatNamespaceName(schemaName);
        K8sResourceCache cache = K8sResourceCache.INSTANCE;
        Map<String, Map<String, K8sCustomResource>> k8sResources = loadCustomResources(kube);
        // synchronize by resource type
        for (ResourceType type : ResourceType.values()) {
            if (type.isMangedResource() && !type.equals(ResourceType.Th2Job)) {
                var typeKind = type.kind();
                Map<String, RepositoryResource> resources = repositoryResources.get(typeKind);
                Map<String, K8sCustomResource> customResources = k8sResources.get(typeKind);

                for (RepositoryResource resource : resources.values()) {
                    String resourceName = resource.getMetadata().getName();
                    String resourceLabel = "\"" + annotationFor(namespace, typeKind, resourceName) + "\"";
                    String hashTag = Strings.formatHash(resource.getSourceHash());
                    // add resource to cache
                    cache.add(namespace, resource);
                    //check resources for dynamic image version range
                    DynamicResourceProcessor.checkResource(resource, schemaName);
                    // check repository items against k8s
                    if (!customResources.containsKey(resourceName)) {
                        // create custom resources that do not exist in k8s
                        logger.info("Creating resource {} {}. [commit: {}]",
                                resourceLabel, hashTag, shortCommitRef);
                        try {
                            kube.createCustomResource(resource);
                        } catch (Exception e) {
                            logger.error("Exception creating resource {} {}. [commit: {}]",
                                    resourceLabel, hashTag, shortCommitRef, e);
                        }
                    } else {
                        // compare object's hashes and update custom resources whose hash labels do not match
                        K8sCustomResource cr = customResources.get(resourceName);

                        if (!(resource.getSourceHash() == null
                                || resource.getSourceHash().equals(cr.getSourceHash()))) {
                            // update custom resource
                            logger.info("Updating resource {} {}. [commit: {}]",
                                    resourceLabel, hashTag, shortCommitRef);
                            try {
                                kube.replaceCustomResource(resource);
                            } catch (Exception e) {
                                logger.error("Exception updating resource {} {}. [commit: {}]",
                                        resourceLabel, hashTag, shortCommitRef, e);
                            }
                        }
                    }
                }

                // delete k8s resources that do not exist in repository
                for (String resourceName : customResources.keySet()) {
                    if (!resources.containsKey(resourceName)) {
                        String resourceLabel = annotationFor(namespace, typeKind, resourceName);
                        try {
                            logger.info("Deleting resource {}. [commit: {}]", resourceLabel, shortCommitRef);
                            RepositoryResource resource = new RepositoryResource();
                            resource.setKind(typeKind);
                            ObjectMeta meta = new ObjectMeta();
                            meta.setName(resourceName);
                            resource.setMetadata(meta);
                            DynamicResourceProcessor.checkResource(resource, schemaName, true);
                            kube.deleteCustomResource(resource);
                        } catch (Exception e) {
                            logger.error("Exception deleting resource {}. [commit: {}]",
                                    resourceLabel, shortCommitRef, e);
                        }
                    }
                }
            }
        }
    }

    private Map<String, Map<String, K8sCustomResource>> loadCustomResources(Kubernetes kube) {
        Map<String, Map<String, K8sCustomResource>> k8sResources = new HashMap<>();
        for (ResourceType t : ResourceType.values()) {
            if (t.isMangedResource() && !t.equals(ResourceType.Th2Job)) {
                k8sResources.put(t.kind(), kube.loadCustomResources(t));
            }
        }
        return k8sResources;
    }

    public void synchronizeBranch(String branch, long detectionTime) {

        if (!K8sCustomResource.isSchemaNameValid(branch)) {
            logger.error("Schema name \"{}\" is invalid. " +
                            "Name length must less than {} characters and match pattern: \"{}\"",
                    branch, K8sCustomResource.SCHEMA_NAME_MAX_LENGTH, K8sCustomResource.RESOURCE_NAME_REGEXP);
            return;
        }

        try {
            logger.info("Checking settings for schema \"{}\"", branch);

            // get repository items
            GitterContext ctx = GitterContext.getContext(config.getGit());
            Gitter gitter = ctx.getGitter(branch);
            RepositorySnapshot snapshot;
            RepositorySettingsResource repositorySettings;
            try {
                gitter.lock();
                repositorySettings = Repository.getSettings(gitter);
                if (repositorySettings != null && repositorySettings.getSpec().isK8sPropagationDenied()) {
                    deleteNamespace(branch);
                    return;
                }
                if (repositorySettings == null || !repositorySettings.getSpec().isK8sSynchronizationRequired()) {
                    logger.info("Ignoring schema \"{}\" as it is not configured for synchronization",
                            branch);
                    return;
                }
                if (repositorySettings.getSpec().getCradle().getKeyspace() == null) {
                    logger.error("Keyspace is not specified for schema \"{}\". Synchronization is ignored", branch);
                    return;
                }

                snapshot = Repository.getSnapshot(gitter);
            } finally {
                gitter.unlock();
            }

            Set<RepositoryResource> repositoryResources = snapshot.getResources();
            String fullCommitRef = snapshot.getCommitRef();
            String shortCommitRef = getShortCommitRef(fullCommitRef);

            logger.info("Proceeding with schema \"{}\" [{}]", branch, shortCommitRef);

            var repositoryMap = SchemaUtils.convertToRepositoryMap(repositoryResources);

            // compress Dictionaries
            repositoryMap.get(ResourceType.Th2Dictionary.kind())
                    .values()
                    .forEach(Th2DictionaryProcessor::compressData);

            // add commit reference in annotations to every resource
            stampResources(repositoryMap, fullCommitRef, detectionTime);
            // synchronize entries
            synchronizeNamespace(branch, repositoryMap, repositorySettings, fullCommitRef);

        } catch (Exception e) {
            logger.error("Exception synchronizing schema \"{}\"", branch, e);
        }
    }

    @PostConstruct
    public void start() {
        logger.info("Starting Kubernetes synchronization phase");
        subscribeToRepositoryEvents();
        try {
            config = Config.getInstance();
        } catch (IOException e) {
            logger.error("Error loading config");
            throw new RuntimeException("Failed to start Kubernetes synchronization component");
        }
    }

    private void subscribeToRepositoryEvents() {
        ExecutorService executor = Executors.newCachedThreadPool();
        for (int i = 0; i < SYNC_PARALLELIZATION_THREADS; i++) {
            executor.execute(this::processRepositoryEvents);
        }

        SchemaEventRouter router = SchemaEventRouter.getInstance();
        router.getObservable()
                .onBackpressureBuffer()
                .observeOn(Schedulers.computation())
                .filter(event -> ((event instanceof SynchronizationRequestEvent
                        || (event instanceof RepositoryUpdateEvent && !((RepositoryUpdateEvent) event).isSyncingK8s()))
                ))
                .subscribe(event -> jobQueue.addJob(new K8sSynchronizationJobQueue.Job(event.getSchema())));

        logger.info("Kubernetes synchronization process subscribed to repository events");
    }

    private void processRepositoryEvents() {

        logger.info("Kubernetes synchronization thread started. waiting for synchronization events");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                K8sSynchronizationJobQueue.Job job = jobQueue.takeJob();
                if (job == null) {
                    Thread.sleep(1000);
                    continue;
                }
                synchronizeBranch(job.getSchema(), job.getCreationTime());
                jobQueue.completeJob(job);
            } catch (InterruptedException e) {
                logger.info("Interrupt signal received. Exiting synchronization thread");
                break;
            }
        }
        logger.info("Leaving Kubernetes synchronization thread: interrupt signal received");
    }

    private void stampResources(Map<String, Map<String, RepositoryResource>> repositoryMap,
                                String commitHash,
                                long detectionTime) {
        repositoryMap.values()
                .forEach(repoResources -> repoResources.values()
                        .forEach(resource -> resource.stamp(commitHash, detectionTime))
                );
    }

    private String getShortCommitRef(String commitRef) {
        int length = 8;
        return commitRef.substring(0, length);
    }
}
