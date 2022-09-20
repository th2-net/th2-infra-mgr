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

package com.exactpro.th2.inframgr.repository;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.SchemaEventRouter;
import com.exactpro.th2.inframgr.docker.monitoring.DynamicResourceProcessor;
import com.exactpro.th2.inframgr.k8s.K8sResourceCache;
import com.exactpro.th2.inframgr.util.cfg.GitCfg;
import com.exactpro.th2.infrarepo.git.GitterContext;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RepositoryWatcherService {

    private final Map<String, String> commitHistory;

    private final GitCfg config;

    private final SchemaEventRouter eventRouter;

    private final Logger logger;

    private final KubernetesClient kubeClient = new KubernetesClientBuilder().build();

    private int prevBranchCount;

    private final String namespacePrefix;

    private static volatile boolean startupSynchronizationComplete;

    public RepositoryWatcherService() throws Exception {
        var fullConfig = Config.getInstance();
        commitHistory = new HashMap<>();
        config = fullConfig.getGit();
        namespacePrefix = fullConfig.getKubernetes().getNamespacePrefix();
        eventRouter = SchemaEventRouter.getInstance();
        logger = LoggerFactory.getLogger(RepositoryWatcherService.class);
    }

    @Scheduled(fixedDelayString = "${GIT_FETCH_INTERVAL:14000}")
    private void scheduledJob() {
        try {
            logger.debug("fetching changes from git");
            GitterContext ctx = GitterContext.getContext(config);
            Map<String, String> commits = ctx.getAllBranchesCommits();
            if (prevBranchCount > commits.size()) {
                removeExtinctedNamespaces(commits.keySet());
            }
            prevBranchCount = commits.size();
            if (commitHistory.isEmpty()) {
                doInitialSynchronization(commits);
            }
            commits.forEach((branch, commitRef) -> {

                if (!(branch.equals("master")
                        || commitHistory.isEmpty()
                        || commitHistory.getOrDefault(branch, "").equals(commitRef))) {
                    logger.info("New commit \"{}\" detected for branch \"{}\"", commitRef, branch);

                    RepositoryUpdateEvent event = new RepositoryUpdateEvent(branch, commitRef);
                    boolean sent = eventRouter.addEventIfNotCached(branch, event);
                    if (!sent) {
                        logger.info("Event is recently processed, ignoring");
                    }
                }
            });

            commitHistory.putAll(commits);
        } catch (Exception e) {
            logger.error("Error fetching repository", e);
        }
    }

    private void doInitialSynchronization(Map<String, String> commits) {
        logger.info("Starting Kubernetes synchronization phase");
        commits.forEach((branch, commitRef) -> {
            if (!branch.equals("master")) {
                RepositoryUpdateEvent event = new RepositoryUpdateEvent(branch, commitRef);
                boolean sent = eventRouter.addEventIfNotCached(branch, event);
                if (!sent) {
                    logger.info("Event is recently processed, ignoring");
                }
            }
        });
        startupSynchronizationComplete = true;
        logger.info("Kubernetes synchronization phase complete");
    }

    private void removeExtinctedNamespaces(Set<String> existingBranches) {
        List<String> extinctNamespaces = kubeClient.namespaces()
                .list()
                .getItems()
                .stream()
                .map(item -> item.getMetadata().getName())
                .filter(namespace -> namespace.startsWith(namespacePrefix)
                        && !existingBranches.contains(namespace.substring(namespacePrefix.length())))
                .collect(Collectors.toList());

        for (String extinctNamespace : extinctNamespaces) {
            String schemaName = extinctNamespace.substring(namespacePrefix.length());
            DynamicResourceProcessor.deleteSchema(schemaName);
            K8sResourceCache.INSTANCE.removeNamespace(extinctNamespace);
            Resource<Namespace> namespaceResource = kubeClient.namespaces().withName(extinctNamespace);
            if (namespaceResource != null) {
                String branchName = extinctNamespace.substring(namespacePrefix.length());
                eventRouter.removeEventsForSchema(branchName);
                logger.info("branch \"{}\" was removed from remote repository, deleting corresponding namespace \"{}\"",
                        branchName, existingBranches);
                namespaceResource.delete();
                commitHistory.remove(branchName);
            }
        }
    }

    public static boolean isStartupSynchronizationComplete() {
        return startupSynchronizationComplete;
    }
}
