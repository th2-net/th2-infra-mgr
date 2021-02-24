package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.docker.DynamicResourcesCache;
import com.exactpro.th2.infrarepo.GitterContext;

import java.io.IOException;
import java.util.concurrent.*;

public class RegistryWatcher implements Runnable {
    private static final int THREAD_POOL_SIZE_SCHEDULER = 3;
    private static final DynamicResourcesCache DYNAMIC_RESOURCES_CACHE = DynamicResourcesCache.INSTANCE;

    private long initialDelay;
    private long repeatPeriod;

    private ScheduledExecutorService taskScheduler;

    private GitterContext ctx;

    public RegistryWatcher(long repeatPeriod) {
        this(0, repeatPeriod);
    }

    public RegistryWatcher(long initialDelay, long repeatPeriod) {
        this.taskScheduler = new ScheduledThreadPoolExecutor(THREAD_POOL_SIZE_SCHEDULER);
        this.initialDelay = initialDelay;
        this.repeatPeriod = repeatPeriod;
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
        for(String schema: DYNAMIC_RESOURCES_CACHE.getSchemas()){
            new SchemaJob(DYNAMIC_RESOURCES_CACHE.getDynamicResources(schema), ctx.getGitter(schema)).start();
        }
    }

    public void shutdown() {
        taskScheduler.shutdown();
    }

}
