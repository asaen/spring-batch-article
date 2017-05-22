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

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.batch.test.StepScopeTestExecutionListener;
import org.springframework.batch.test.StepScopeTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

/**
 * Tests for {@link BirthdayFilterProcessor}.
 *
 * @author Alexey Saenko (alexey.saenko@gmail.com)
 */
@Slf4j
@RunWith(SpringRunner.class)
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class, StepScopeTestExecutionListener.class})
@ContextConfiguration(classes = {BatchApplication.class, BatchTestConfiguration.class})
public class BirthdayFilterProcessorTest {

    @Autowired
    private BirthdayFilterProcessor processor;

    public StepExecution getStepExecution() {
        log.info("getting step execution");
        return MetaDataInstanceFactory.createStepExecution();
    }

    @Test
    public void filter() throws Exception {
        final Customer customer = new Customer();
        customer.setId(1);
        customer.setName("name");
        customer.setBirthday(new GregorianCalendar());
        Assert.assertNotNull(processor.process(customer));
    }

    @Test
    public void filterId() throws Exception {
        final Customer customer = new Customer();
        customer.setId(1);
        customer.setName("name");
        customer.setBirthday(new GregorianCalendar());
        final int id = StepScopeTestUtils.doInStepScope(
            getStepExecution(),
            () -> processor.process(customer).getId()
        );
        Assert.assertEquals(1, id);
    }

}
