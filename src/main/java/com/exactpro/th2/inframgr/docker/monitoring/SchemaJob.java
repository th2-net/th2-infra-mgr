package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.docker.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.infrarepo.Gitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public class SchemaJob extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(SchemaJob.class);

    private Collection<DynamicResource> dynamicResources;
    private Gitter gitter;
    private RegistryConnection connection;

    public SchemaJob(Collection<DynamicResource> dynamicResources, Gitter gitter, RegistryConnection connection) {
        this.dynamicResources = dynamicResources;
        this.gitter = gitter;
        this.connection = connection;
    }

    @Override
    public void start() {
        logger.info("Checking for new versions for resources in schema: \"{}\"", gitter.getBranch());
        for (DynamicResource resource : dynamicResources) {
            try {
                TagUpdater.updateTag(resource, gitter, connection);
            } catch (Exception e) {
                logger.error("Exception while updating repository", e);
            }
        }
        try {
            gitter.lock();
            try {
                String commitRef = gitter.commitAndPush("Updated image versions");
                if (commitRef != null) {
                    logger.info("Successfully pushed to branch: \"{}\" commitRef: \"{}\"", gitter.getBranch(), commitRef);
                }else {
                    logger.info("All files up to date for branch: \"{}\"", gitter.getBranch());
                }
            } catch (Exception e) {
                logger.info("Exception while pushing to branch: \"{}\".", gitter.getBranch());
            }
        } finally {
            gitter.unlock();
        }
    }
}
