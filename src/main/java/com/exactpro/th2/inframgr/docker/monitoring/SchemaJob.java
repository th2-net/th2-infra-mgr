package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.docker.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.infrarepo.Gitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SchemaJob extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(SchemaJob.class);

    private List<TagUpdater> tagUpdateJobs = new ArrayList<>();
    private Gitter gitter;
    private RegistryConnection connection;

    public SchemaJob(Collection<DynamicResource> dynamicResources, Gitter gitter, RegistryConnection connection) {
        this.gitter = gitter;
        this.connection = connection;
        createJobs(dynamicResources);
    }

    @Override
    public void start() {
        logger.info("Checking for new versions for resources in schema: \"{}\"", gitter.getBranch());
        for (TagUpdater tagUpdater : tagUpdateJobs) {
            try {
                tagUpdater.updateTagAndCommit();
            } catch (IOException e) {
                logger.error("Exception while updating repository", e);
            }
        }
        try {
            gitter.lock();
            try {
                String commitRef = gitter.commitAndPush("Updated image versions");
                logger.info("Successfully updated branch: \"{}\" commitRef: \"{}\"", gitter.getBranch(), commitRef);
            } catch (Exception e) {
                logger.info("Exception while pushing to branch: \"{}\".", gitter.getBranch());
            }
        } finally {
            gitter.unlock();
        }
    }

    private void createJobs(Collection<DynamicResource> dynamicResources) {
        for (DynamicResource resource : dynamicResources) {
            tagUpdateJobs.add(new TagUpdater(resource, gitter, connection));
        }
    }
}
