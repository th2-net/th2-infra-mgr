package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.docker.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.inframgr.docker.util.SpecUtils;
import com.exactpro.th2.inframgr.docker.util.TagValidator;
import com.exactpro.th2.infrarepo.Gitter;
import com.exactpro.th2.infrarepo.Repository;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class TagUpdateJob {

    private static final Logger logger = LoggerFactory.getLogger(TagUpdateJob.class);
    private static final int PAGE_SIZE = 10;

    private DynamicResource resource;
    private ExecutorService executor;
    private RegistryConnection connection;
    private Gitter gitter;

    public TagUpdateJob(DynamicResource resource, ExecutorService executor, Gitter gitter) {
        this.resource = resource;
        this.executor = executor;
        this.connection = new RegistryConnection();
        this.gitter = gitter;
    }

    public void submit() {
        executor.submit(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                Thread.currentThread().setName(resource.getAnnotation());
                updateTagAndCommit();
            } catch (Exception e) {
                logger.error("Exception processing job {}", resource.getAnnotation(), e);
            } finally {
                Thread.currentThread().setName(threadName);
            }
        });
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
            String commitRef = gitter.commitAndPush(String.format("Updated version of \"%s\" to \"%s\"", resource.getResourceName(), latestTag));
            logger.info("Successfully updated branch: \"{}\" commitRef: \"{}\"", resource.getSchema(), commitRef);
        }catch (Exception e){
            logger.error("Exception while working with repository", e);
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
