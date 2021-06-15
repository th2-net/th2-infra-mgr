package com.exactpro.th2.inframgr.metrics;

import io.prometheus.client.Histogram;

public class ManagerMetrics {
    private static final double[] DEFAULT_BUCKETS = {0.1, 0.2, 0.5, 1.0, 2, 3, 5, 10, 20, 30, 50};

    private static Histogram commitProcessingTime = Histogram
            .build("th2_infra_mgr_commit_processing_time", "Time it took to process changes in a commit")
            .buckets(DEFAULT_BUCKETS)
            .register();


    public static Histogram.Timer getCommitTimer() {
        return commitProcessingTime.startTimer();
    }
}
