package com.exactpro.th2.inframgr.docker;

import com.exactpro.th2.inframgr.docker.util.TagValidator;
import com.exactpro.th2.inframgr.k8s.K8sCustomResource;
import com.exactpro.th2.infrarepo.ResourceType;
import io.fabric8.kubernetes.client.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.Arrays;
import java.util.List;

import static com.exactpro.th2.inframgr.docker.util.SpecExtractor.getFieldAsString;

public class DynamicResourceProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DynamicResourceProcessor.class);

    private static final String IMAGE_NAME_ALIAS = "image-name";
    private static final String IMAGE_VERSION_ALIAS = "image-version";
    private static final String VERSION_RANGE_ALIAS = "version-range";

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

    public static void checkResource(K8sCustomResource resource, String schema, Watcher.Action action) {
        String kind = resource.getKind();
        String name = resource.getMetadata().getName();
        if (!monitoredKinds.contains(kind)) {
            logger.debug("Resource: {} in schema: {} is of kind: {}. no need to check for tag pattern",
                    name, schema, kind);
            return;
        }

        if (action == Watcher.Action.DELETED) {
            removeFromTrackedResources(schema, name);
            return;
        }

        var spec = resource.getSpec();
        if (spec == null) {
            logger.debug("Resource: {} in schema: {} doesn't have spec section", name, schema);
            return;
        }

        String patternNode = getFieldAsString(spec, VERSION_RANGE_ALIAS);
        if (patternNode == null) {
            logger.debug("Resource: {} in schema: {} doesn't have pattern section", name, schema);
            removeFromTrackedResources(schema, name);
            return;
        }
        addToTrackedResources(schema, name, patternNode, spec);
    }

    //TODO should it be synchronized ?
    public static void removeFromTrackedResources(String schema, String name) {
        trackedResources.remove(schema, name);
    }

    //TODO should it be synchronized ?
    public static void addToTrackedResources(String schema, String name, String pattern, Object spec) {
        String image = getFieldAsString(spec, IMAGE_NAME_ALIAS);
        String tag = getFieldAsString(spec, IMAGE_VERSION_ALIAS);

        if (image != null && tag != null) {
            if (TagValidator.validate(tag, pattern)) {
                trackedResources.add(schema, name, new DynamicResource(image, tag, pattern, schema));
            } else {
                logger.error("Current image-version: {} of resource: {} in schema: {} doesn't match pattern: {}. Will not be monitored",
                        tag, name, schema, pattern);
            }
        } else {
            logger.error("Resource: {} in schema: {} doesn't have image-name or image-version section", name, schema);
        }
    }
}
