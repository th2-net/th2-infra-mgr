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

package com.exactpro.th2.inframgr.util;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RetryableTaskQueue {

    private class SingleTask implements Runnable {
        RetryableTask retryableTask;
        private SingleTask(RetryableTask retryableTask) {
            this.retryableTask = retryableTask;
        }

        @Override
        public void run() {
            try {
                retryableTask.run();
                completeTask(retryableTask);
            } catch (Exception e) {
                RetryableTaskQueue.this.addTask(this, true);
            }
        }
    }

    private final Set<String> tasks;
    private final ScheduledExecutorService taskScheduler;


    public RetryableTaskQueue(int threads) {
        tasks = new HashSet<>();
        taskScheduler = new ScheduledThreadPoolExecutor(threads);
    }


    private synchronized void addTask(SingleTask task, boolean startDelayed) {
        tasks.add(task.retryableTask.getUniqueKey());
        taskScheduler.schedule(task, startDelayed ? task.retryableTask.getRetryDelay() : 0, TimeUnit.SECONDS);
    }


    public synchronized void add(RetryableTask retryableTask) {
        add(retryableTask, false);
    }

    public synchronized void add(RetryableTask retryableTask, boolean startDelayed) {
        if (!tasks.contains(retryableTask.getUniqueKey()))
            addTask(new SingleTask(retryableTask), startDelayed);
    }


    private synchronized void completeTask(RetryableTask retryableTask) throws IllegalStateException {
        String taskKey = retryableTask.getUniqueKey();
        if (!tasks.contains(taskKey))
            throw new IllegalStateException("Task \"" + taskKey + "\" was not found in active task list");

        tasks.remove(taskKey);
    }

    public void shutdown() {
        taskScheduler.shutdown();
    }
}
