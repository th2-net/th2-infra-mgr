package com.exactpro.th2.schema.schemamanager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class K8sSynchronizationJobQueue {
    public static class Job {
        private String schema;
        private long creationTime;
        private long processingTime;
        private long completionTime;
        private int callId;
        public Job(String schema) {
            this.schema = schema;
            this.creationTime = System.currentTimeMillis();
        }

        public String getSchema() {
            return schema;
        }
    }


    private LinkedHashMap<String, Job> jobQueue;
    private Map<String, Job> jobsInProgress;

    public K8sSynchronizationJobQueue() {
        jobQueue = new LinkedHashMap<>();
        jobsInProgress = new HashMap<>();
    }

    public synchronized void addJob(Job job) {

        if (!jobQueue.containsKey(job.schema))
            jobQueue.put(job.schema, job);
    }

    private volatile int callId = 0;
    public synchronized Job takeJob() {
        return takeJob(++callId);
    }

    private synchronized Job takeJob(int callId) {

        if (jobQueue.isEmpty())
            return null;

        Iterator<Job> iterator = jobQueue.values().iterator();
        Job job = iterator.next();
        iterator.remove();

        // check if we are not already processing the job
        // and return it as a result
        if (!jobsInProgress.containsKey(job.schema)) {
            job.processingTime = System.currentTimeMillis();
            jobsInProgress.put(job.schema, job);
            return job;
        }

        // at this stage we are already processing this job in some thread
        // return it and  take another
        addJob(job);

        if (job.callId == callId)
            // we have completed circle, no jobs can be taken at this point
            return null;

        job.callId = callId;
        return takeJob(callId);
    }

    public synchronized Job completeJob(Job job) throws IllegalStateException {

        if (!jobsInProgress.containsKey(job.schema))
            throw new IllegalStateException("Job \"" + job.schema + "\" was not found in active job list");

        jobsInProgress.remove(job.schema);

        job.completionTime = System.currentTimeMillis();
        return job;
    }
}
