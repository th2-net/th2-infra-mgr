package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.docker.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.inframgr.docker.util.SpecMapper;
import com.exactpro.th2.inframgr.docker.util.TagValidator;
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

    private TagUpdater(){}

    public static void updateTag(DynamicResource resource, Gitter gitter, RegistryConnection connection)
            throws IOException, GitAPIException {
        String latestTag = TagValidator.getLatestTag(getAllHigherTags(resource, connection));
        if (latestTag == null || latestTag.equals(resource.getTag())) {
            //TODO remove log after testing
            logger.info("Couldn't find new version for resource: \"{}\"", resource.getAnnotation());
            return;
        }
        //TODO remove log after testing
        logger.info("Found new version for resource: \"{}\", updating repository", resource.getAnnotation());
        try {
            gitter.lock();
            var repositoryResource = getRepositoryResource(gitter, resource.getResourceName());
            if (repositoryResource != null) {
                SpecMapper.changeImageVersion(repositoryResource.getSpec(), latestTag);
                Repository.update(gitter, repositoryResource);
                logger.info("Successfully updated repository with: \"{}\"", resource.getAnnotation());
            }else {
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
        String latest = resource.getTag();
        List<String> allHigherTags = new ArrayList<>();
        List<String> tags;

        //get tags in small amounts starting from current image-version
        do {
            tags = connection.getTags("", PAGE_SIZE, latest);
            allHigherTags.addAll(TagValidator.filterTags(tags, resource.getMask()));
            latest = tags.get(tags.size() - 1);
        } while (!(tags.size() < PAGE_SIZE));

        return allHigherTags;
    }
}
