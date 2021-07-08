package com.exactpro.th2.inframgr.cassandra.jobs;

import com.exactpro.th2.inframgr.util.RetryableTask;

public class SchemaCreationMonitoringJob implements RetryableTask {
    @Override
    public String getUniqueKey() {
        return null;
    }

    @Override
    public long getRetryDelay() {
        return 0;
    }

    @Override
    public void run() {

    }
}
