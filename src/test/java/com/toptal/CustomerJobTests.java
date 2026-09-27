package com.toptal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.*;

@SpringBatchTest
@SpringBootTest
@Import(CustomerJobTests.FaultConfiguration.class)
class CustomerJobTests {
    @Autowired JobOperatorTestUtils jobs;
    @Autowired JobRepositoryTestUtils repositoryTests;
    @Autowired JobOperator operator;
    @Autowired JobRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired FaultWriter faults;
    @TempDir Path temp;

    @BeforeEach
    void reset() {
        repositoryTests.removeJobExecutions();
        jdbc.update("DELETE FROM customer_report");
        faults.calls.set(0);
        faults.transientFailures = 0;
        faults.failAfterWritingId = -1;
    }

    JobParameters parameters(Path input) {
        return new JobParametersBuilder()
                .addString("inputFile", input.toAbsolutePath().toString())
                .addString("reportDate", "2026-09-07").toJobParameters();
    }

    Path customers(int count) throws Exception {
        Path input = temp.resolve("customers.csv");
        Files.writeString(input, "id,name,birthday,transactions\n" +
                IntStream.rangeClosed(1, count)
                        .mapToObj(i -> i + ",Customer " + i + ",1990-09-12,2\n")
                        .collect(Collectors.joining()));
        return input;
    }

    public StepExecution getStepExecution() {
        return org.springframework.batch.test.MetaDataInstanceFactory.createStepExecution();
    }

    @Test
    void filtersBusinessExclusionsAndSkipsInvalidRecords() throws Exception {
        var result = jobs.startJob(parameters(Path.of("input/customers.csv")));
        assertThat(result.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        var step = result.getStepExecutions().stream()
                .filter(s -> s.getStepName().equals("importCustomers")).findFirst().orElseThrow();
        assertThat(step.getReadCount()).isEqualTo(5);
        assertThat(step.getFilterCount()).isEqualTo(2);
        assertThat(step.getReadSkipCount()).isEqualTo(1);
        assertThat(step.getProcessSkipCount()).isEqualTo(1);
        assertThat(step.getWriteCount()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT customer_id FROM customer_report ORDER BY customer_id", Long.class))
                .containsExactly(1L, 4L);
        assertThat(repository.getJobExecution(result.getId()).getExecutionContext()
                .getString("inputFile")).endsWith("customers.csv");
    }

    @Test
    void rejectsASecondCompletedInstance() throws Exception {
        var parameters = parameters(customers(2));
        jobs.startJob(parameters);
        assertThatThrownBy(() -> jobs.startJob(parameters))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class);
    }

    @Test
    void rollsBackTheFailedChunkAndRestartsFromTheLastCommit() throws Exception {
        var parameters = parameters(customers(4));
        faults.failAfterWritingId = 3;
        var failed = jobs.startJob(parameters);
        assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(jdbc.queryForList("SELECT customer_id FROM customer_report ORDER BY customer_id", Long.class))
                .containsExactly(1L, 2L);
        faults.failAfterWritingId = -1;
        var restarted = operator.restart(failed);
        assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(restarted.getJobInstance().getId()).isEqualTo(failed.getJobInstance().getId());
        assertThat(restarted.getId()).isNotEqualTo(failed.getId());
        var restartedStep = restarted.getStepExecutions().stream()
                .filter(s -> s.getStepName().equals("importCustomers")).findFirst().orElseThrow();
        assertThat(restartedStep.getReadCount()).isEqualTo(2);
        assertThat(restartedStep.getWriteCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_report", Long.class)).isEqualTo(4);
        assertThat(restarted.getStepExecutions()).noneMatch(s -> s.getStepName().equals("prepareInput"));
    }

    @Test
    void upsertsWithoutDuplicatesWhenANewInstanceReprocessesTheSameInput() throws Exception {
        var original = parameters(customers(4));
        jobs.startJob(original);
        var revised = new JobParametersBuilder(original).addLong("revision", 2L).toJobParameters();
        assertThat(jobs.startJob(revised).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_report", Long.class)).isEqualTo(4);
    }

    @Test
    void retriesTransientWritesAndThenCompletes() throws Exception {
        faults.transientFailures = 2;
        var result = jobs.startJob(parameters(customers(2)));
        assertThat(result.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(faults.calls.get()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_report", Long.class)).isEqualTo(2);
    }

    @Test
    void failsAfterThreeRetriesWithoutSkippingInfrastructureFailure() throws Exception {
        faults.transientFailures = 100;
        var result = jobs.startJob(parameters(customers(2)));
        assertThat(result.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(faults.calls.get()).isEqualTo(4);
        assertThat(result.getStepExecutions()).allSatisfy(step -> assertThat(step.getSkipCount()).isZero());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_report", Long.class)).isZero();
    }

    @Test
    void failsWhenTheSkipLimitIsExceeded() throws Exception {
        Path input = temp.resolve("invalid.csv");
        Files.writeString(input, "id,name,birthday,transactions\n" +
                IntStream.rangeClosed(1, 11).mapToObj(i -> i + ",Bad,1990-09-12,-1\n")
                        .collect(Collectors.joining()));
        assertThat(jobs.startJob(parameters(input)).getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_report", Long.class)).isZero();
    }

    @Test
    void testsTheImportStepWithItsRequiredJobContext() throws Exception {
        Path input = customers(2);
        var context = new ExecutionContext();
        context.putString("inputFile", input.toString());
        var result = jobs.startStep("importCustomers", parameters(input), context);
        assertThat(result.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_report", Long.class)).isEqualTo(2);
    }

    @Test
    void missingInputFailsBeforeAnyCustomerIsWritten() throws Exception {
        var result = jobs.startJob(parameters(temp.resolve("missing.csv")));
        assertThat(result.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_report", Long.class)).isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FaultConfiguration {
        @Bean
        @Primary
        FaultWriter faultWriter(@Qualifier("customerWriter") JdbcBatchItemWriter<Customer> delegate) {
            return new FaultWriter(delegate);
        }
    }

    static class FaultWriter extends JdbcBatchItemWriter<Customer> {
        final JdbcBatchItemWriter<Customer> delegate;
        final AtomicInteger calls = new AtomicInteger();
        volatile int transientFailures;
        volatile long failAfterWritingId = -1;

        FaultWriter(JdbcBatchItemWriter<Customer> delegate) { this.delegate = delegate; }

        @Override
        public void afterPropertiesSet() { }

        @Override
        public void write(Chunk<? extends Customer> chunk) throws Exception {
            if (chunk.isEmpty()) { return; }
            int call = calls.incrementAndGet();
            if (call <= transientFailures) {
                throw new TransientDataAccessResourceException("Injected temporary outage");
            }
            delegate.write(chunk);
            if (chunk.getItems().stream().anyMatch(c -> c.id() == failAfterWritingId)) {
                throw new DataAccessResourceFailureException("Injected failure after SQL write");
            }
        }
    }
}
