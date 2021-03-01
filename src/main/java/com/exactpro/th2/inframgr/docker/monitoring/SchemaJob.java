package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.SchemaEventRouter;
import com.exactpro.th2.inframgr.docker.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.inframgr.docker.util.SpecUtils;
import com.exactpro.th2.inframgr.repository.RepositoryUpdateEvent;
import com.exactpro.th2.inframgr.statuswatcher.ResourcePath;
import com.exactpro.th2.infrarepo.*;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SchemaJob extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(SchemaJob.class);

    private final Collection<DynamicResource> dynamicResources;
    private final List<UpdatedResource> updatedResources;
    private final RegistryConnection connection;
    private final Gitter gitter;
    private final String schema;

    public SchemaJob(Collection<DynamicResource> dynamicResources, RegistryConnection connection, Gitter gitter, String schema) {
        this.dynamicResources = dynamicResources;
        this.updatedResources = new ArrayList<>();
        this.connection = connection;
        this.gitter = gitter;
        this.schema = schema;
    }

    @Override
    public void start() {
        logger.info("Checking for new versions for resources in schema: \"{}\"", schema);
        for (DynamicResource resource : dynamicResources) {
            TagUpdater.getLatestTags(resource, updatedResources, connection);
        }
        try {
            commitAndPush();
        } catch (Exception e) {
            logger.info("Exception while pushing to branch: \"{}\".", schema);
        }
    }

    private void commitAndPush() throws IOException, GitAPIException {
        try {
            gitter.lock();
            var snapshot = Repository.getSnapshot(gitter);
            updateRepository(snapshot);
            String commitRef = gitter.commitAndPush("Updated image versions");
            if (commitRef != null) {
                logger.info("Successfully pushed to branch: \"{}\" commitRef: \"{}\"", schema, commitRef);
                SchemaEventRouter router = SchemaEventRouter.getInstance();
                RepositoryUpdateEvent event = new RepositoryUpdateEvent(schema, commitRef);
                router.addEvent(event);
            } else {
                logger.info("All files up to date for branch: \"{}\"", schema);
            }
        } finally {
            gitter.unlock();
        }
    }

    private void updateRepository(RepositorySnapshot snapshot) {
        Set<RepositoryResource> repositoryResources = snapshot.getResources();
        Map<String, RepositoryResource> resourceSpecMap = repositoryResources.stream()
                .collect(Collectors.toMap(
                        x -> x.getMetadata().getName(),
                        Function.identity())
                );

        for (UpdatedResource updatedResource : updatedResources) {
            var repositoryResource = resourceSpecMap.get(updatedResource.getName());
            if (repositoryResource != null) {
                var spec = repositoryResource.getSpec();
                String resourceName = repositoryResource.getMetadata().getName();
                String latestVersion = updatedResource.getLatestVersion();
                String currentVersion = SpecUtils.getImageVersion(spec);
                String resourceLabel = ResourcePath.annotationFor(schema, repositoryResource.getKind(), resourceName);

                if (latestVersion.equals(currentVersion)) {
                    //TODO remove log after testing
                    logger.info("Couldn't find new version for resource: \"{}\"", resourceLabel);
                    continue;
                }
                logger.info("Found new version for resource: \"{}\"", resourceLabel);
                SpecUtils.changeImageVersion(spec, latestVersion);
                try {
                    Repository.update(gitter, repositoryResource);
                    logger.info("Successfully updated repository with: \"{}\"", resourceLabel);
                } catch (Exception e) {
                    logger.info("Exception while updating repository with : \"{}\"", resourceLabel);
                }
            }
        }
    }

    static class UpdatedResource {
        private final String name;
        private final String latestVersion;

        public UpdatedResource(String name, String latestVersion) {
            this.name = name;
            this.latestVersion = latestVersion;
        }

        public String getName() {
            return name;
        }

        public String getLatestVersion() {
            return latestVersion;
        }
    }
}
