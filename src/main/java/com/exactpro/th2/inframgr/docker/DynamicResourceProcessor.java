package com.exactpro.th2.inframgr.docker;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.docker.monitoring.RegistryWatcher;
import com.exactpro.th2.inframgr.docker.util.SpecUtils;
import com.exactpro.th2.inframgr.docker.util.VersionNumberUtils;
import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.inframgr.statuswatcher.ResourcePath;
import com.exactpro.th2.infrarepo.RepositoryResource;
import com.exactpro.th2.infrarepo.ResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class DynamicResourceProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DynamicResourceProcessor.class);
    private static final long REGISTRY_CHECK_PERIOD_SECONDS = 200;
    private static final long REGISTRY_CHECK_INITIAL_DELAY_SECONDS = 30;
    private static final List<String> monitoredKinds = Arrays.asList(
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

    public static void checkResource(RepositoryResource resource, String schema, boolean deleted) {
        String kind = resource.getKind();
        String name = resource.getMetadata().getName();
        String resourceLabel = ResourcePath.annotationFor(schema, kind, name);
        if (!monitoredKinds.contains(kind)) {
            return;
        }

        if (deleted) {
            removeFromTrackedResources(schema, name, resourceLabel, "Resource deleted from repository");
            return;
        }

        var spec = resource.getSpec();
        if (spec == null) {
            return;
        }

        String versionRange = SpecUtils.getImageVersionRange(spec);
        if (versionRange == null) {
            removeFromTrackedResources(schema, name, resourceLabel, "versionRange field is null");
            return;
        }
        updateTrackedResources(schema, name, versionRange, resource);
    }

    private static void removeFromTrackedResources(String schema, String name, String resourceLabel, String cause) {
        var removedResource = DYNAMIC_RESOURCES_CACHE.remove(schema, name);
        if (removedResource != null) {
            logger.info("Removing resource: \"{}\" from dynamic version tracking. {}", resourceLabel, cause);
        }
    }

    private static void updateTrackedResources(String schema, String name, String versionRange, RepositoryResource resource) {
        String resourceLabel = ResourcePath.annotationFor(schema, resource.getKind(), name);
        var spec = resource.getSpec();
        String image = SpecUtils.getImageName(spec);
        String currentVersion = SpecUtils.getImageVersion(spec);
        String versionRangeChopped = StringUtils.chop(versionRange);

        if (VersionNumberUtils.validate(currentVersion, versionRangeChopped)) {
            logger.info("Resource: \"{}\" needs dynamic version tracking", resourceLabel);
            var alreadyInCache = DYNAMIC_RESOURCES_CACHE.add(schema, new DynamicResource(name, image, currentVersion, versionRangeChopped, schema));
            if (alreadyInCache != null) {
                logger.info("Modified resource: \"{}\" in dynamic resources cache", resourceLabel);
            } else {
                logger.info("Added: \"{}\" to dynamic resources cache", resourceLabel);
            }
        } else {
            logger.error("Current image-version: \"{}\" of resource: \"{}.{}\" doesn't match versionRange: \"{}\". Will not be monitored",
                    currentVersion, schema, name, versionRange);
            removeFromTrackedResources(schema, name, resourceLabel, "image-version doesn't match versionRange");
        }
    }

    @PostConstruct
    public void start() throws IOException {
        Kubernetes kube = new Kubernetes(Config.getInstance().getKubernetes(), null);
        ObjectMapper mapper = new ObjectMapper();
        SecretMapper secretMapper = new SecretMapper(kube, mapper);
        RegistryConnection registryConnection = new RegistryConnection(secretMapper.mapSecrets());
        RegistryWatcher registryWatcher = new RegistryWatcher(
                REGISTRY_CHECK_INITIAL_DELAY_SECONDS,
                REGISTRY_CHECK_PERIOD_SECONDS,
                registryConnection
        );
        registryWatcher.startWatchingRegistry();
        logger.info("DynamicResourceProcessor has been started");
    }
}
