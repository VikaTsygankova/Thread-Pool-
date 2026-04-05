# Задание 2 — кастомный пул потоков (`pj-threadpool`)

## Состав

- `CustomExecutor` / `CustomThreadPool` — реализация пула с параметрами `corePoolSize`, `maxPoolSize`, `keepAliveTime`, `queueSize`, `minSpareThreads`.
- Несколько очередей `BlockingQueue` (по одной на слот до `maxPoolSize`), распределение задач **Round-Robin**.
- `PoolThreadFactory` — имена `ИмяПула-worker-N`, лог при создании потока.
- Логи: приём в очередь, выполнение, отказ, idle timeout, завершение потока.
- `PoolRejectedHandlers` — **abort** (исключение) и **callerRuns** (выполнить в вызывающем потоке).
- `Main` — демонстрационная программа (вместо unit-тестов по условию).

## Как проверить

1. Установите **JDK** (с `javac`), **Maven** в `PATH`.
2. В каталоге `pj-threadpool`:

```bash
mvn clean package
java -jar target/pj-threadpool-1.0-SNAPSHOT.jar
```

Или без JAR:

```bash
mvn -q compile exec:java -Dexec.mainClass=com.coursework.pool.Main
```

(если не настроен `exec-maven-plugin`, проще:)

```bash
mvn -q compile
java -cp target/classes com.coursework.pool.Main
```

3. В консоли должны появиться логи `[Pool]`, `[Worker]`, `[ThreadFactory]`, при перегрузке — `[Rejected]` и исключения `RejectedExecutionException` из политики abort.

## Отчёт

См. файл `Отчет_задание2_ThreadPool.html` (можно открыть в браузере и сохранить как PDF).

## Ограничения и обоснование политики отказа

Выбрана политика **abort**: при переполнении очередей задача не выполняется, вызывающий получает `RejectedExecutionException`. Плюсы: предсказуемость и быстрый отказ под нагрузкой. Минусы: потеря задачи без очереди на повтор. Альтернатива **callerRuns** снижает давление на пул, но может блокировать продюсера (и при вызове из потока пула — риск дедлока).
