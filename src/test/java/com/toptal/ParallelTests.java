package com.toptal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:parallel-tests;DB_CLOSE_DELAY=-1")
@ActiveProfiles("parallel")
@Import(ParallelTests.ProbeConfiguration.class)
class ParallelTests {
    @Autowired JobOperatorTestUtils jobs;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProbedProcessor probe;
    @TempDir Path temp;

    @Test
    void processesItemsOnMultipleWorkersWithoutChangingTheResult() throws Exception {
        Path input = temp.resolve("parallel.csv");
        Files.writeString(input, "id,name,birthday,transactions\n" +
                IntStream.rangeClosed(1, 100)
                        .mapToObj(i -> i + ",Customer " + i + ",1990-09-12,2\n")
                        .collect(Collectors.joining()));
        var result = jobs.startJob(new JobParametersBuilder()
                .addString("inputFile", input.toString())
                .addString("reportDate", "2026-09-07").toJobParameters());
        assertThat(result.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_report", Long.class)).isEqualTo(100);
        assertThat(probe.threads.size()).isGreaterThanOrEqualTo(2);
        assertThat(probe.threads).allMatch(name -> name.startsWith("batch-worker-"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        @Primary
        ProbedProcessor probedProcessor(
                @Qualifier("customerProcessor") CompositeItemProcessor<Customer, Customer> delegate) {
            return new ProbedProcessor(delegate);
        }
    }

    static class ProbedProcessor extends CompositeItemProcessor<Customer, Customer> {
        final Set<String> threads = ConcurrentHashMap.newKeySet();
        final CountDownLatch twoWorkers = new CountDownLatch(2);
        final CompositeItemProcessor<Customer, Customer> delegate;

        ProbedProcessor(CompositeItemProcessor<Customer, Customer> delegate) { this.delegate = delegate; }

        @Override
        public void afterPropertiesSet() { }

        @Override
        public Customer process(Customer customer) throws Exception {
            threads.add(Thread.currentThread().getName());
            twoWorkers.countDown();
            if (!twoWorkers.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Expected concurrent item processing");
            }
            return delegate.process(customer);
        }
    }
}
