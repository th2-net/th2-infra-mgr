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

package com.exactpro.th2.inframgr.k8s;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.repo.*;
import com.exactpro.th2.inframgr.statuswatcher.ResourcePath;
import com.exactpro.th2.inframgr.util.RetryableTaskQueue;
import com.exactpro.th2.inframgr.util.Stringifier;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;

@Component
public class K8sOperator {

    private static final Logger logger = LoggerFactory.getLogger(K8sOperator.class);
    private static final int RECOVERY_THREAD_POOL_SIZE = 3;

    private Config config;
    private K8sResourceCache cache;
    private RetryableTaskQueue taskQueue;


    private void startWatchers() {

        // wait for startup synchronization to complete
        logger.info("Operator is waiting for kubernetes startup  synchronization to complete");
        while (!(Thread.currentThread().isInterrupted() || K8sSynchronization.isStartupSynchronizationComplete())) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.info("Interrupt signal received. Exiting operator thread");
                return;
            }
        }

        logger.info("Starting Kubernetes watchers");
        cache = K8sResourceCache.INSTANCE;

        try {
            Kubernetes kube = new Kubernetes(config.getKubernetes(), null);
            kube.registerWatchers(new Kubernetes.ExtendedWatcher<K8sCustomResource>() {
                @Override
                public void eventReceived(Action action, K8sCustomResource res) {
                    processEvent(action, res, kube);
                }

                @Override
                public void onClose(KubernetesClientException cause) {
                    logger.error("Exception watching resources", cause);
                }
                @Override
                public void onRecover() {
                    logger.info("Watcher recovered");
                }
            });
        } catch (Exception e) {
            logger.error("Exception registering watchers. Exiting", e);
            return;
        }

        // enter the loop
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.info("Interrupt signal received. Exiting operator thread");
                break;
            }
        }
    }


    private void processEvent(Watcher.Action action, K8sCustomResource res, Kubernetes kube) {

        try {
            ObjectMeta meta = res.getMetadata();

            String namespace = meta.getNamespace();
            String name = meta.getName();
            String kind = res.getKind();
            String hash = res.getSourceHash();

            String resourceLabel = "\"" + ResourcePath.annotationFor(namespace, kind, name) + "\"";
            logger.debug("Received {} event on resource {}", action.name(), resourceLabel);

            Lock lock = cache.lockFor(namespace, kind, name);
            try {
                lock.lock();

                // do preliminary check against the cache to avoid repository downloading
                K8sResourceCache.CacheEntry cacheEntry = cache.get(namespace, kind, name);
                String cachedHash = cacheEntry == null ? null : cacheEntry.getHash();
                if (action.equals(Watcher.Action.DELETED) && cacheEntry != null && cacheEntry.isMarkedAsDeleted()
                        && Objects.equals(cachedHash, hash)) {
                    logger.debug("No action needed for resource {}", resourceLabel);
                    return;
                }

                if (!action.equals(Watcher.Action.DELETED) && cacheEntry != null && !cacheEntry.isMarkedAsDeleted()
                        && Objects.equals(cachedHash, hash)) {

                    logger.debug("No action needed for resource {}", resourceLabel);
                    return;
                }


                // action is needed as optimistic check did not draw enough conclusions
                Gitter gitter = Gitter.getBranch(config.getGit(), kube.extractSchemaName(namespace));
                logger.info("Checking out branch \"{}\" from repository", gitter.getBranch()) ;

                RepositoryResource resource = null;
                try {
                    gitter.lock();
                    RepositorySnapshot snapshot = Repository.getSnapshot(gitter);

                    // check if we need to re-synchronize k8s at all
                    RepositorySettings rs = snapshot.getRepositorySettings();
                    if (rs == null || !rs.isK8sGovernanceRequired())
                        return;

                    // refresh cache for this namespace
                    for (RepositoryResource r :snapshot.getResources()) {
                        cache.add(namespace, r);
                        if (r.getKind().equals(kind) && r.getMetadata().getName().equals(name))
                            resource = r;
                    }

                } finally {
                    gitter.unlock();
                }


                // recheck item
                cacheEntry = cache.get(namespace, kind, name);
                cachedHash = cacheEntry == null ? null : cacheEntry.getHash();

                boolean actionReplace = false, actionDelete = false;
                if (action.equals(Watcher.Action.DELETED)) {
                    if (cacheEntry == null || cacheEntry.isMarkedAsDeleted())
                        return; // no action is required as item does not exist in repository
                    actionReplace = true;
                } else {
                    if (cacheEntry != null && !cacheEntry.isMarkedAsDeleted() && Objects.equals(cachedHash, hash))
                        return; // no action is needed as item's hash matches

                    if (cachedHash != null)
                        actionReplace = true;
                    else {
                        actionDelete = true;

                        resource = new RepositoryResource();
                        resource.setKind(kind);
                        resource.setMetadata(new RepositoryResource.Metadata(name));
                    }
                }

                Stringifier.stringify(resource.getSpec());
                if (actionReplace) {
                    logger.info("Detected external manipulation on {}, recreating resource", resourceLabel) ;

                    // check current status of namespace
                    Namespace n = kube.getNamespace(namespace);
                    if (n == null || !n.getStatus().getPhase().equals(Kubernetes.PHASE_ACTIVE)) {
                        logger.warn("Cannot recreate resource {} as namespace is in \"{}\" state. Scheduled full schema synchronization"
                                , resourceLabel, (n == null ? "Deleted" : n.getStatus().getPhase()));
                        taskQueue.add(new SchemaRecoveryTask(kube.extractSchemaName(namespace)), true);
                    } else
                        kube.createOrReplaceCustomResource(resource, namespace);
                } else
                if (actionDelete) {
                    logger.info("Detected external manipulation on {}, deleting resource", resourceLabel) ;
                    kube.deleteCustomResource(resource, namespace);
                }

            } finally {
                lock.unlock();
            }

        } catch (Exception e) {
            logger.error("exception processing event", e);
        }
    }


    @PostConstruct
    public void start() throws IOException {

        config = Config.getInstance();
        taskQueue = new RetryableTaskQueue(RECOVERY_THREAD_POOL_SIZE);

        // start repository event listener thread
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(this::startWatchers);
    }

    @PreDestroy
    public void destroy() {
        logger.info("Shutting down retryable task scheduler");
        taskQueue.shutdown();
    }

}
