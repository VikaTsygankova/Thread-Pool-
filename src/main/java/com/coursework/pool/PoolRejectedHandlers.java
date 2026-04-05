package com.coursework.pool;

import java.util.concurrent.RejectedExecutionException;

/**
 * Стандартные политики отказа (по смыслу аналоги {@link java.util.concurrent.ThreadPoolExecutor}).
 */
public final class PoolRejectedHandlers {

    private PoolRejectedHandlers() {
    }

    /**
     * Выбрасывает {@link RejectedExecutionException} — предсказуемо для вызывающего, но теряется задача без выполнения.
     */
    public static PoolRejectedHandler abort() {
        return (task, pool) -> {
            throw new RejectedExecutionException("Task rejected from " + pool.getPoolName());
        };
    }

    /**
     * Выполняет задачу в потоке, который вызвал {@code execute} — снижает нагрузку на пул, но может блокировать продюсера
     * (риск дедлока, если вызывать из потока самого пула).
     */
    public static PoolRejectedHandler callerRuns() {
        return (task, pool) -> task.run();
    }
}
