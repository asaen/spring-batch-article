/*
 * MIT License
 *
 * Copyright (c) 2017-2026 Alexey Saenko
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.toptal;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Job configuration routing imports with skipped records to a review step.
 *
 * @author Alexey Saenko (alexey.saenko@gmail.com)
 */
@Configuration(proxyBeanMethods = false)
@Profile("flow")
public class ConditionalFlowConfiguration {
    @Bean
    public Job customerReportJob(JobRepository repository, Step prepareInput,
            Step importCustomers, Step reviewRejects, Step reportSummary) {
        return new JobBuilder("customerReportJob", repository)
                .start(prepareInput).on("COMPLETED").to(importCustomers)
                .from(prepareInput).on("*").fail()
                .from(importCustomers).on("COMPLETED WITH SKIPS").to(reviewRejects)
                .from(importCustomers).on("COMPLETED").to(reportSummary)
                .from(importCustomers).on("*").fail()
                .from(reviewRejects).on("COMPLETED").to(reportSummary)
                .from(reviewRejects).on("*").fail()
                .end().build();
    }

    @Bean
    public Step reviewRejects(JobRepository repository, PlatformTransactionManager tx) {
        return new StepBuilder("reviewRejects", repository)
                .tasklet((contribution, context) -> {
                    contribution.getStepExecution().getJobExecution()
                            .getExecutionContext().putString("reviewRequired", "true");
                    return RepeatStatus.FINISHED;
                }, tx).build();
    }
}
