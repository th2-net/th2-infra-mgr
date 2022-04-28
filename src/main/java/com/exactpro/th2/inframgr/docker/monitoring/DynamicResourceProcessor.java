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

package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.inframgr.docker.RegistryCredentialLookup;
import com.exactpro.th2.inframgr.docker.monitoring.watcher.RegistryWatcher;
import com.exactpro.th2.inframgr.docker.util.SpecUtils;
import com.exactpro.th2.inframgr.docker.util.VersionNumberUtils;
import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.inframgr.statuswatcher.ResourcePath;
import com.exactpro.th2.infrarepo.RepositoryResource;
import com.exactpro.th2.infrarepo.ResourceType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;

@Component
public class DynamicResourceProcessor {

    private static final Logger logger = LoggerFactory.getLogger(DynamicResourceProcessor.class);

    private static final long REGISTRY_CHECK_PERIOD_SECONDS = 200;

    private static final long REGISTRY_CHECK_INITIAL_DELAY_SECONDS = 30;

    private static final List<String> monitoredKinds = List.of(
            ResourceType.Th2Box.kind(),
            ResourceType.Th2CoreBox.kind(),
            ResourceType.Th2Estore.kind(),
            ResourceType.Th2Mstore.kind()
    );

    private static final DynamicResourcesCache DYNAMIC_RESOURCES_CACHE = DynamicResourcesCache.INSTANCE;

    private DynamicResourceProcessor() {
    }

    public static void checkResource(RepositoryResource resource, String schema) {
        checkResource(resource, schema, false);
    }

    public static void checkResource(RepositoryResource resource, String schema, boolean delete) {
        String kind = resource.getKind();
        String name = resource.getMetadata().getName();
        String resourceLabel = ResourcePath.annotationFor(schema, kind, name);
        if (!monitoredKinds.contains(kind)) {
            return;
        }

        if (delete) {
            removeFromTrackedResources(schema, name, resourceLabel, "Resource deleted from repository");
            return;
        }

        var spec = resource.getSpec();
        if (spec == null) {
            removeFromTrackedResources(schema, name, resourceLabel, "spec section is null");
            return;
        }

        String versionRange = SpecUtils.getImageVersionRange(spec);
        if (versionRange == null) {
            removeFromTrackedResources(schema, name, resourceLabel, "versionRange field is null");
            return;
        }
        updateTrackedResources(schema, name, kind, versionRange, resource);
    }

    public static void deleteSchema(String schema) {
        DYNAMIC_RESOURCES_CACHE.removeSchema(schema);
        logger.info("Removing resources associated with schema: \"{}\"", schema);
    }

    private static void removeFromTrackedResources(String schema, String name, String resourceLabel, String cause) {
        var removedResource = DYNAMIC_RESOURCES_CACHE.removeResource(schema, name);
        if (removedResource != null) {
            logger.info("Removing resource: \"{}\" from dynamic version tracking. {}", resourceLabel, cause);
        }
    }

    private static void updateTrackedResources(String schema,
                                               String name,
                                               String kind,
                                               String versionRange,
                                               RepositoryResource resource) {

        String resourceLabel = ResourcePath.annotationFor(schema, kind, name);
        var spec = resource.getSpec();
        String image = SpecUtils.getImageName(spec);
        String currentVersion = SpecUtils.getImageVersion(spec);
        String versionRangeChopped = StringUtils.chop(versionRange);

        if (VersionNumberUtils.validate(currentVersion, versionRangeChopped)) {
            logger.info("Resource: \"{}\" needs dynamic version tracking", resourceLabel);
            var alreadyInCache = DYNAMIC_RESOURCES_CACHE.add(
                    schema,
                    new DynamicResource(name, kind, image, currentVersion, versionRangeChopped, schema)
            );
            if (alreadyInCache != null) {
                logger.info("Modified resource: \"{}\" in dynamic resources cache", resourceLabel);
            } else {
                logger.info("Added: \"{}\" to dynamic resources cache", resourceLabel);
            }
        } else {
            logger.error("Current image-version: \"{}\" of resource: " +
                            "\"{}.{}\" doesn't match versionRange: \"{}\". Will not be monitored",
                    currentVersion, schema, name, versionRange);
            removeFromTrackedResources(schema, name, resourceLabel, "image-version doesn't match versionRange");
        }
    }

    @PostConstruct
    public void start() throws IOException {
        Kubernetes kube = new Kubernetes(Config.getInstance().getKubernetes(), null);
        RegistryCredentialLookup secretMapper = new RegistryCredentialLookup(kube);
        RegistryConnection registryConnection = new RegistryConnection(secretMapper.getCredentials());
        RegistryWatcher registryWatcher = new RegistryWatcher(
                REGISTRY_CHECK_INITIAL_DELAY_SECONDS,
                REGISTRY_CHECK_PERIOD_SECONDS,
                registryConnection
        );
        registryWatcher.startWatchingRegistry();
        logger.info("DynamicResourceProcessor has been started");
    }
}
