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

import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Daily schedule of the customer report job.
 *
 * @author Alexey Saenko (alexey.saenko@gmail.com)
 */
@Configuration(proxyBeanMethods = false)
@Profile("scheduled")
@EnableScheduling
public class ReportSchedule {
    private static final Logger log = LoggerFactory.getLogger(ReportSchedule.class);
    private final JobOperator operator;
    private final Job job;

    public ReportSchedule(JobOperator operator, Job customerReportJob) {
        this.operator = operator;
        this.job = customerReportJob;
    }

    public static JobParameters parametersFor(LocalDate date) {
        return new JobParametersBuilder()
                .addString("reportDate", date.toString())
                .addString("inputFile", "input/customers-" + date + ".csv")
                .toJobParameters();
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void runDailyReport() throws Exception {
        try {
            operator.start(job, parametersFor(LocalDate.now(ZoneOffset.UTC)));
        } catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException ex) {
            log.info("Daily report already complete or running");
        }
    }
}
