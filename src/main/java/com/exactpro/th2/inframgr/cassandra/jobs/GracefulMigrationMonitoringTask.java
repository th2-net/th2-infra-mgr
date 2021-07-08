package com.exactpro.th2.inframgr.cassandra.jobs;

import com.exactpro.th2.inframgr.k8s.Kubernetes;
import com.exactpro.th2.inframgr.util.RetryableTask;
import io.fabric8.kubernetes.api.model.batch.CronJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GracefulMigrationMonitoringTask implements RetryableTask {
    private static final Logger logger = LoggerFactory.getLogger(GracefulMigrationMonitoringTask.class);
    private static final long RETRY_DELAY = 50;

    private final String schemaName;

    private final String jobName;

    private final Kubernetes kube;

    public GracefulMigrationMonitoringTask(String schemaName, String jobName, Kubernetes kube) {
        this.schemaName = schemaName;
        this.jobName = jobName;
        this.kube = kube;
    }

    @Override
    public String getUniqueKey() {
        return String.format("%s:%s:%s", GracefulMigrationMonitoringTask.class.getName(), schemaName, jobName);
    }

    @Override
    public long getRetryDelay() {
        return RETRY_DELAY;
    }

    @Override
    public void run() {
        logger.info("Executing task: \"{}\"", getUniqueKey());
        checkJobStatus();
    }

    private void checkJobStatus() {
        CronJob job = kube.getJob(jobName);
    }
}
