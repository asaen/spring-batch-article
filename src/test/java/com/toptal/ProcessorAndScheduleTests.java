package com.toptal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import static org.assertj.core.api.Assertions.*;

class ProcessorAndScheduleTests {
    @Test
    void birthdayFilteringUsesTheReportMonthIncludingLeapDayBirthdays() throws Exception {
        var processor = new BirthdayFilterProcessor(LocalDate.of(2025, 2, 1));
        var leapDayCustomer = new Customer(1, "Ada", LocalDate.of(2000, 2, 29), 2);
        assertThat(processor.process(leapDayCustomer)).isEqualTo(leapDayCustomer);
        assertThat(processor.process(new Customer(2, "Ben", LocalDate.of(2000, 3, 1), 1))).isNull();
    }

    @Test
    void fiveTransactionsIsExcludedAndFourIsIncluded() throws Exception {
        var processor = new TransactionValidatingProcessor(5);
        assertThat(processor.process(new Customer(1, "Ada", LocalDate.of(1990, 9, 1), 4))).isNotNull();
        assertThat(processor.process(new Customer(1, "Ada", LocalDate.of(1990, 9, 1), 5))).isNull();
    }

    @Test
    void invalidRecordsAreRejectedEvenWhenTheirBirthdayDoesNotMatch() {
        var processor = new BirthdayFilterProcessor(LocalDate.of(2026, 9, 1));
        assertThatThrownBy(() -> processor.process(new Customer(1, "Ada", LocalDate.of(1990, 1, 1), -1)))
                .isInstanceOf(InvalidCustomerException.class);
    }

    @Test
    void scheduleHasStableDailyIdentityAndRunsAtTwoAm() {
        var day = LocalDate.of(2026, 9, 7);
        var keys = new org.springframework.batch.core.job.DefaultJobKeyGenerator();
        assertThat(keys.generateKey(ReportSchedule.parametersFor(day)))
                .isEqualTo(keys.generateKey(ReportSchedule.parametersFor(day)));
        assertThat(keys.generateKey(ReportSchedule.parametersFor(day)))
                .isNotEqualTo(keys.generateKey(ReportSchedule.parametersFor(day.plusDays(1))));
        assertThat(CronExpression.parse("0 0 2 * * *").next(LocalDateTime.of(2026, 9, 7, 2, 0)))
                .isEqualTo(LocalDateTime.of(2026, 9, 8, 2, 0));
    }
}
