package com.exactpro.th2.inframgr.docker.monitoring;

import com.exactpro.th2.inframgr.Config;
import com.exactpro.th2.inframgr.docker.DynamicResource;
import com.exactpro.th2.inframgr.docker.DynamicResourcesCache;
import com.exactpro.th2.infrarepo.GitterContext;

import java.io.IOException;
import java.util.concurrent.*;

public class RegistryMonitor implements Runnable {
    private static final int THREAD_POOL_SIZE = 3;

    private long initialDelay;
    private long repeatPeriod;

    private ScheduledExecutorService taskScheduler;
    private final ExecutorService executor;

    private GitterContext ctx;

    public RegistryMonitor(long initialDelay, long repeatPeriod) {
        this.taskScheduler = new ScheduledThreadPoolExecutor(THREAD_POOL_SIZE);
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.initialDelay = initialDelay;
        this.repeatPeriod = repeatPeriod;
    }

    public RegistryMonitor(long repeatPeriod) {
        this(0, repeatPeriod);
    }


    public synchronized void start() throws IOException {
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


    public void shutdown() {
        taskScheduler.shutdown();
    }

    @Override
    public void run() {
        for(DynamicResource resource: DynamicResourcesCache.INSTANCE.getAllDynamicResources()){
            new TagUpdateJob(resource, this.executor, ctx.getGitter(resource.getSchema())).submit();
        }
    }
}
