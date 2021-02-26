package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.docker.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.inframgr.docker.util.SpecUtils;
import com.exactpro.th2.infrarepo.Gitter;
import com.exactpro.th2.infrarepo.Repository;
import com.exactpro.th2.infrarepo.RepositoryResource;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SchemaJob extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(SchemaJob.class);

    private Collection<DynamicResource> dynamicResources;
    private List<UpdatedResource> updatedResources;
    private Gitter gitter;
    private RegistryConnection connection;

    public SchemaJob(Collection<DynamicResource> dynamicResources, Gitter gitter, RegistryConnection connection) {
        this.dynamicResources = dynamicResources;
        this.updatedResources = new ArrayList<>();
        this.gitter = gitter;
        this.connection = connection;
    }

    @Override
    public void start() {
        logger.info("Checking for new versions for resources in schema: \"{}\"", gitter.getBranch());
        for (DynamicResource resource : dynamicResources) {
            TagUpdater.getLatestTags(resource, updatedResources, connection);
        }
        try {
            String commitRef = commitAndPush();
            if (commitRef != null) {
                logger.info("Successfully pushed to branch: \"{}\" commitRef: \"{}\"", gitter.getBranch(), commitRef);
            } else {
                logger.info("All files up to date for branch: \"{}\"", gitter.getBranch());
            }
        } catch (Exception e) {
            logger.info("Exception while pushing to branch: \"{}\".", gitter.getBranch());
        }
    }

    private String commitAndPush() throws IOException, GitAPIException {
        try {
            gitter.lock();
            Set<RepositoryResource> repositoryResources = Repository.getSnapshot(gitter).getResources();
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
                    if (latestVersion.equals(currentVersion)) {
                        //TODO remove log after testing
                        logger.info("Couldn't find new version for resource: \"{}\"", resourceName);
                        continue;
                    }
                    logger.info("Found new version for resource: \"{}\"", resourceName);
                    SpecUtils.changeImageVersion(spec, latestVersion);
                    try {
                        Repository.update(gitter, repositoryResource);
                        logger.info("Successfully updated repository with: \"{}\"", resourceName);
                    } catch (Exception e) {
                        logger.info("Exception while updating repository with : \"{}\"", resourceName);
                    }
                }
            }
            return gitter.commitAndPush("Updated image versions");
        } finally {
            gitter.unlock();
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
