/*
 * MIT License
 *
 * Copyright (c) 2017 Alexey Saenko
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

import java.beans.XMLEncoder;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.Collection;
import java.util.GregorianCalendar;
import java.util.LinkedList;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the application.
 *
 * @author Alexey Saenko (alexey.saenko@gmail.com)
 */
@Slf4j
@EnableScheduling
@EnableBatchProcessing
@SpringBootApplication
public class BatchApplication {

    public static void main(String[] args) {
        prepareTestData(1000);
        SpringApplication.run(BatchApplication.class, args);
    }

    private static void prepareTestData(final int amount) {
        final int actualYear = new GregorianCalendar().get(Calendar.YEAR);
        final Collection<Customer> customers = new LinkedList<>();
        for (int i = 1; i <= amount; i++) {
            final Calendar birthday = new GregorianCalendar();
            birthday.set(Calendar.YEAR, random(actualYear - 100, actualYear));
            birthday.set(Calendar.DAY_OF_YEAR, random(1, birthday.getActualMaximum(Calendar.DAY_OF_YEAR)));
            final Customer customer = new Customer();
            customer.setId(i);
            customer.setName(UUID.randomUUID().toString().replaceAll("[^a-z]", ""));
            customer.setBirthday(birthday);
            customer.setTransactions(random(0, 100));
            customers.add(customer);
        }
        try (final XMLEncoder encoder = new XMLEncoder(new FileOutputStream(CustomerReportJobConfig.XML_FILE))) {
            encoder.writeObject(customers);
        } catch (final FileNotFoundException e) {
            log.error(e.getMessage(), e);
            System.exit(-1);
        }
    }

    private static int random(final int start, final int end) {
        return start + (int) Math.round(Math.random() * (end - start));
    }
}
