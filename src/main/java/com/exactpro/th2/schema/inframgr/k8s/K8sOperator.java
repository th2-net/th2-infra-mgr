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
import com.exactpro.th2.schema.inframgr.models.*;
import com.exactpro.th2.schema.inframgr.repository.Gitter;
import com.exactpro.th2.schema.inframgr.repository.Repository;
import com.exactpro.th2.schema.inframgr.util.Stringifier;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;

@Component
public class K8sOperator {

    private static final Logger logger = LoggerFactory.getLogger(K8sOperator.class);
    private Config config;
    private K8sResourceCache cache;


    private void startWatchers() {

        // wait for startup synchronization to complete
        logger.info("Operator is waiting for startup Kubernetes synchronization to complete");
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
            kube.registerWatchers(new Watcher<K8sCustomResource>() {
                @Override
                public void eventReceived(Action action, K8sCustomResource res) {
                    processEvent(action, res, kube);
                }

                @Override
                public void onClose(KubernetesClientException cause) {
                    logger.error("exception watching resources", cause);

                }
            });
        } catch (Exception e) {
            logger.error("Exception registering watchers. exiting", e);
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
            Map<String, String> o = (Map<String, String>) res.getStatus();

            String namespace = meta.getNamespace();
            String name = meta.getName();
            String kind = res.getKind();
            String hash = res.getSourceHashLabel();

            Lock lock = cache.lockFor(namespace, kind, name);
            try {
                lock.lock();

                // do prelimenary check against the cache to avoid repository downloading
                K8sResourceCache.CacheEntry cacheEntry = cache.get(namespace, kind, name);
                String cachedHash = cacheEntry == null ? null : cacheEntry.getHash();
                if (action.equals(Watcher.Action.DELETED) && cacheEntry != null && cacheEntry.isMarkedAsDeleted()
                        && Objects.equals(cachedHash, hash))

                    return;

                if (!action.equals(Watcher.Action.DELETED) && cacheEntry != null && !cacheEntry.isMarkedAsDeleted()
                        && Objects.equals(cachedHash, hash))

                    return;

                // action is needed as optimistic check did not draw enough conclusions
                Gitter gitter = Gitter.getBranch(config.getGit(), kube.extractSchemaName(namespace));
                logger.info("Need to checkout branch {} from repository", gitter.getBranch()) ;

                ResourceEntry resourceEntry = null;
                try {
                    gitter.lock();
                    gitter.checkout();
                    RepositorySnapshot snapshot = Repository.getSnapshot(gitter);

                    // check if we need to re-synchronize k8s at all
                    RepositorySettings rs = snapshot.getRepositorySettings();
                    if (!(rs.isK8sPropagationEnabled() && rs.isK8sGovernanceEnabled()))
                        return;

                    // refresh cache for this namespace
                    for (ResourceEntry e :snapshot.getResources()) {
                        cache.add(namespace, e);
                        if (e.getKind().kind().equals(kind) && e.getName().equals(name))
                            resourceEntry = e;
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

                        resourceEntry = new ResourceEntry();
                        resourceEntry.setKind(ResourceType.forKind(kind));
                        resourceEntry.setName(name);
                    }
                }

                Stringifier.stringify(resourceEntry.getSpec());
                RepositoryResource resource = new RepositoryResource(resourceEntry);
                if (actionReplace) {
                    logger.info("Detected external manipulation on {}.{}.{}, recreating resource"
                            , namespace, kind, name) ;
                    kube.createOrReplaceCustomResource(resource, namespace);
                } else
                if (actionDelete) {
                    logger.info("Detected external manipulation on {}.{}.{}, deleting resource"
                            , namespace, kind, name) ;
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

        // start repository event listener thread
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> startWatchers());
    }
}
