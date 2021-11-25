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

package com.exactpro.th2.inframgr;

import com.exactpro.th2.inframgr.k8s.K8sSynchronizationJobQueue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class K8sSynchronizationQueueTests {

    @Test
    void testOrder() {
        K8sSynchronizationJobQueue queue = new K8sSynchronizationJobQueue();
        K8sSynchronizationJobQueue.Job job;

        queue.addJob(new K8sSynchronizationJobQueue.Job("schema1"));
        queue.addJob(new K8sSynchronizationJobQueue.Job("schema2"));
        queue.addJob(new K8sSynchronizationJobQueue.Job("schema1"));

        job = queue.takeJob();
        assertTrue(job.getSchema().equals("schema1"));

        job = queue.takeJob();
        assertTrue(job.getSchema().equals("schema2"));

        job = queue.takeJob();
        assertTrue(job == null);
    }

    @Test
    void testLoop1() {
        K8sSynchronizationJobQueue queue = new K8sSynchronizationJobQueue();

        queue.addJob(new K8sSynchronizationJobQueue.Job("schema1"));

        K8sSynchronizationJobQueue.Job job1 = queue.takeJob();
        assertTrue(job1.getSchema().equals("schema1"));

        queue.addJob(new K8sSynchronizationJobQueue.Job("schema1"));

        K8sSynchronizationJobQueue.Job job2 = queue.takeJob();
        assertTrue(job2 == null);

        queue.completeJob(job1);

        K8sSynchronizationJobQueue.Job job3 = queue.takeJob();
        assertTrue(job3.getSchema().equals("schema1"));
    }

    @Test
    void testLoop2() {
        K8sSynchronizationJobQueue queue = new K8sSynchronizationJobQueue();

        queue.addJob(new K8sSynchronizationJobQueue.Job("schema1"));

        K8sSynchronizationJobQueue.Job job1 = queue.takeJob();
        assertTrue(job1.getSchema().equals("schema1"));

        queue.addJob(new K8sSynchronizationJobQueue.Job("schema1"));
        queue.addJob(new K8sSynchronizationJobQueue.Job("schema2"));

        K8sSynchronizationJobQueue.Job job2 = queue.takeJob();
        assertTrue(job2.getSchema().equals("schema2"));

        queue.addJob(new K8sSynchronizationJobQueue.Job("schema2"));

        K8sSynchronizationJobQueue.Job job3 = queue.takeJob();
        assertTrue(job3 == null);

        queue.completeJob(job2);

        K8sSynchronizationJobQueue.Job job4 = queue.takeJob();
        assertTrue(job4.getSchema().equals("schema2"));
        queue.completeJob(job4);

        K8sSynchronizationJobQueue.Job job5 = queue.takeJob();
        assertTrue(job5 == null);

        queue.completeJob(job1);

        K8sSynchronizationJobQueue.Job job6 = queue.takeJob();
        assertTrue(job6.getSchema().equals("schema1"));
        queue.completeJob(job6);

        K8sSynchronizationJobQueue.Job job7 = queue.takeJob();
        assertTrue(job7 == null);

    }
}
