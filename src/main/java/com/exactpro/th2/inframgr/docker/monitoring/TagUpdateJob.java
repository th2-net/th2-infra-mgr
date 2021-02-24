package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.docker.DynamicResource;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.inframgr.docker.util.TagValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class TagUpdateJob {

    private static final Logger logger = LoggerFactory.getLogger(TagUpdateJob.class);
    private static final int PAGE_SIZE = 10;

    private DynamicResource resource;
    private ExecutorService executor;
    private RegistryConnection connection;


    public TagUpdateJob(DynamicResource resource, ExecutorService executor) {
        this.resource = resource;
        this.executor = executor;
        this.connection = new RegistryConnection();
    }

    public void submit() {
        executor.submit(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                Thread.currentThread().setName(getJobId());
                applyLatestTag();
            } catch (Exception e) {
                logger.error("Exception processing job {}", getJobId(), e);
            } finally {
                Thread.currentThread().setName(threadName);
            }
        });
    }

    public void applyLatestTag() {
        String latestTag = getLatestTag();
        //TODO logic to apply latest tag to repository
    }

    private List<String> getAllHigherTags() {
        String latest = resource.getTag();
        List<String> allHigherTags = new ArrayList<>();
        List<String> responseTags;

        //get tags in small amounts starting from current image-version
        do {
            var response = connection.getTags(PAGE_SIZE, latest);
            responseTags = response.getTags();
            allHigherTags.addAll(filteredTags(responseTags));
            latest = responseTags.get(responseTags.size() - 1);
        } while (!(responseTags.size() < PAGE_SIZE));

        return allHigherTags;
    }

    private List<String> filteredTags(List<String> tags) {
        return tags.stream()
                .filter(tag -> TagValidator.validate(tag, resource.getPattern()))
                .collect(Collectors.toList());
    }

    private String getLatestTag() {
        //TODO check if list is in correct order, if not add logic to find latest tag
        List<String> allHigherTags = getAllHigherTags();
        return allHigherTags.get(allHigherTags.size() - 1);
    }

    public String getJobId() {
        return "";
    }
}
