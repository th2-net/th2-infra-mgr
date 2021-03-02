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
import com.exactpro.th2.inframgr.docker.DynamicResourceProcessor;
import com.exactpro.th2.inframgr.initializer.SchemaInitializer;
import com.exactpro.th2.inframgr.repository.RepositoryUpdateEvent;
import com.exactpro.th2.inframgr.statuswatcher.ResourcePath;
import com.exactpro.th2.inframgr.util.Th2DictionaryProcessor;
import com.exactpro.th2.inframgr.util.Strings;
import com.exactpro.th2.infrarepo.*;
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

    private void synchronizeNamespace(String schemaName, Map<String, Map<String, RepositoryResource>> repositoryResources) throws Exception {

        try (Kubernetes kube = new Kubernetes(config.getKubernetes(), schemaName)) {

            String namespace = kube.formatNamespaceName(schemaName);
            K8sResourceCache cache = K8sResourceCache.INSTANCE;
            SchemaInitializer.ensureSchema(schemaName, kube);

            // load custom resources from k8s
            Map<String, Map<String, K8sCustomResource>> k8sResources = new HashMap<>();
            for (ResourceType t : ResourceType.values())
                if (t.isK8sResource() && !t.equals(ResourceType.HelmRelease))
                    k8sResources.put(t.kind(), kube.loadCustomResources(t));

            // synchronize by resource type
            for (ResourceType type : ResourceType.values())
                if (type.isK8sResource() && !type.equals(ResourceType.HelmRelease)) {
                    Map<String, RepositoryResource> resources = repositoryResources.get(type.kind());
                    Map<String, K8sCustomResource> customResources = k8sResources.get(type.kind());

                    for (RepositoryResource resource: resources.values()) {
                        String resourceName = resource.getMetadata().getName();
                        String resourceLabel = "\"" + ResourcePath.annotationFor(namespace, type.kind(), resourceName) + "\"";
                        String hashTag = Strings.formatHash(resource.getSourceHash());
                        // add resource to cache
                        cache.add(namespace, resource);
                        //check resources for dynamic image version range
                        DynamicResourceProcessor.checkResource(resource, schemaName);
                        // check repository items against k8s
                        if (!customResources.containsKey(resourceName)) {
                            // create custom resources that do not exist in k8s
                            logger.info("Creating resource {} {}", resourceLabel, hashTag);
                            try {
                                Strings.stringify(resource.getSpec());
                                kube.createCustomResource(resource);
                            } catch (Exception e) {
                                logger.error("Exception creating resource {} {}", resourceLabel, hashTag, e);
                            }
                        } else {
                            // compare object's hashes and update custom resources who's hash labels do not match
                            K8sCustomResource cr = customResources.get(resourceName);

                            if (!(resource.getSourceHash() == null || resource.getSourceHash().equals(cr.getSourceHash()))) {
                                // update custom resource
                                logger.info("Updating resource {} {}", resourceLabel, hashTag);
                                try {
                                    Strings.stringify(resource.getSpec());
                                    kube.replaceCustomResource(resource);
                                } catch (Exception e) {
                                    logger.error("Exception updating resource {} {}", resourceLabel, hashTag, e);
                                }
                            }
                        }
                    }

                    // delete k8s resources that do not exist in repository
                    for (String resourceName : customResources.keySet())
                        if (!resources.containsKey(resourceName)) {
                            String resourceLabel = ResourcePath.annotationFor(namespace, type.kind(), resourceName);
                            try {
                                logger.info("Deleting resource {}", resourceLabel);
                                RepositoryResource resource = new RepositoryResource();
                                resource.setKind(type.kind());
                                resource.setMetadata(new RepositoryResource.Metadata(resourceName));
                                DynamicResourceProcessor.checkResource(resource, schemaName, true);
                                kube.deleteCustomResource(resource);
                            } catch (Exception e) {
                                logger.error("Exception deleting resource {}", resourceLabel, e);
                            }
                        }
            }
        }
    }


    public void synchronizeBranch(String branch) {

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
            RepositorySettings repositorySettings = snapshot.getRepositorySettings();

            if (repositorySettings != null && repositorySettings.isK8sPropagationDenied()) {
                deleteNamespace(branch);
                return;
            }
            if (repositorySettings == null || !repositorySettings.isK8sSynchronizationRequired()) {
                logger.info("Ignoring schema \"{}\" as it is not configured for synchronization", branch);
                return;
            }

            logger.info("Proceeding with schema \"{}\"", branch);

            // convert to map
            Map<String, Map<String, RepositoryResource>> repositoryMap = new HashMap<>();
            for (ResourceType t : ResourceType.values())
                if (t.isK8sResource())
                    repositoryMap.put(t.kind(), new HashMap<>());

            for (RepositoryResource resource : repositoryResources)
                if (ResourceType.forKind(resource.getKind()).isK8sResource()) {
                    if (ResourceType.forKind(resource.getKind()) == ResourceType.Th2Dictionary)
                        Th2DictionaryProcessor.compressData(resource);

                    Map<String, RepositoryResource> typeMap = repositoryMap.get(resource.getKind());
                    typeMap.put(resource.getMetadata().getName(), resource);
                }

            // synchronize entries
            synchronizeNamespace(branch, repositoryMap);

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
            for (String branch : branches.keySet())
                if (!branch.equals("master"))
                    executor.execute(() -> synchronizeBranch(branch));

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
        for (int i = 0; i < SYNC_PARALLELIZATION_THREADS; i++)
            executor.execute(this::processRepositoryEvents);

        SchemaEventRouter router = SchemaEventRouter.getInstance();
        router.getObservable()
                .onBackpressureBuffer()
                .observeOn(Schedulers.computation())
                .filter(event -> (
                                (event instanceof SynchronizationRequestEvent
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

                synchronizeBranch(job.getSchema());
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
}
