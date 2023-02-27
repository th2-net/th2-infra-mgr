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
import com.exactpro.th2.infrarepo.InconsistentRepositoryStateException;
import com.exactpro.th2.infrarepo.git.Gitter;
import com.exactpro.th2.infrarepo.repo.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.exactpro.th2.inframgr.statuswatcher.ResourcePath.annotationFor;

public class SchemaJob implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SchemaJob.class);

    private final TagUpdater tagUpdater;

    private final Gitter gitter;

    private final String schema;

    public SchemaJob(Collection<DynamicResource> dynamicResources,
                     RegistryConnection connection,
                     Gitter gitter,
                     String schema) {
        this.tagUpdater = new TagUpdater(dynamicResources, connection);
        this.gitter = gitter;
        this.schema = schema;
    }

    @Override
    public void run() {
        logger.debug("Checking for new versions of resources in schema: \"{}\"", schema);
        List<UpdatedResource> updatedResources = tagUpdater.checkLatestTags();

        if (!updatedResources.isEmpty()) {
            try {
                gitter.lock();
                gitter.checkout();
                if (updateRepository(updatedResources)) {
                    pushToRemoteRepository();
                }
            } catch (Exception e) {
                logger.error("Exception while working with gitter: \"{}\".", schema, e);
            } finally {
                gitter.unlock();
            }
        }
    }

    private boolean updateRepository(List<UpdatedResource> updatedResources) {
        boolean isUpdated = false;
        for (UpdatedResource updatedResource : updatedResources) {
            String resLabel = annotationFor(schema, updatedResource.getKind(), updatedResource.getName());
            try {
                var repositoryResource = Repository.getResource(
                        gitter,
                        updatedResource.getKind(),
                        updatedResource.getName()
                );
                if (repositoryResource != null) {
                    var spec = repositoryResource.getSpec();
                    String latestVersion = updatedResource.getLatestVersion();
                    String currentVersion = SpecUtils.getImageVersion(spec);

                    if (latestVersion.equals(currentVersion)) {
                        continue;
                    }
                    logger.info("Found new version for resource: \"{}\"", resLabel);
                    SpecUtils.changeImageVersion(spec, latestVersion);
                    Repository.update(gitter, repositoryResource);
                    logger.info("Successfully updated repository with: \"{}\"", resLabel);
                    isUpdated = true;
                } else {
                    logger.warn("Resource \"{}\" is not present in repository", resLabel);
                }
            } catch (Exception e) {
                logger.error("Unexpected Exception while updating local repository with resource \"{}\"", resLabel, e);
            }
        }
        return isUpdated;
    }

    private void pushToRemoteRepository() {
        try {
            String commitRef = gitter.commitAndPush("Updated image versions");
            if (commitRef != null) {
                logger.info("Successfully pushed to branch: \"{}\" commitRef: \"{}\"", schema, commitRef);
            } else {
                logger.info("All files up to date for branch: \"{}\"", schema);
            }
        } catch (InconsistentRepositoryStateException e) {
            logger.error("Inconsistent repository state exception for branch \"{}\"", schema, e);
            try {
                gitter.recreateCache();
            } catch (Exception re) {
                logger.error("Exception recreating repository's local cache for branch \"{}\"", schema, re);
            }
        } catch (Exception e) {
            logger.error("Exception while pushing to branch: \"{}\".", schema, e);
        }
    }
}
