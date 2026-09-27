package com.toptal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:partition-tests;DB_CLOSE_DELAY=-1")
@ActiveProfiles("partition")
class PartitionTests {
    @Autowired JobOperatorTestUtils jobs;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path temp;

    @Test
    void eachFileHasItsOwnWorkerAndAllCustomersAreWrittenExactlyOnce() throws Exception {
        Path first = temp.resolve("first.csv");
        Path second = temp.resolve("second.csv");
        for (int part = 0; part < 2; part++) {
            Files.writeString(part == 0 ? first : second, "id,name,birthday,transactions\n" +
                    IntStream.rangeClosed(part * 50 + 1, part * 50 + 50)
                            .mapToObj(i -> i + ",Customer " + i + ",1990-09-12,2\n")
                            .collect(Collectors.joining()));
        }
        var result = jobs.startJob(new JobParametersBuilder()
                .addString("inputFiles", first + ";" + second)
                .addString("reportDate", "2026-09-07").toJobParameters());
        assertThat(result.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_report", Long.class)).isEqualTo(100);
        var workers = result.getStepExecutions().stream()
                .filter(s -> s.getStepName().startsWith("customerWorker:")).toList();
        assertThat(workers).hasSize(2);
        assertThat(workers).allSatisfy(s -> {
            assertThat(s.getStatus()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(s.getWriteCount()).isEqualTo(50);
        });
        assertThat(workers.stream().map(s -> s.getExecutionContext().getString("fileName")))
                .containsExactlyInAnyOrder(first.toString(), second.toString());
    }
}
