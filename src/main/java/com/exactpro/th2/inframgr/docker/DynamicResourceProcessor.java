package com.exactpro.th2.inframgr.docker;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.docker.monitoring.RegistryWatcher;
import com.exactpro.th2.inframgr.docker.util.SpecMapper;
import com.exactpro.th2.inframgr.docker.util.TagValidator;
import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.infrarepo.RepositoryResource;
import com.exactpro.th2.infrarepo.ResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final long REGISTRY_CHECK_PERIOD_SECONDS = 300;
    private static final long REGISTRY_CHECK_INITIAL_DELAY_SECONDS = 10;
    private static final List<String> monitoredKinds = Arrays.asList(
            ResourceType.Th2Box.kind(),
            ResourceType.Th2CoreBox.kind(),
            ResourceType.Th2Estore.kind(),
            ResourceType.Th2Mstore.kind()
    );
    private static final DynamicResourcesCache DYNAMIC_RESOURCES_CACHE = DynamicResourcesCache.INSTANCE;

    private DynamicResourceProcessor() {}

    public static void checkResource(RepositoryResource resource, String schema) {
        checkResource(resource, schema, false);
    }

    public static void checkResource(RepositoryResource resource, String schema, boolean deleted) {
        String kind = resource.getKind();
        String name = resource.getMetadata().getName();
        if (!monitoredKinds.contains(kind)) {
            return;
        }

        if (deleted) {
            removeFromTrackedResources(schema, name);
            return;
        }

        var spec = resource.getSpec();
        if (spec == null) {
            return;
        }

        String versionRange = SpecMapper.getImageVersionRange(spec);
        if (versionRange == null) {
            removeFromTrackedResources(schema, name);
            return;
        }
        addToTrackedResources(schema, name, versionRange, resource);
    }

    private static void removeFromTrackedResources(String schema, String name) {
        logger.info("Removing resource: \"{}.{}\" from dynamic version tracking", schema, name);
        DYNAMIC_RESOURCES_CACHE.remove(schema, name);
    }

    private static void addToTrackedResources(String schema, String name, String mask, RepositoryResource resource) {
        var spec = resource.getSpec();
        String image = SpecMapper.getImageName(spec);
        String tag = SpecMapper.getImageVersion(spec);

        if (TagValidator.validate(tag, mask)) {
            logger.info("Adding resource: \"{}.{}\" from dynamic version tracking", schema, name);
            DYNAMIC_RESOURCES_CACHE.add(schema, new DynamicResource(name, image, tag, mask, schema));
        } else {
            logger.error("Current image-version: \"{}\" of resource: \"{}.{}\" doesn't match mask: \"{}\". Will not be monitored",
                    tag, schema, name, mask);
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
    }
}
