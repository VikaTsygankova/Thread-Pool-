package com.coursework.pool;

/**
 * Простое логирование в консоль (формат можно менять; важны события по условию задания).
 */
public final class PoolLogger {

    private final String poolName;

    public PoolLogger(String poolName) {
        this.poolName = poolName;
    }

    public void threadFactoryCreating(String threadName) {
        System.out.println("[ThreadFactory] Creating new thread: " + threadName);
    }

    public void poolTaskAccepted(int queueId, String taskDescription) {
        System.out.println("[Pool] Task accepted into queue #" + queueId + ": " + taskDescription);
    }

    public void rejected(String taskDescription) {
        System.out.println("[Rejected] Task " + taskDescription + " was rejected due to overload!");
    }

    public void workerExecutes(String threadName, String taskDescription) {
        System.out.println("[Worker] " + threadName + " executes " + taskDescription);
    }

    public void workerIdleTimeoutStopping(String threadName) {
        System.out.println("[Worker] " + threadName + " idle timeout, stopping.");
    }

    public void workerTerminated(String threadName) {
        System.out.println("[Worker] " + threadName + " terminated.");
    }

    public void poolShutdown(String phase) {
        System.out.println("[Pool] " + poolName + " " + phase);
    }

    public String getPoolName() {
        return poolName;
    }
}
