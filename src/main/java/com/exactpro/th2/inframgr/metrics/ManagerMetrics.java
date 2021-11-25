/*
 * Copyright 2020-2021 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
