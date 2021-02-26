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
    private List<ModifiedResource> modifiedResources;
    private Gitter gitter;
    private RegistryConnection connection;

    public SchemaJob(Collection<DynamicResource> dynamicResources, Gitter gitter, RegistryConnection connection) {
        this.dynamicResources = dynamicResources;
        this.modifiedResources = new ArrayList<>();
        this.gitter = gitter;
        this.connection = connection;
    }

    @Override
    public void start() {
        logger.info("Checking for new versions for resources in schema: \"{}\"", gitter.getBranch());
        for (DynamicResource resource : dynamicResources) {
            TagUpdater.checkForNewVersion(resource, modifiedResources, connection);
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

            for (SchemaJob.ModifiedResource modifiedResource : modifiedResources) {
                var repositoryResource = resourceSpecMap.get(modifiedResource.getName());
                if (repositoryResource != null) {
                    var spec = repositoryResource.getSpec();
                    SpecUtils.changeImageVersion(spec, modifiedResource.getLatestTag());
                    try {
                        Repository.update(gitter, repositoryResource);
                        logger.info("Successfully updated repository with: \"{}\"", repositoryResource.getMetadata().getName());
                    } catch (Exception e) {
                        logger.info("Exception while updating repository with : \"{}\"", repositoryResource.getMetadata().getName());
                    }
                }
            }
            return gitter.commitAndPush("Updated image versions");
        } finally {
            gitter.unlock();
        }
    }

    static class ModifiedResource {
        private final String name;
        private final String latestTag;

        public ModifiedResource(String name, String latestTag) {
            this.name = name;
            this.latestTag = latestTag;
        }

        public String getName() {
            return name;
        }

        public String getLatestTag() {
            return latestTag;
        }
    }
}
