package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.docker.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.inframgr.docker.util.VersionNumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TagUpdater {

    private static final Logger logger = LoggerFactory.getLogger(TagUpdater.class);
    private static final int PAGE_SIZE = 10;

    private TagUpdater() {
    }

    static void checkForNewVersion(DynamicResource resource, List<SchemaJob.ModifiedResource> modifiedResources, RegistryConnection connection) {
        String latestTag = VersionNumberUtils.getLatestTag(getAllHigherTags(resource, connection));
        if (latestTag == null || latestTag.equals(resource.getCurrentVersion())) {
            //TODO remove log after testing
            logger.info("Couldn't find new version for resource: \"{}\"", resource.getAnnotation());
            return;
        }
        logger.info("Found new version for resource: \"{}\"", resource.getAnnotation());
        modifiedResources.add(new SchemaJob.ModifiedResource(resource.getName(), latestTag));

    }

    private static List<String> getAllHigherTags(DynamicResource resource, RegistryConnection connection) {
        String versionRange = resource.getVersionRange();
        String image = resource.getImage();
        List<String> allHigherTags = new ArrayList<>();
        List<String> tags;

        //get tags in small amounts starting from current image-version
        do {
            tags = connection.getTags(image, PAGE_SIZE, versionRange);
            if (tags == null || tags.size() < 1) {
                break;
            }
            allHigherTags.addAll(VersionNumberUtils.filterTags(tags, resource.getVersionRange()));
            versionRange = tags.get(tags.size() - 1);
        } while (!(tags.size() < PAGE_SIZE));

        return allHigherTags;
    }
}
