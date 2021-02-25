package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.docker.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.inframgr.docker.util.SpecMapper;
import com.exactpro.th2.inframgr.docker.util.TagValidator;
import com.exactpro.th2.infrarepo.Gitter;
import com.exactpro.th2.infrarepo.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TagUpdater {

    private static final Logger logger = LoggerFactory.getLogger(TagUpdater.class);
    private static final int PAGE_SIZE = 10;

    private RegistryConnection connection;
    private DynamicResource resource;
    private Gitter gitter;

    public TagUpdater(DynamicResource resource, Gitter gitter, RegistryConnection connection) {
        this.connection = connection;
        this.resource = resource;
        this.gitter = gitter;
    }

    public void updateTagAndCommit() throws IOException {
        String latestTag = TagValidator.getLatestTag(getAllHigherTags());
        if (latestTag == null || latestTag.equals(resource.getTag())) {
            //TODO remove log after testing
            logger.info("Couldn't find new version for resource: \"{}\"", resource.getAnnotation());
            return;
        }
        //TODO remove log after testing
        logger.info("Found new version for resource: \"{}\", updating repository", resource.getAnnotation());
        SpecMapper.changeImageVersion(resource.getRepositoryResource().getSpec(), latestTag);
        try {
            gitter.lock();
            Repository.update(gitter, resource.getRepositoryResource());
            logger.info("Successfully updated repository with: \"{}\"", resource.getAnnotation());
        } finally {
            gitter.unlock();
        }
    }

    private List<String> getAllHigherTags() {
        String latest = resource.getTag();
        List<String> allHigherTags = new ArrayList<>();
        List<String> tags;

        //get tags in small amounts starting from current image-version
        do {
            tags = connection.getTags("", PAGE_SIZE, latest);
            allHigherTags.addAll(TagValidator.filteredTags(tags, resource.getMask()));
            latest = tags.get(tags.size() - 1);
        } while (!(tags.size() < PAGE_SIZE));

        return allHigherTags;
    }
}
