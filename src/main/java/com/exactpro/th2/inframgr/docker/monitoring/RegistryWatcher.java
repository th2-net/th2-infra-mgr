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

package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.docker.DynamicResourcesCache;
import com.exactpro.th2.inframgr.docker.RegistryConnection;
import com.exactpro.th2.infrarepo.GitterContext;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class RegistryWatcher implements Runnable {
    private static final int THREAD_POOL_SIZE_SCHEDULER = 3;
    private static final DynamicResourcesCache DYNAMIC_RESOURCES_CACHE = DynamicResourcesCache.INSTANCE;

    private final long initialDelay;
    private final long repeatPeriod;

    private final ScheduledExecutorService taskScheduler;
    private final RegistryConnection connection;
    private GitterContext ctx;

    public RegistryWatcher(long initialDelay, long repeatPeriod, RegistryConnection connection) {
        this.taskScheduler = new ScheduledThreadPoolExecutor(THREAD_POOL_SIZE_SCHEDULER);
        this.initialDelay = initialDelay;
        this.repeatPeriod = repeatPeriod;
        this.connection = connection;
    }

    public void startWatchingRegistry() throws IOException {
        Config config = Config.getInstance();
        Config.GitConfig gitConfig = config.getGit();
        ctx = GitterContext.getContext(gitConfig);

        taskScheduler.scheduleAtFixedRate(
                this,
                initialDelay,
                repeatPeriod,
                TimeUnit.SECONDS
        );
    }

    @Override
    public void run() {
        for (String schema : DYNAMIC_RESOURCES_CACHE.getSchemas()) {
            new Thread(new SchemaJob(
                    DYNAMIC_RESOURCES_CACHE.getDynamicResourcesCopy(schema),
                    connection,
                    ctx.getGitter(schema),
                    schema
            )).start();

        }
    }

    public void shutdown() {
        taskScheduler.shutdown();
    }

}
