package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.docker.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.inframgr.docker.util.SpecUtils;
import com.exactpro.th2.inframgr.docker.util.TagValidator;
import com.exactpro.th2.infrarepo.Gitter;
import com.exactpro.th2.infrarepo.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TagUpdater {

    private static final Logger logger = LoggerFactory.getLogger(TagUpdater.class);
    private static final int PAGE_SIZE = 10;

    private RegistryConnection connection;
    private DynamicResource resource;
    private Gitter gitter;

    public TagUpdater(DynamicResource resource, Gitter gitter) {
        this.connection = new RegistryConnection();
        this.resource = resource;
        this.gitter = gitter;
    }

    public void updateTagAndCommit(){
        String latestTag = TagValidator.getLatestTag(getAllHigherTags());
        if(latestTag == null || latestTag.equals(resource.getTag())){
            logger.info("Couldn't find new version for resource: \"{}\"", resource.getAnnotation());
            return;
        }
        logger.info("Found new version for resource: \"{}\", updating repository", resource.getAnnotation());
        SpecUtils.changeImageVersion(resource.getRepositoryResource().getSpec(), latestTag);
        try {
            Repository.update(gitter, resource.getRepositoryResource());
        }catch (Exception e){
            logger.error("Exception while updating repository", e);
        }
    }

    private List<String> getAllHigherTags() {
        String latest = resource.getTag();
        List<String> allHigherTags = new ArrayList<>();
        List<String> responseTags;

        //get tags in small amounts starting from current image-version
        do {
            var response = connection.getTags(PAGE_SIZE, latest);
            responseTags = response.getTags();
            allHigherTags.addAll(TagValidator.filteredTags(responseTags, resource.getMask()));
            latest = responseTags.get(responseTags.size() - 1);
        } while (!(responseTags.size() < PAGE_SIZE));

        return allHigherTags;
    }
}
