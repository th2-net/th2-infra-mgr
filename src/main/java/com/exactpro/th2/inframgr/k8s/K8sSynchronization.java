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
import com.exactpro.th2.inframgr.UrlPathConflicts;
import com.exactpro.th2.inframgr.docker.monitoring.DynamicResourceProcessor;
import com.exactpro.th2.inframgr.initializer.LoggingConfigMap;
import com.exactpro.th2.inframgr.initializer.Th2BoxConfigurations;
import com.exactpro.th2.inframgr.initializer.SchemaInitializer;
import com.exactpro.th2.inframgr.metrics.ManagerMetrics;
import com.exactpro.th2.inframgr.repository.RepositoryUpdateEvent;
import com.exactpro.th2.inframgr.util.Strings;
import com.exactpro.th2.inframgr.util.Th2DictionaryProcessor;
import com.exactpro.th2.inframgr.validator.SchemaValidator;
import com.exactpro.th2.inframgr.validator.cache.ValidationCache;
import com.exactpro.th2.infrarepo.*;
import io.prometheus.client.Histogram;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import rx.schedulers.Schedulers;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.exactpro.th2.inframgr.statuswatcher.ResourcePath.annotationFor;

@Component
public class K8sSynchronization {

    private static final int SYNC_PARALLELIZATION_THREADS = 3;

    private static final Logger logger = LoggerFactory.getLogger(K8sSynchronization.class);

    private Config config;

    private static volatile boolean startupSynchronizationComplete;

    private final K8sSynchronizationJobQueue jobQueue = new K8sSynchronizationJobQueue();

    private void deleteNamespace(String schemaName) {
        try (Kubernetes kube = new Kubernetes(config.getKubernetes(), schemaName)) {
            logger.info("Removing schema \"{}\" from kubernetes", schemaName);
            kube.deleteNamespace();
        } catch (Exception e) {
            logger.error("Exception removing schema \"{}\" from kubernetes", schemaName, e);
        }
    }

    private void synchronizeNamespace(String schemaName,
                                      Map<String, Map<String, RepositoryResource>> repositoryResources,
                                      RepositorySettings repositorySettings,
                                      String fullCommitRef) throws Exception {

        Histogram.Timer timer = ManagerMetrics.getCommitTimer();
        String shortCommitRef = getShortCommitRef(fullCommitRef);
        try (Kubernetes kube = new Kubernetes(config.getKubernetes(), schemaName)) {

            SchemaInitializer.ensureSchema(schemaName, kube);
            validateSchema(schemaName, repositoryResources, shortCommitRef);

            try {
                LoggingConfigMap.copyLoggingConfigMap(
                        repositorySettings.getLogLevelRoot(),
                        repositorySettings.getLogLevelTh2(),
                        fullCommitRef,
                        kube
                );
            } catch (Exception e) {
                logger.error("Exception copying logging config map to schema \"{}\"", schemaName, e);
            }

            Th2BoxConfigurations.synchronizeBoxConfigMaps(
                    repositorySettings.getMqRouter(),
                    repositorySettings.getGrpcRouter(),
                    repositorySettings.getCradleManager(),
                    fullCommitRef,
                    kube
            );

            syncByResourceType(schemaName, repositoryResources, kube, shortCommitRef);

        } finally {
            timer.observeDuration();
        }
    }

    private void syncByResourceType(String schemaName,
                                    Map<String, Map<String, RepositoryResource>> repositoryResources,
                                    Kubernetes kube, String shortCommitRef) {
        String namespace = kube.formatNamespaceName(schemaName);
        K8sResourceCache cache = K8sResourceCache.INSTANCE;
        // load custom resources from k8s
        Map<String, Map<String, K8sCustomResource>> k8sResources = loadCustomResources(kube);
        for (ResourceType type : ResourceType.values()) {
            if (type.isK8sResource() && !type.equals(ResourceType.HelmRelease)) {
                var typeKind = type.kind();
                Map<String, RepositoryResource> resources = repositoryResources.get(typeKind);
                Map<String, K8sCustomResource> customResources = k8sResources.get(typeKind);

                for (RepositoryResource resource : resources.values()) {
                    // add resource to cache
                    cache.add(namespace, resource);
                    //check resources for dynamic image version range
                    DynamicResourceProcessor.checkResource(resource, schemaName);

                    syncRepoResourceWithK8s(resource, namespace, typeKind,
                            customResources, kube, shortCommitRef);
                }

                deleteK8sResourcesAbsentInRepo(schemaName, typeKind, resources,
                        customResources, kube, shortCommitRef);
            }
        }
    }

    private void syncRepoResourceWithK8s(RepositoryResource resource, String namespace, String typeKind,
                                         Map<String, K8sCustomResource> customResources,
                                         Kubernetes kube, String shortCommitRef) {
        String resourceName = resource.getMetadata().getName();
        String resourceLabel = "\"" + annotationFor(namespace, typeKind, resourceName) + "\"";
        String hashTag = Strings.formatHash(resource.getSourceHash());
        // check repository items against k8s
        if (!customResources.containsKey(resourceName)) {
            // create custom resources that do not exist in k8s
            logger.info("Creating resource {} {}. [commit: {}]",
                    resourceLabel, hashTag, shortCommitRef);
            try {
                Strings.stringify(resource.getSpec());
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
                    Strings.stringify(resource.getSpec());
                    kube.replaceCustomResource(resource);
                } catch (Exception e) {
                    logger.error("Exception updating resource {} {}. [commit: {}]",
                            resourceLabel, hashTag, shortCommitRef, e);
                }
            }
        }
    }


    private void deleteK8sResourcesAbsentInRepo(String schemaName, String typeKind,
                                             Map<String, RepositoryResource> resources, Map<String, K8sCustomResource> customResources,
                                             Kubernetes kube, String shortCommitRef) {
        for (String resourceName : customResources.keySet()) {
            if (!resources.containsKey(resourceName)) {
                String namespace = kube.formatNamespaceName(schemaName);
                String resourceLabel = annotationFor(namespace, typeKind, resourceName);
                try {
                    logger.info("Deleting resource {}. [commit: {}]", resourceLabel, shortCommitRef);
                    RepositoryResource resource = new RepositoryResource();
                    resource.setKind(typeKind);
                    resource.setMetadata(new RepositoryResource.Metadata(resourceName));
                    DynamicResourceProcessor.checkResource(resource, schemaName, true);
                    kube.deleteCustomResource(resource);
                } catch (Exception e) {
                    logger.error("Exception deleting resource {}. [commit: {}]",
                            resourceLabel, shortCommitRef, e);
                }
            }
        }
    }

    private Map<String, Map<String, K8sCustomResource>> loadCustomResources(Kubernetes kube) {
        Map<String, Map<String, K8sCustomResource>> k8sResources = new HashMap<>();
        for (ResourceType t : ResourceType.values()) {
            if (t.isK8sResource() && !t.equals(ResourceType.HelmRelease)) {
                k8sResources.put(t.kind(), kube.loadCustomResources(t)); // [kind, [name, K8sCustomResource]]
            }
        }
        return k8sResources;
    }

    // validate schema links, remove invalid ones
    // validate secret custom config
    private void validateSchema(String schemaName, Map<String, Map<String, RepositoryResource>> repoResources, String shortCommitRef) {
        if (!SchemaValidator.validate(schemaName, repoResources, shortCommitRef)) {
            logger.warn("Schema \"{}\" contains errors. Invalid links will not be applied to cluster.",
                    schemaName);
            ValidationCache.getSchemaTable(schemaName).printErrors();
        } else {
            logger.info("Schema \"{}\" validated.", schemaName);
        }
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
            try {
                gitter.lock();
                snapshot = Repository.getSnapshot(gitter);
            } finally {
                gitter.unlock();
            }

            Set<RepositoryResource> repositoryResources = snapshot.getResources();
            repositoryResources = UrlPathConflicts.detectUrlPathsConflicts(repositoryResources, branch);
            RepositorySettings repositorySettings = snapshot.getRepositorySettings();
            String fullCommitRef = snapshot.getCommitRef();
            String shortCommitRef = getShortCommitRef(fullCommitRef);

            if (repositorySettings != null && repositorySettings.isK8sPropagationDenied()) {
                DynamicResourceProcessor.schemaDeleted(branch);
                deleteNamespace(branch);
                return;
            }
            if (repositorySettings == null || !repositorySettings.isK8sSynchronizationRequired()) {
                logger.info("Ignoring schema \"{}\" as it is not configured for synchronization [{}]",
                        branch, shortCommitRef);
                return;
            }

            logger.info("Proceeding with schema \"{}\" [{}]", branch, shortCommitRef);

            // convert to map
            Map<String, Map<String, RepositoryResource>> repositoryMap = new HashMap<>();
            for (ResourceType t : ResourceType.values()) {
                if (t.isK8sResource()) {
                    repositoryMap.put(t.kind(), new HashMap<>());
                }
            }

            for (RepositoryResource resource : repositoryResources) {
                if (ResourceType.forKind(resource.getKind()).isK8sResource()) {
                    if (ResourceType.forKind(resource.getKind()) == ResourceType.Th2Dictionary) {
                        Th2DictionaryProcessor.compressData(resource);
                    }

                    Map<String, RepositoryResource> typeMap = repositoryMap.get(resource.getKind());
                    typeMap.put(resource.getMetadata().getName(), resource);
                }
            }

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

        try {
            config = Config.getInstance();
            GitterContext ctx = GitterContext.getContext(config.getGit());
            Map<String, String> branches = ctx.getAllBranchesCommits();

            ExecutorService executor = Executors.newFixedThreadPool(SYNC_PARALLELIZATION_THREADS);
            for (String branch : branches.keySet()) {
                if (!branch.equals("master")) {
                    executor.execute(() -> synchronizeBranch(branch, System.currentTimeMillis()));
                }
            }

            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }

        } catch (Exception e) {
            logger.error("Exception fetching branch list from repository", e);
            //throw new RuntimeException("Kubernetes synchronization failed");
        }

        startupSynchronizationComplete = true;
        logger.info("Kubernetes synchronization phase complete");

        subscribeToRepositoryEvents();
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

    public static boolean isStartupSynchronizationComplete() {
        return startupSynchronizationComplete;
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
