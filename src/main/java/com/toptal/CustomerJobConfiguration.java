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

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import javax.sql.DataSource;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.DefaultJobParametersValidator;
import org.springframework.batch.core.listener.ExecutionContextPromotionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Job configuration.
 *
 * @author Alexey Saenko (alexey.saenko@gmail.com)
 */
@Configuration(proxyBeanMethods = false)
public class CustomerJobConfiguration {
    private static final Logger log = LoggerFactory.getLogger(CustomerJobConfiguration.class);

    @Bean
    @Profile("!flow & !partition & !parallel")
    public Job customerReportJob(JobRepository repository, Step prepareInput,
            Step importCustomers, Step reportSummary) {
        return new JobBuilder("customerReportJob", repository)
                .validator(new DefaultJobParametersValidator(
                        new String[]{"inputFile", "reportDate"}, new String[]{"revision"}))
                .start(prepareInput)
                .next(importCustomers)
                .next(reportSummary)
                .build();
    }

    @Bean
    public Step prepareInput(JobRepository repository, PlatformTransactionManager tx) {
        var promotion = new ExecutionContextPromotionListener();
        promotion.setKeys(new String[]{"inputFile"});
        promotion.setStrict(true);
        return new StepBuilder("prepareInput", repository)
                .tasklet((contribution, context) -> {
                    var step = contribution.getStepExecution();
                    String input = step.getJobParameters().getString("inputFile");
                    var resource = new FileSystemResource(input);
                    if (!resource.exists() || !resource.isReadable()) {
                        throw new IllegalArgumentException("Cannot read input file: " + input);
                    }
                    step.getExecutionContext().putString("inputFile", input);
                    return RepeatStatus.FINISHED;
                }, tx)
                .listener(promotion)
                .build();
    }

    @Bean
    public Step importCustomers(JobRepository repository, PlatformTransactionManager tx,
            FlatFileItemReader<Customer> customerReader,
            CompositeItemProcessor<Customer, Customer> customerProcessor,
            JdbcBatchItemWriter<Customer> customerWriter, ObservationRegistry observations) {
        return new StepBuilder("importCustomers", repository)
                .<Customer, Customer>chunk(2)
                .transactionManager(tx)
                .reader(customerReader)
                .processor(customerProcessor)
                .writer(customerWriter)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .skip(FlatFileParseException.class, InvalidCustomerException.class)
                .skipLimit(10)
                .skipListener(new BatchListeners.RejectLogger())
                .listener(new BatchListeners.StepSummary())
                .observationRegistry(observations)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<Customer> customerReader(
            @Value("#{jobExecutionContext['inputFile']}") String inputFile) {
        return readerFor(inputFile);
    }

    public static FlatFileItemReader<Customer> readerFor(String inputFile) {
        return new FlatFileItemReaderBuilder<Customer>()
                .name("customerReader")
                .resource(new FileSystemResource(inputFile))
                .strict(true)
                .linesToSkip(1)
                .delimited()
                .names("id", "name", "birthday", "transactions")
                .fieldSetMapper(fields -> new Customer(
                        fields.readLong("id"), fields.readString("name"),
                        LocalDate.parse(fields.readString("birthday")),
                        fields.readInt("transactions")))
                .build();
    }

    @Bean
    @StepScope
    public CompositeItemProcessor<Customer, Customer> customerProcessor(
            @Value("#{jobParameters['reportDate']}") String reportDate) {
        var processor = new CompositeItemProcessor<Customer, Customer>();
        processor.setDelegates(List.of(
                new BirthdayFilterProcessor(LocalDate.parse(reportDate)),
                new TransactionValidatingProcessor(5)));
        return processor;
    }

    @Bean
    @StepScope
    public JdbcBatchItemWriter<Customer> customerWriter(DataSource dataSource,
            @Value("#{jobParameters['reportDate']}") String reportDate) {
        return new JdbcBatchItemWriterBuilder<Customer>()
                .dataSource(dataSource)
                .sql("""
                    MERGE INTO customer_report
                      (report_date, customer_id, customer_name, birthday, transactions)
                    KEY (report_date, customer_id) VALUES (?, ?, ?, ?, ?)
                    """)
                .itemPreparedStatementSetter((customer, statement) -> {
                    statement.setDate(1, Date.valueOf(reportDate));
                    statement.setLong(2, customer.id());
                    statement.setString(3, customer.name());
                    statement.setDate(4, Date.valueOf(customer.birthday()));
                    statement.setInt(5, customer.transactions());
                })
                .build();
    }

    @Bean
    public Step reportSummary(JobRepository repository, PlatformTransactionManager tx,
            JdbcTemplate jdbc) {
        return new StepBuilder("reportSummary", repository)
                .tasklet((contribution, context) -> {
                    String date = contribution.getStepExecution()
                            .getJobParameters().getString("reportDate");
                    Long total = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM customer_report WHERE report_date = ?",
                            Long.class, Date.valueOf(date));
                    log.info("Report {} has {} customers", date, total);
                    return RepeatStatus.FINISHED;
                }, tx)
                .build();
    }
}
