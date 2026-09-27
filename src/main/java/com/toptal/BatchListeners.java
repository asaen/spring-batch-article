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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;

/**
 * Listeners logging rejected records and step counters.
 *
 * @author Alexey Saenko (alexey.saenko@gmail.com)
 */
public final class BatchListeners {
    private static final Logger log = LoggerFactory.getLogger(BatchListeners.class);

    private BatchListeners() { }

    public static class RejectLogger implements SkipListener<Customer, Customer> {
        @Override
        public void onSkipInRead(Throwable error) {
            if (error instanceof FlatFileParseException parseError) {
                log.warn("Rejected CSV line {} ({})", parseError.getLineNumber(),
                        error.getClass().getSimpleName());
            }
        }

        @Override
        public void onSkipInProcess(Customer customer, Throwable error) {
            log.warn("Rejected customer {} ({})", customer.id(),
                    error.getClass().getSimpleName());
        }
    }

    public static class StepSummary implements StepExecutionListener {
        @Override
        public ExitStatus afterStep(StepExecution step) {
            log.info("Step {}: status={}, read={}, filtered={}, written={}, skipped={}",
                    step.getStepName(), step.getStatus(), step.getReadCount(),
                    step.getFilterCount(), step.getWriteCount(), step.getSkipCount());
            if (step.getExitStatus().getExitCode().equals("COMPLETED")
                    && step.getSkipCount() > 0) {
                return new ExitStatus("COMPLETED WITH SKIPS");
            }
            return null;
        }
    }
}
