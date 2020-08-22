package com.exactpro.th2.schema.schemaeditorbe.repository;

import com.exactpro.th2.schema.schemaeditorbe.Config;
import com.exactpro.th2.schema.schemaeditorbe.SchemaEventRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RepositoryWatcherService {

    private Map<String, String> commitHistory;
    private Config.GitConfig config;
    private SchemaEventRouter eventRouter;
    private Logger logger;

    public RepositoryWatcherService() throws Exception {
        commitHistory = new HashMap<>();
        config = Config.getInstance().getGit();
        eventRouter = SchemaEventRouter.getInstance();
        logger = LoggerFactory.getLogger(RepositoryWatcherService.class);
    }

    @Scheduled(fixedDelay = 30000)
    public void scheduledJob(){

        try {
            Map<String, String> commits = Gitter.getAllBranchesCommits(config);
            commits.forEach((branch, commitRef) -> {

                if (!(commitHistory.isEmpty() || commitHistory.getOrDefault(branch, "").equals(commitRef))) {
                    logger.info("New commit \"{}\" detected for branch \"{}\"", commitRef, branch);

                    RepositoryUpdateEvent event = new RepositoryUpdateEvent(branch, commitRef);
                    if (eventRouter.isEventCached(event))
                        logger.info("Event is recently processed, ignoring");
                    else
                        eventRouter.addEvent(event);
                }
            });

            commitHistory.putAll(commits);
        } catch (Exception e) {
            logger.error("Error fetching repository", e);
        }
    }
}
