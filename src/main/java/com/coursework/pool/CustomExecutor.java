package com.coursework.pool;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * Интерфейс кастомного пула: выполнение задач и корректное завершение.
 */
public interface CustomExecutor extends Executor {

    @Override
    void execute(Runnable command);

    <T> Future<T> submit(Callable<T> callable);

    void shutdown();

    void shutdownNow();
}
