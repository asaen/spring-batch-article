# spring-batch-article

Runnable companion to **Spring Batch Tutorial: A Practical Guide to Scalable Batch Processing**. Built on Spring Batch 6.0.4.

## Requirements and Baseline

- Java 21.
- Maven 3.9.x. Verification used Maven 3.9.11.
- Spring Boot 4.0.7, which manages Spring Batch 6.0.4 and JUnit Jupiter 6.0.3.
- Internet access for the first Maven build.

Run commands from this directory. Every command below fits on one line and works in PowerShell or a POSIX shell with Java and Maven on the path.

## Build and Run

```shell
mvn clean verify
java -jar target/spring-batch-article-0.0.1-SNAPSHOT.jar inputFile=input/customers.csv reportDate=2026-09-07
```

The CSV is deliberately imperfect. For September, customers 1 and 4 qualify, customers 2 and 3 are filtered, customer 5 has invalid business data, and CSV line 7 cannot be parsed. Expected import counters are `read=5`, `filtered=2`, `written=2`, and `skipped=2` (one read skip and one process skip). Header lines are excluded. A malformed input line is not included in the successful read count.

The default application has three steps: validate and promote the input path, import customers, and log the output count. The chunk size of two makes transaction boundaries visible in a tiny fixture. It is a teaching choice, not a recommended production tuning value.

The default H2 database is file-backed at `data/customer-batch`. Both output and batch metadata survive application exits. A second identical command is rejected because the job instance has already completed. A different `reportDate` creates a different instance. To intentionally reprocess a completed report, add `revision=2`; the writer upserts by `(report_date, customer_id)` to prevent duplicate rows. This does not remove rows that no longer qualify in a changed source, so it is not full snapshot replacement.

To inspect the output without an H2 console server, find the H2 JAR downloaded by Maven and use its shell after the application has exited:

```shell
java -cp PATH_TO_H2_JAR org.h2.tools.Shell -url jdbc:h2:file:./data/customer-batch -user sa -sql "SELECT * FROM customer_report ORDER BY report_date, customer_id"
```

Replace `PATH_TO_H2_JAR` with the actual path in your Maven cache. This inspection command is optional.

## Alternative Examples

Select only one of `flow`, `parallel`, and `partition` per run. Each is an alternative definition of the same logical job. Give each variant a distinct database while experimenting so an earlier variant's metadata cannot be mistaken for its state.

**Conditional flow:** A successful import with skipped records sets `reviewRequired=true` in the job context before the summary step. This is a demonstration flag, not a notification system or a reject-record store.

```shell
java -jar target/spring-batch-article-0.0.1-SNAPSHOT.jar --spring.profiles.active=flow --spring.datasource.url=jdbc:h2:file:./data/flow-demo inputFile=input/customers.csv reportDate=2026-09-07
```

**Parallel item processing:** Uses a clean fixture and four workers. Reading and writing remain serial in the Spring Batch 6 chunk model; processors run concurrently. The queue holds 20 tasks, matching this example's chunk size. Retune both together. This focused scaling variant fails on invalid input; it does not inherit the main job's retry and skip policy.

```shell
java -jar target/spring-batch-article-0.0.1-SNAPSHOT.jar --spring.profiles.active=parallel --spring.datasource.url=jdbc:h2:file:./data/parallel-demo inputFile=input/customers-clean.csv reportDate=2026-09-07
```

**Local partitioning:** Assigns one already-split CSV file to each worker, with independent reader state. The example list is semicolon-separated, so filenames must not contain semicolons. Quote the argument so the shell does not treat the semicolon as a command separator; a comma cannot be used because Spring Batch reads it as the job parameter type separator. The sample has two files; the bounded executor is sized for that demonstration. A production partitioner needs bounded submission or a queue sized for its submitted work. `gridSize` is a hint passed to the partitioner, not a guarantee that an arbitrary partitioner returns that many partitions.

```shell
java -jar target/spring-batch-article-0.0.1-SNAPSHOT.jar --spring.profiles.active=partition --spring.datasource.url=jdbc:h2:file:./data/partition-demo "inputFiles=input/customers-north.csv;input/customers-south.csv" reportDate=2026-09-07
```

**Scheduling:** The `scheduled` profile keeps the process running and disables Boot's startup launch. At 02:00 UTC daily, it looks for `input/customers-YYYY-MM-DD.csv` for that UTC date. Files must be ready and immutable before the trigger. Stop the process with Ctrl+C when finished.

```shell
java -jar target/spring-batch-article-0.0.1-SNAPSHOT.jar --spring.profiles.active=scheduled
```

The scheduler catches an already-running or already-completed instance; other errors remain visible. The cron expression and identity generation are tested without waiting for a clock trigger. ShedLock is discussed in the article as Alexey's production experience and is not an installed dependency of this single-process example. H2 file mode is for local use; multiple application nodes need a shared server database and appropriate coordination.

## Tests

The suite covers:

- Business filtering, malformed CSV, invalid business data, and exact output/counters.
- Input promotion between steps, an isolated step test, and a scoped reader test.
- A completed instance being rejected on a second launch.
- A failure after SQL writes, rollback of the entire uncommitted chunk, and restart reading only the remaining records.
- Reprocessing under a new instance without duplicate output rows.
- Recovery after two transient writer failures and failure after three retries.
- Failure at the eleventh invalid item with a skip limit of ten.
- Missing input, all conditional-flow branches, concurrent processors, and file partitioning.
- Transaction-count boundaries, leap-day birthdays under month-based selection, and stable daily scheduling identity.

Fault injection exists only in test code. The production example contains no deliberate outage switch. Tests use isolated in-memory databases; separate packaged-process checks verify file-backed persistence. These are functional tests, not load tests or validation of remote JVM partitioning, distributed locks, hard-crash recovery, or a production database dialect.

## Adaptation Notes

`MERGE ... KEY` is H2 SQL. Replace it with the target database's upsert syntax and retain the unique business key. The primary key protects against duplicate rows, but does not provide exactly-once delivery to an external API or mail system.

Schema initialization is enabled to make the local demo self-contained. For production, manage both application and batch metadata schemas through reviewed migrations and set `spring.batch.jdbc.initialize-schema=never` and `spring.sql.init.mode=never`. Do not erase batch metadata to force reruns. Restart with the original identifying parameters and unchanged input.

The example intentionally uses Boot's JDBC batch auto-configuration. Adding `@EnableBatchProcessing` would opt out of it. In a manually configured Batch 6 application, durable JDBC infrastructure requires `@EnableJdbcJobRepository` alongside the common batch configuration.
