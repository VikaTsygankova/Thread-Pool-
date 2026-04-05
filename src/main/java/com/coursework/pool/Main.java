package com.coursework.pool;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Демонстрация: параметры пула, имитационные задачи, {@link CustomThreadPool#shutdown()},
 * сценарий перегрузки и отказа в приёме задач.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== Демо кастомного пула потоков (MyPool) ===\n");

        int corePoolSize = 2;
        int maxPoolSize = 4;
        int queueSize = 5;
        long keepAliveSeconds = 5;
        int minSpareThreads = 1;

        CustomThreadPool pool = new CustomThreadPool(
                "MyPool",
                corePoolSize,
                maxPoolSize,
                keepAliveSeconds,
                TimeUnit.SECONDS,
                queueSize,
                minSpareThreads,
                PoolRejectedHandlers.abort()
        );

        AtomicInteger taskSeq = new AtomicInteger();

        System.out.println("--- Нормальная нагрузка: несколько задач с sleep ---");
        pool.execute(new DemoTask(taskSeq, "A", 300));
        pool.execute(new DemoTask(taskSeq, "B", 300));
        pool.execute(new DemoTask(taskSeq, "C", 200));
        Thread.sleep(1200);

        System.out.println("\n--- Перегрузка: много задач, очереди заполняются, часть задач отклоняется (AbortPolicy) ---");
        for (int i = 0; i < 30; i++) {
            try {
                pool.execute(new DemoTask(taskSeq, "load-" + i, 50));
            } catch (Exception e) {
                System.out.println("  submit failed: " + e.getMessage());
            }
        }
        Thread.sleep(2000);

        System.out.println("\n--- shutdown(): новые задачи не принимаются, очереди отрабатываются ---");
        pool.shutdown();
        try {
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    System.out.println("не должно выполниться");
                }

                @Override
                public String toString() {
                    return "task:illegal-after-shutdown";
                }
            });
        } catch (Exception e) {
            System.out.println("  ожидаемо отклонено после shutdown: " + e.getClass().getSimpleName());
        }
        pool.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("\n--- Новый пул для shutdownNow() (прерывание) ---");
        CustomThreadPool pool2 = new CustomThreadPool(
                "MyPool2",
                1,
                2,
                1,
                TimeUnit.SECONDS,
                3,
                0,
                PoolRejectedHandlers.abort()
        );
        pool2.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("  долгая задача прервана shutdownNow");
                }
            }

            @Override
            public String toString() {
                return "task:long-sleep";
            }
        });
        Thread.sleep(200);
        pool2.shutdownNow();
        pool2.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("\n=== Демо завершено ===");
    }

    /**
     * Задача с понятным {@link #toString()} для логов пула (вместо {@code Lambda@...}).
     */
    private static final class DemoTask implements Runnable {

        private final AtomicInteger seq;
        private final String label;
        private final long sleepMs;

        DemoTask(AtomicInteger seq, String label, long sleepMs) {
            this.seq = seq;
            this.label = label;
            this.sleepMs = sleepMs;
        }

        @Override
        public void run() {
            seq.incrementAndGet();
            String t = Thread.currentThread().getName();
            System.out.println("  [Task " + label + "] start on " + t);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("  [Task " + label + "] interrupted");
                return;
            }
            System.out.println("  [Task " + label + "] end on " + t);
        }

        @Override
        public String toString() {
            return "task:" + label;
        }
    }
}
