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

package com.exactpro.th2.inframgr.repository;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.SchemaEventRouter;
import com.exactpro.th2.inframgr.docker.monitoring.DynamicResourceProcessor;
import com.exactpro.th2.inframgr.k8s.K8sResourceCache;
import com.exactpro.th2.infrarepo.git.GitterContext;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.exactpro.th2.inframgr.SchemaController.SOURCE_BRANCH;

@Component
public class RepositoryWatcherService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryWatcherService.class);

    private static volatile boolean startupSynchronizationComplete;

    private final Map<String, String> commitHistory;

    private final SchemaEventRouter eventRouter;

    private final KubernetesClient kubeClient = new KubernetesClientBuilder().build();

    private Set<String> prevBranches = Collections.emptySet();

    @Autowired
    private Config config;

    public RepositoryWatcherService() {
        commitHistory = new HashMap<>();
        eventRouter = SchemaEventRouter.getInstance();
    }

    @Scheduled(fixedDelayString = "${GIT_FETCH_INTERVAL:14000}")
    private void scheduledJob() {
        try {
            LOGGER.debug("fetching changes from git");
            GitterContext ctx = GitterContext.getContext(config.getGit());
            Map<String, String> commits = ctx.getAllBranchesCommits();
            if (!prevBranches.equals(commits.keySet())) {
                LOGGER.info("Fetched branches: {}, previous branches: {}", commits.keySet(), prevBranches);
                removeExtinctedNamespaces(commits.keySet());
            } else {
                notifyAboutExtinctedNamespaces(commits.keySet());
            }
            prevBranches = commits.keySet();
            if (commitHistory.isEmpty()) {
                doInitialSynchronization(commits);
            }
            commits.forEach((branch, commitRef) -> {

                if (!(SOURCE_BRANCH.equals(branch)
                        || commitHistory.isEmpty()
                        || commitHistory.getOrDefault(branch, "").equals(commitRef))) {
                    LOGGER.info("New commit \"{}\" detected for branch \"{}\"", commitRef, branch);

                    RepositoryUpdateEvent event = new RepositoryUpdateEvent(branch, commitRef);
                    boolean sent = eventRouter.addEventIfNotCached(branch, event);
                    if (!sent) {
                        LOGGER.info("Event is recently processed, ignoring");
                    }
                }
            });

            commitHistory.putAll(commits);
        } catch (Exception e) {
            LOGGER.error("Error fetching repository", e);
        }
    }

    private void doInitialSynchronization(Map<String, String> commits) {
        LOGGER.info("Starting Kubernetes synchronization phase");
        commits.forEach((branch, commitRef) -> {
            if (!SOURCE_BRANCH.equals(branch)) {
                RepositoryUpdateEvent event = new RepositoryUpdateEvent(branch, commitRef);
                boolean sent = eventRouter.addEventIfNotCached(branch, event);
                if (!sent) {
                    LOGGER.info("Event is recently processed, ignoring");
                }
            }
        });
        startupSynchronizationComplete = true;
        LOGGER.info("Kubernetes synchronization phase complete");
    }

    private void removeExtinctedNamespaces(Set<String> existingBranches) {
        List<String> extinctNamespaces = getExtinctNamespaces(existingBranches);

        for (String extinctNamespace : extinctNamespaces) {
            String schemaName = extinctNamespace.substring(config.getKubernetes().getNamespacePrefix().length());
            DynamicResourceProcessor.deleteSchema(schemaName);
            K8sResourceCache.INSTANCE.removeNamespace(extinctNamespace);
            Resource<Namespace> namespaceResource = kubeClient.namespaces().withName(extinctNamespace);
            if (namespaceResource != null) {
                eventRouter.removeEventsForSchema(schemaName);
                if (config.getBehaviour().isPermittedToRemoveNamespace()) {
                    LOGGER.info(
                            "branch \"{}\" was removed from remote repository, deleting corresponding namespace \"{}\"",
                            schemaName,
                            extinctNamespace
                    );
                    namespaceResource.delete();
                } else {
                    LOGGER.warn(
                            "branch \"{}\" was removed from remote repository, stopping namespace \"{}\" maintenance",
                            schemaName,
                            extinctNamespace
                    );
                }
                commitHistory.remove(schemaName);
            }
        }
    }

    private void notifyAboutExtinctedNamespaces(Set<String> existingBranches) {
        if (!config.getBehaviour().isPermittedToRemoveNamespace() && LOGGER.isWarnEnabled()) {
            List<String> extinctNamespaces = getExtinctNamespaces(existingBranches);
            if (!extinctNamespaces.isEmpty()) {
                LOGGER.warn(
                        "Maintenance for namespaces \"{}\" are stopped, existed branches: \"{}\"",
                        extinctNamespaces,
                        existingBranches
                );
            }
        }
    }

    @NotNull
    private List<String> getExtinctNamespaces(Set<String> existingBranches) {
        String namespacePrefix = config.getKubernetes().getNamespacePrefix();
        return kubeClient.namespaces()
                .list()
                .getItems()
                .stream()
                .map(item -> item.getMetadata().getName())
                .filter(namespace -> namespace.startsWith(namespacePrefix)
                        && !existingBranches.contains(namespace.substring(namespacePrefix.length())))
                .toList();
    }

    public static boolean isStartupSynchronizationComplete() {
        return startupSynchronizationComplete;
    }
}
