package com.coursework.pool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Пул с несколькими {@link BlockingQueue} (по одной на слот до {@code maxPoolSize}),
 * распределение задач Round-Robin, поддержка {@code minSpareThreads} и усечения числа
 * потоков сверх {@code corePoolSize} по простою (для слотов с индексом &gt;= {@code corePoolSize}).
 */
public class CustomThreadPool implements CustomExecutor {

    private final String poolName;
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final java.util.concurrent.TimeUnit timeUnit;
    private final int minSpareThreads;

    private final BlockingQueue<Runnable>[] queues;
    private final AtomicInteger roundRobin = new AtomicInteger();

    private final PoolLogger log;
    private final PoolThreadFactory threadFactory;
    private final PoolRejectedHandler rejectedHandler;

    private final Thread[] workerThreads;
    private final ReentrantLock mainLock = new ReentrantLock();
    private final AtomicInteger workerCount = new AtomicInteger();
    private final AtomicInteger idleCount = new AtomicInteger();

    private volatile boolean shutdown;
    private volatile boolean shutdownNow;

    @SuppressWarnings("unchecked")
    public CustomThreadPool(
            String poolName,
            int corePoolSize,
            int maxPoolSize,
            long keepAliveTime,
            java.util.concurrent.TimeUnit timeUnit,
            int queueSize,
            int minSpareThreads,
            PoolRejectedHandler rejectedHandler) {
        if (corePoolSize < 0 || maxPoolSize < 1 || corePoolSize > maxPoolSize) {
            throw new IllegalArgumentException("corePoolSize/maxPoolSize invalid");
        }
        if (queueSize < maxPoolSize) {
            throw new IllegalArgumentException("queueSize must be >= maxPoolSize (ёмкость каждой из очередей не меньше 1)");
        }
        if (minSpareThreads < 0) {
            throw new IllegalArgumentException("minSpareThreads >= 0");
        }
        this.poolName = poolName;
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.minSpareThreads = minSpareThreads;
        this.rejectedHandler = rejectedHandler != null ? rejectedHandler : PoolRejectedHandlers.abort();
        this.log = new PoolLogger(poolName);
        this.threadFactory = new PoolThreadFactory(poolName, log);
        this.queues = new BlockingQueue[maxPoolSize];
        this.workerThreads = new Thread[maxPoolSize];
        int[] caps = distributeQueueCapacities(queueSize, maxPoolSize);
        for (int i = 0; i < maxPoolSize; i++) {
            queues[i] = new LinkedBlockingQueue<>(caps[i]);
        }
        for (int i = 0; i < corePoolSize; i++) {
            startWorker(i);
        }
        ensureMinSpareWorkers();
    }

    /**
     * Распределение суммарного лимита {@code queueSize} по очередям (в сумме даёт queueSize).
     */
    static int[] distributeQueueCapacities(int totalSize, int n) {
        int[] caps = new int[n];
        int base = totalSize / n;
        int rem = totalSize % n;
        for (int i = 0; i < n; i++) {
            caps[i] = base + (i < rem ? 1 : 0);
        }
        return caps;
    }

    public String getPoolName() {
        return poolName;
    }

    private void startWorker(final int queueId) {
        mainLock.lock();
        try {
            if (workerThreads[queueId] != null) {
                return;
            }
            if (workerCount.get() >= maxPoolSize) {
                return;
            }
            Runnable task = new WorkerTask(queueId);
            Thread t = threadFactory.newThread(task);
            workerThreads[queueId] = t;
            workerCount.incrementAndGet();
            t.start();
        } finally {
            mainLock.unlock();
        }
    }

    private int findFreeSlot() {
        for (int i = 0; i < maxPoolSize; i++) {
            if (workerThreads[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private void ensureWorkerForQueue(int queueId) {
        mainLock.lock();
        try {
            if (workerThreads[queueId] == null && workerCount.get() < maxPoolSize) {
                startWorker(queueId);
            }
        } finally {
            mainLock.unlock();
        }
    }

    private void ensureMinSpareWorkers() {
        mainLock.lock();
        try {
            while (idleCount.get() < minSpareThreads && workerCount.get() < maxPoolSize) {
                int idx = findFreeSlot();
                if (idx < 0) {
                    break;
                }
                startWorker(idx);
            }
        } finally {
            mainLock.unlock();
        }
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        if (shutdown || shutdownNow) {
            throw new RejectedExecutionException("Pool is shut down");
        }
        int idx = Math.floorMod(roundRobin.getAndIncrement(), maxPoolSize);
        boolean offered = queues[idx].offer(command);
        if (!offered) {
            log.rejected(String.valueOf(command));
            try {
                rejectedHandler.rejected(command, this);
            } catch (RuntimeException ex) {
                throw ex;
            }
            return;
        }
        log.poolTaskAccepted(idx, String.valueOf(command));
        ensureWorkerForQueue(idx);
        ensureMinSpareWorkers();
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        if (callable == null) {
            throw new NullPointerException("callable");
        }
        FutureTask<T> ft = new FutureTask<>(callable);
        execute(ft);
        return ft;
    }

    @Override
    public void shutdown() {
        mainLock.lock();
        try {
            shutdown = true;
            log.poolShutdown("shutdown requested - no new tasks, finishing queues");
        } finally {
            mainLock.unlock();
        }
    }

    @Override
    public void shutdownNow() {
        mainLock.lock();
        try {
            shutdownNow = true;
            shutdown = true;
            for (int i = 0; i < maxPoolSize; i++) {
                queues[i].clear();
                Thread t = workerThreads[i];
                if (t != null) {
                    t.interrupt();
                }
            }
            log.poolShutdown("shutdownNow - queues cleared, workers interrupted");
        } finally {
            mainLock.unlock();
        }
    }

    public boolean isShutdown() {
        return shutdown || shutdownNow;
    }

    /**
     * Ожидает завершения всех рабочих потоков после {@link #shutdown()} или {@link #shutdownNow()}.
     *
     * @return {@code true}, если к моменту таймаута счётчик воркеров обнулен
     */
    public boolean awaitTermination(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        long deadline = System.currentTimeMillis() + unit.toMillis(timeout);
        while (System.currentTimeMillis() < deadline) {
            if (workerCount.get() == 0) {
                return true;
            }
            Thread[] snapshot;
            mainLock.lock();
            try {
                snapshot = workerThreads.clone();
            } finally {
                mainLock.unlock();
            }
            for (Thread t : snapshot) {
                if (t != null && t.isAlive()) {
                    long wait = deadline - System.currentTimeMillis();
                    if (wait <= 0) {
                        return workerCount.get() == 0;
                    }
                    t.join(wait);
                }
            }
            if (workerCount.get() == 0) {
                return true;
            }
            Thread.sleep(10);
        }
        return workerCount.get() == 0;
    }

    private final class WorkerTask implements Runnable {

        private final int queueId;

        WorkerTask(int queueId) {
            this.queueId = queueId;
        }

        private boolean isExtraSlot() {
            return queueId >= corePoolSize;
        }

        @Override
        public void run() {
            final String threadName = Thread.currentThread().getName();
            boolean idleShrink = false;
            try {
                BlockingQueue<Runnable> q = queues[queueId];
                while (!shutdownNow) {
                    if (shutdown && q.isEmpty()) {
                        break;
                    }
                    idleCount.incrementAndGet();
                    Runnable task;
                    try {
                        task = q.poll(keepAliveTime, timeUnit);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        if (shutdownNow) {
                            break;
                        }
                        continue;
                    } finally {
                        idleCount.decrementAndGet();
                    }

                    if (task == null) {
                        mainLock.lock();
                        try {
                            if (shutdownNow) {
                                break;
                            }
                            if (shutdown && q.isEmpty()) {
                                break;
                            }
                            if (isExtraSlot() && workerCount.get() > corePoolSize) {
                                idleShrink = true;
                                break;
                            }
                        } finally {
                            mainLock.unlock();
                        }
                        continue;
                    }

                    if (shutdownNow) {
                        break;
                    }

                    mainLock.lock();
                    try {
                        if (shutdownNow) {
                            break;
                        }
                    } finally {
                        mainLock.unlock();
                    }

                    log.workerExecutes(threadName, String.valueOf(task));
                    try {
                        task.run();
                    } catch (RuntimeException | Error e) {
                        e.printStackTrace();
                    }
                }
            } finally {
                if (idleShrink) {
                    log.workerIdleTimeoutStopping(threadName);
                }
                mainLock.lock();
                try {
                    if (workerThreads[queueId] == Thread.currentThread()) {
                        workerThreads[queueId] = null;
                        workerCount.decrementAndGet();
                    }
                } finally {
                    mainLock.unlock();
                }
                log.workerTerminated(threadName);
            }
        }
    }
}
