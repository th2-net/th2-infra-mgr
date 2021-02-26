package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.docker.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.inframgr.docker.util.SpecUtils;
import com.exactpro.th2.inframgr.docker.util.VersionNumberUtils;
import com.exactpro.th2.infrarepo.Gitter;
import com.exactpro.th2.infrarepo.Repository;
import com.exactpro.th2.infrarepo.RepositoryResource;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TagUpdater {

    private static final Logger logger = LoggerFactory.getLogger(TagUpdater.class);
    private static final int PAGE_SIZE = 10;

    private TagUpdater() {
    }

    public static void updateTag(DynamicResource resource, Gitter gitter, RegistryConnection connection)
            throws IOException, GitAPIException {
        String latestTag = VersionNumberUtils.getLatestTag(getAllHigherTags(resource, connection));
        try {
            gitter.lock();
            var repositoryResource = getRepositoryResource(gitter, resource.getName());
            if (repositoryResource != null) {
                var spec = repositoryResource.getSpec();
                String prevTag = SpecUtils.getImageVersion(spec);
                if (latestTag == null || latestTag.equals(prevTag)) {
                    //TODO remove log after testing
                    logger.info("Couldn't find new version for resource: \"{}\"", resource.getAnnotation());
                    return;
                }
                //TODO remove log after testing
                logger.info("Found new version for resource: \"{}\", updating repository", resource.getAnnotation());
                SpecUtils.changeImageVersion(spec, latestTag);
                Repository.update(gitter, repositoryResource);
                logger.info("Successfully updated repository with: \"{}\"", resource.getAnnotation());
            } else {
                logger.warn("Resource: \"{}\" is not present in repository", resource.getAnnotation());
            }
        } finally {
            gitter.unlock();
        }
    }

    private static RepositoryResource getRepositoryResource(Gitter gitter, String resourceName) throws IOException, GitAPIException {
        var snapshot = Repository.getSnapshot(gitter);
        for (RepositoryResource resource : snapshot.getResources()) {
            if (resource.getMetadata().getName().equals(resourceName)) {
                return resource;
            }
        }
        return null;
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
