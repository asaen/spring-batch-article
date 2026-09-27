package com.toptal;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:flow-tests;DB_CLOSE_DELAY=-1")
@ActiveProfiles("flow")
class ConditionalFlowTests {
    @Autowired JobOperatorTestUtils jobs;
    @Autowired JobRepositoryTestUtils repositoryTests;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path temp;

    @BeforeEach
    void reset() {
        repositoryTests.removeJobExecutions();
        jdbc.update("DELETE FROM customer_report");
    }

    @Test
    void routesSkippedRecordsToReview() throws Exception {
        var result = jobs.startJob(new JobParametersBuilder()
                .addString("inputFile", Path.of("input/customers.csv").toAbsolutePath().toString())
                .addString("reportDate", "2026-09-07").toJobParameters());
        assertThat(result.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(result.getStepExecutions()).anyMatch(s -> s.getStepName().equals("reviewRejects"));
        assertThat(result.getExecutionContext().getString("reviewRequired")).isEqualTo("true");
    }

    @Test
    void cleanInputGoesDirectlyToTheSummary() throws Exception {
        Path input = temp.resolve("clean.csv");
        Files.writeString(input, "id,name,birthday,transactions\n1,Ada,1990-09-12,2\n");
        var result = jobs.startJob(new JobParametersBuilder()
                .addString("inputFile", input.toString())
                .addString("reportDate", "2026-09-07").toJobParameters());
        assertThat(result.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(result.getStepExecutions()).noneMatch(s -> s.getStepName().equals("reviewRejects"));
        assertThat(result.getStepExecutions()).anyMatch(s -> s.getStepName().equals("reportSummary"));
    }

    @Test
    void unexpectedFailuresCannotTakeTheSuccessBranch() throws Exception {
        var result = jobs.startJob(new JobParametersBuilder()
                .addString("inputFile", temp.resolve("missing.csv").toString())
                .addString("reportDate", "2026-09-07").toJobParameters());
        assertThat(result.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(result.getStepExecutions()).noneMatch(s -> s.getStepName().equals("reportSummary"));
    }
}
