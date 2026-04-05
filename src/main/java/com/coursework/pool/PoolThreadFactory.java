package com.coursework.pool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Имена вида {@code MyPool-worker-N}, логирование создания потока.
 */
public final class PoolThreadFactory implements ThreadFactory {

    private final String namePrefix;
    private final AtomicInteger seq = new AtomicInteger(1);
    private final PoolLogger log;

    public PoolThreadFactory(String poolName, PoolLogger log) {
        this.namePrefix = poolName + "-worker-";
        this.log = log;
    }

    @Override
    public Thread newThread(Runnable r) {
        String name = namePrefix + seq.getAndIncrement();
        log.threadFactoryCreating(name);
        Thread t = new Thread(r, name);
        t.setDaemon(false);
        return t;
    }
}
