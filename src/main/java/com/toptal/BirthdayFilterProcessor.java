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
import org.springframework.batch.infrastructure.item.ItemProcessor;

/**
 * Processor filters customers by being born in the month of the report date.
 *
 * @author Alexey Saenko (alexey.saenko@gmail.com)
 */
public class BirthdayFilterProcessor implements ItemProcessor<Customer, Customer> {
    private final LocalDate reportDate;

    public BirthdayFilterProcessor(LocalDate reportDate) {
        this.reportDate = reportDate;
    }

    @Override
    public Customer process(Customer customer) {
        if (customer.id() <= 0 || customer.name().isBlank()
                || customer.transactions() < 0) {
            throw new InvalidCustomerException("Invalid customer: " + customer.id());
        }
        return customer.birthday().getMonth() == reportDate.getMonth()
                ? customer : null;
    }
}
