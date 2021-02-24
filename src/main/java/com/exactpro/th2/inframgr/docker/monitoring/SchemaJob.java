package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.docker.DynamicResource;
import com.exactpro.th2.infrarepo.Gitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SchemaJob extends Thread {
    private static final Logger logger = LoggerFactory.getLogger(SchemaJob.class);
    private static final int THREAD_POOL_SIZE_EXECUTOR = 10;
    private final int WAIT_TIME_MS = 500;

    private ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE_EXECUTOR);
    private Map<String, DynamicResource> tagUpdateJobs;
    private Gitter gitter;

    public SchemaJob(Map<String, DynamicResource> tagUpdateJobs, Gitter gitter) {
        this.tagUpdateJobs = tagUpdateJobs;
        this.gitter = gitter;
    }


    @Override
    public void start() {
        for (DynamicResource resource : tagUpdateJobs.values()) {
            submitTagJob(resource);
        }
        while (!isInterrupted()) {
            try {
                Thread.sleep(WAIT_TIME_MS);
            } catch (InterruptedException e) {
                break;
            }
            if (tagUpdateJobs.isEmpty()) {
                try {
                    String commitRef = gitter.commitAndPush("Updated image versions");
                    logger.info("Successfully updated branch: \"{}\" commitRef: \"{}\"", gitter.getBranch(), commitRef);
                } catch (Exception e) {
                    logger.info("Exception while pushing to branch: \"{}\".", gitter.getBranch());
                }finally {
                    shutDown();
                }
            }
        }
        logger.info("SchemaJob worker thread interrupted, stopping executor.");
        shutDown();
    }

    private void submitTagJob(DynamicResource resource) {
        executor.submit(() -> {
            String threadName = Thread.currentThread().getName();
            try {
                Thread.currentThread().setName(resource.getAnnotation());
                new TagUpdater(resource, gitter).updateTagAndCommit();
            } catch (Exception e) {
                logger.error("Exception processing job {}", resource.getAnnotation(), e);
            } finally {
                tagUpdateJobs.remove(resource.getResourceName());
                Thread.currentThread().setName(threadName);
            }
        });
    }

    private void shutDown(){
        executor.shutdown();
    }
}
