package com.exactpro.th2.schema.schemaeditorbe.repository;

import com.exactpro.th2.schema.schemaeditorbe.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class WatcherService {

    private Map<String, String> commitHistory;
    private Config.GitConfig config;
    private RepositoryEventRouter eventRouter;
    private Logger logger;

    public WatcherService() throws Exception {
        commitHistory = new HashMap<>();
        config = Config.getInstance().getGit();
        eventRouter = RepositoryEventRouter.getInstance();
        logger = LoggerFactory.getLogger(Config.class);
    }


    @Scheduled(fixedDelay = 10000)
    public void scheduledJob(){

        try {
            Map<String, String> commits = Gitter.getAllBranchesCommits(config);
            if (commitHistory.isEmpty()) {
                commitHistory.putAll(commits);
                return;
            }

            commits.forEach((branch, commitRef) -> {

                if (!commitHistory.isEmpty() && !commitHistory.getOrDefault(branch, "").equals(commitRef)) {
                    logger.info("new commit [{}] detected for branch \"{}\"", commitRef, branch);
                    commitHistory.put(branch, commitRef);
                    eventRouter.addEvent(new RepositoryEvent(branch, commitRef));
                }
            });

        } catch (Exception e) {
            logger.error("error fetching git repository", e);
        }

    }

}
