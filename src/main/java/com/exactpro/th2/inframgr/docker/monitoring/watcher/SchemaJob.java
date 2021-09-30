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

package com.exactpro.th2.inframgr.docker.monitoring.watcher;

import com.exactpro.th2.inframgr.SchemaEventRouter;
import com.exactpro.th2.inframgr.docker.monitoring.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.inframgr.docker.util.SpecUtils;
import com.exactpro.th2.inframgr.repository.RepositoryUpdateEvent;
import com.exactpro.th2.inframgr.statuswatcher.ResourcePath;
import com.exactpro.th2.infrarepo.Gitter;
import com.exactpro.th2.infrarepo.Repository;
import com.exactpro.th2.infrarepo.RepositoryResource;
import com.exactpro.th2.infrarepo.RepositorySnapshot;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SchemaJob implements Runnable {
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
    public void run() {
        logger.info("Checking for new versions of resources in schema: \"{}\"", schema);
        for (DynamicResource resource : dynamicResources) {
            TagUpdater.checkLatestTags(resource, updatedResources, connection);
        }
        try {
            commitAndPush();
        } catch (Exception e) {
            logger.error("Exception while pushing to branch: \"{}\".", schema);
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
                    continue;
                }
                logger.info("Found new version for resource: \"{}\"", resourceLabel);
                SpecUtils.changeImageVersion(spec, latestVersion);
                try {
                    Repository.update(gitter, repositoryResource);
                    logger.info("Successfully updated repository with: \"{}\"", resourceLabel);
                } catch (Exception e) {
                    logger.error("Exception while updating repository with : \"{}\"", resourceLabel);
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
