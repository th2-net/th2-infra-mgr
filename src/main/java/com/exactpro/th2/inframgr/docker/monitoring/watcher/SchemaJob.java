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

import com.exactpro.th2.inframgr.docker.monitoring.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.inframgr.docker.util.SpecUtils;
import com.exactpro.th2.infrarepo.git.Gitter;
import com.exactpro.th2.infrarepo.repo.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.exactpro.th2.inframgr.statuswatcher.ResourcePath.annotationFor;

public class SchemaJob implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SchemaJob.class);

    private final Collection<DynamicResource> dynamicResources;

    private final List<UpdatedResource> updatedResources;

    private final RegistryConnection connection;

    private final Gitter gitter;

    private final String schema;

    public SchemaJob(Collection<DynamicResource> dynamicResources,
                     RegistryConnection connection,
                     Gitter gitter,
                     String schema) {
        this.dynamicResources = dynamicResources;
        this.updatedResources = new ArrayList<>();
        this.connection = connection;
        this.gitter = gitter;
        this.schema = schema;
    }

    @Override
    public void run() {
        logger.info("Checking for new versions of resources in schema: \"{}\"", schema);
        try {
            for (DynamicResource resource : dynamicResources) {
                TagUpdater.checkLatestTags(resource, updatedResources, connection);
            }
            if (!updatedResources.isEmpty()) {
                try {
                    gitter.lock();
                    gitter.checkout();
                    updateRepository();
                    try {
                        String commitRef = gitter.commitAndPush("Updated image versions");
                        if (commitRef != null) {
                            logger.info("Successfully pushed to branch: \"{}\" commitRef: \"{}\"", schema, commitRef);
                        } else {
                            logger.info("All files up to date for branch: \"{}\"", schema);
                        }
                    } catch (Exception e) {
                        logger.error("Exception while pushing to branch: \"{}\".", schema, e);
                    }
                } catch (Exception e) {
                    logger.error("Exception while working with gitter: \"{}\".", schema, e);
                } finally {
                    gitter.unlock();
                }
            }
        } catch (Exception e) {
            logger.error("Unexpected Exception while getting new versions for resources in schema: \"{}\".", schema, e);
        }
    }

    private void updateRepository() {
        for (UpdatedResource updatedResource : updatedResources) {
            try {
                var repositoryResource = Repository.getResource(gitter, updatedResource.kind, updatedResource.name);
                if (repositoryResource != null) {
                    var spec = repositoryResource.getSpec();
                    String resourceName = repositoryResource.getMetadata().getName();
                    String latestVersion = updatedResource.latestVersion;
                    String currentVersion = SpecUtils.getImageVersion(spec);
                    String resLabel = annotationFor(schema, repositoryResource.getKind(), resourceName);

                    if (latestVersion.equals(currentVersion)) {
                        continue;
                    }
                    logger.info("Found new version for resource: \"{}\"", resLabel);
                    SpecUtils.changeImageVersion(spec, latestVersion);
                    try {
                        Repository.update(gitter, repositoryResource);
                        logger.info("Successfully updated repository with: \"{}\"", resLabel);
                    } catch (Exception e) {
                        logger.error("Exception while updating repository with : \"{}\"", resLabel, e);
                    }
                } else {
                    logger.warn("Resource \"{}\" is not present in repository",
                            annotationFor(schema, updatedResource.kind, updatedResource.name));
                }
            } catch (Exception e) {
                logger.error("Unexpected Exception while working with repository", e);
            }
        }
    }

    static class UpdatedResource {

        private final String name;

        private final String kind;

        private final String latestVersion;

        public UpdatedResource(String name, String kind, String latestVersion) {
            this.name = name;
            this.kind = kind;
            this.latestVersion = latestVersion;
        }
    }
}
