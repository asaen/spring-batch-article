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

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Job configurations with parallel item processing and local partitioning.
 *
 * @author Alexey Saenko (alexey.saenko@gmail.com)
 */
@Configuration(proxyBeanMethods = false)
@Profile("partition | parallel")
public class ScalingConfiguration {
    @Bean
    public ThreadPoolTaskExecutor batchWorkers() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("batch-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        return executor;
    }

    @Bean
    @Profile("parallel")
    public Job customerReportJob(JobRepository repository, Step prepareInput,
            Step parallelImport, Step reportSummary) {
        return new JobBuilder("customerReportJob", repository)
                .start(prepareInput).next(parallelImport).next(reportSummary).build();
    }

    @Bean
    @Profile("parallel")
    public Step parallelImport(JobRepository repository, PlatformTransactionManager tx,
            FlatFileItemReader<Customer> customerReader,
            CompositeItemProcessor<Customer, Customer> customerProcessor,
            JdbcBatchItemWriter<Customer> customerWriter, ThreadPoolTaskExecutor batchWorkers) {
        return new StepBuilder("parallelImport", repository)
                .<Customer, Customer>chunk(20).transactionManager(tx)
                .reader(customerReader).processor(customerProcessor).writer(customerWriter)
                .taskExecutor(batchWorkers).build();
    }

    @Bean
    @Profile("partition")
    public Job customerReportJobPartitioned(JobRepository repository, Step partitionedImport,
            Step reportSummary) {
        return new JobBuilder("customerReportJob", repository)
                .start(partitionedImport).next(reportSummary).build();
    }

    @Bean
    @Profile("partition")
    @StepScope
    public Partitioner filePartitioner(@Value("#{jobParameters['inputFiles']}") String inputFiles) {
        return gridSize -> {
            Map<String, ExecutionContext> partitions = new LinkedHashMap<>();
            String[] paths = inputFiles.split(";");
            for (int i = 0; i < paths.length; i++) {
                var context = new ExecutionContext();
                context.putString("fileName", paths[i].trim());
                partitions.put("file" + i, context);
            }
            return partitions;
        };
    }

    @Bean
    @Profile("partition")
    public Step partitionedImport(JobRepository repository, Step customerWorker,
            Partitioner filePartitioner, ThreadPoolTaskExecutor batchWorkers) {
        return new StepBuilder("partitionedImport", repository)
                .partitioner("customerWorker", filePartitioner)
                .step(customerWorker).gridSize(4).taskExecutor(batchWorkers).build();
    }

    @Bean
    @Profile("partition")
    @StepScope
    public FlatFileItemReader<Customer> partitionReader(
            @Value("#{stepExecutionContext['fileName']}") String fileName) {
        return CustomerJobConfiguration.readerFor(fileName);
    }

    @Bean
    @Profile("partition")
    public Step customerWorker(JobRepository repository, PlatformTransactionManager tx,
            FlatFileItemReader<Customer> partitionReader,
            CompositeItemProcessor<Customer, Customer> customerProcessor,
            JdbcBatchItemWriter<Customer> customerWriter) {
        return new StepBuilder("customerWorker", repository)
                .<Customer, Customer>chunk(20).transactionManager(tx)
                .reader(partitionReader).processor(customerProcessor).writer(customerWriter)
                .build();
    }
}
