package com.coursework.pool;

/**
 * Политика при невозможности принять задачу (переполнение очередей при активном {@code maxPoolSize}).
 */
@FunctionalInterface
public interface PoolRejectedHandler {

    void rejected(Runnable task, CustomThreadPool pool);
}
