package com.toptal;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.batch.test.StepScopeTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBatchTest
@SpringBootTest
class ScopedReaderTests {
    @Autowired FlatFileItemReader<Customer> customerReader;

    @Test
    void readerBindsToThePromotedFileAndClosesItsStream() throws Exception {
        var step = MetaDataInstanceFactory.createStepExecution(new JobParametersBuilder()
                .addString("reportDate", "2026-09-07").toJobParameters());
        step.getJobExecution().getExecutionContext().putString("inputFile",
                Path.of("input/customers.csv").toAbsolutePath().toString());
        Long firstId = StepScopeTestUtils.doInStepScope(step, () -> {
            customerReader.open(step.getExecutionContext());
            try {
                return customerReader.read().id();
            } finally {
                customerReader.close();
            }
        });
        assertThat(firstId).isEqualTo(1L);
    }
}
