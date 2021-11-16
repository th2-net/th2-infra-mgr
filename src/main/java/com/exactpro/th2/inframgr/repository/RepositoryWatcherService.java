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
import com.exactpro.th2.inframgr.util.cfg.GitCfg;
import com.exactpro.th2.infrarepo.GitterContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RepositoryWatcherService {

    private Map<String, String> commitHistory;

    private GitCfg config;

    private SchemaEventRouter eventRouter;

    private Logger logger;

    public RepositoryWatcherService() throws Exception {
        commitHistory = new HashMap<>();
        config = Config.getInstance().getGit();
        eventRouter = SchemaEventRouter.getInstance();
        logger = LoggerFactory.getLogger(RepositoryWatcherService.class);
    }

    @Scheduled(fixedDelay = 30000)
    public void scheduledJob() {

        try {
            GitterContext ctx = GitterContext.getContext(config);
            Map<String, String> commits = ctx.getAllBranchesCommits();
            commits.forEach((branch, commitRef) -> {

                if (!(branch.equals("master")
                        || commitHistory.isEmpty()
                        || commitHistory.getOrDefault(branch, "").equals(commitRef))) {
                    logger.info("New commit \"{}\" detected for branch \"{}\"", commitRef, branch);

                    RepositoryUpdateEvent event = new RepositoryUpdateEvent(branch, commitRef);
                    boolean sent = eventRouter.addEventIfNotCached(event);
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
}
