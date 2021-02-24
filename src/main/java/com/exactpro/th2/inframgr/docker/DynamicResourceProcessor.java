package com.exactpro.th2.inframgr.docker;

import com.exactpro.th2.inframgr.docker.util.SpecUtils;
import com.exactpro.th2.inframgr.docker.util.TagValidator;
import com.exactpro.th2.infrarepo.RepositoryResource;
import com.exactpro.th2.infrarepo.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Arrays;
import java.util.List;

public class DynamicResourceProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DynamicResourceProcessor.class);

    private static final List<String> monitoredKinds = Arrays.asList(
            ResourceType.Th2Box.kind(),
            ResourceType.Th2CoreBox.kind(),
            ResourceType.Th2Estore.kind(),
            ResourceType.Th2Mstore.kind()
    );

    private static final DynamicResourcesCache trackedResources = DynamicResourcesCache.INSTANCE;

    private DynamicResourceProcessor() {
        throw new AssertionError("This method should not be called");
    }

    public static void checkResource(RepositoryResource resource, String schema){
        checkResource(resource, schema, false);
    }

    public static void checkResource(RepositoryResource resource, String schema, boolean deleted ) {
        String kind = resource.getKind();
        String name = resource.getMetadata().getName();
        if (!monitoredKinds.contains(kind)) {
            logger.debug("Resource: \"{}.{}\" is of kind: \"{}\". no need to check for tag pattern",
                    schema, name, kind);
            return;
        }

        if (deleted) {
            removeFromTrackedResources(schema, name);
            return;
        }

        var spec = resource.getSpec();
        if (spec == null) {
            logger.debug("Resource: \"{}.{}\" doesn't have spec section", schema, name);
            return;
        }

        String versionRange = SpecUtils.getImageVersionRange(spec);
        if (versionRange == null) {
            logger.debug("Resource: \"{}.{}\" doesn't have pattern section", schema, name);
            removeFromTrackedResources(schema, name);
            return;
        }
        addToTrackedResources(schema, name, versionRange, resource);
    }

    //TODO should it be synchronized ?
    public static void removeFromTrackedResources(String schema, String name) {
        logger.info("Removing resource: \"{}.{}\" from dynamic version tracking", schema, name);
        trackedResources.remove(schema, name);
    }

    //TODO should it be synchronized ?
    public static void addToTrackedResources(String schema, String name, String pattern, RepositoryResource resource) {
        var spec = resource.getSpec();
        String image = SpecUtils.getImageName(spec);
        String tag = SpecUtils.getImageVersion(spec);

        if (image != null && tag != null) {
            if (TagValidator.validate(tag, pattern)) {
                logger.info("Adding resource: \"{}.{}\" from dynamic version tracking", schema, name);
                trackedResources.add(schema, name, new DynamicResource(image, tag, pattern, schema, resource));
            } else {
                logger.error("Current image-version: \"{}\" of resource: \"{}.{}\" doesn't match mask: \"{}\". Will not be monitored",
                        tag, schema, name, pattern);
            }
        } else {
            logger.error("Resource: \"{}.{}\" doesn't have image-name or image-version section", schema, name);
        }
    }
}
