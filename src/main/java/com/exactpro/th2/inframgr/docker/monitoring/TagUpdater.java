package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.docker.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;

import java.util.ArrayList;
import java.util.List;

import static com.exactpro.th2.inframgr.docker.util.VersionNumberUtils.chooseLatestVersion;
import static com.exactpro.th2.inframgr.docker.util.VersionNumberUtils.filterTags;

public class TagUpdater {

    private static final int PAGE_SIZE = 10;

    private TagUpdater() {
    }

    static void getLatestTags(DynamicResource resource, List<SchemaJob.UpdatedResource> updatedResources, RegistryConnection connection) {
        String latestTag = chooseLatestVersion(getNewerTags(resource, connection));
        if (latestTag != null) {
            updatedResources.add(new SchemaJob.UpdatedResource(resource.getName(), resource.getVersionRange() + latestTag));
        }
    }

    private static List<String> getNewerTags(DynamicResource resource, RegistryConnection connection) {
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
            allHigherTags.addAll(filterTags(tags, resource.getVersionRange()));
            versionRange = tags.get(tags.size() - 1);
        } while (!(tags.size() < PAGE_SIZE));

        return allHigherTags;
    }
}
