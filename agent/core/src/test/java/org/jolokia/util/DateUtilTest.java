/*
 * Copyright 2009-2011 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.util;

import java.lang.reflect.Field;
import java.util.Date;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 19.04.11
 */
public class DateUtilTest {

    static Object[] testData = {
            "2011-04-19T08:48:31+02:00",new Date(1303195711000L),
            "2011-04-19T10:48:31+00:00",new Date(1303195711000L),
            "2011-04-19T10:48:31Z",new Date(1303195711000L),
            "2011-04-19T10:18:31+00:30",new Date(1303195711000L)
    };

    @Test
    public void conversion() {
        runTests();
    }

    private void runTests() {
        for (int i = 0; i < testData.length; i += 2) {
            assertEquals(DateUtil.toISO8601((Date) testData[1]),testData[0]);
            assertEquals(DateUtil.fromISO8601((String) testData[0]),testData[1]);
        }
    }

    @Test(expectedExceptions = {IllegalArgumentException.class})
    public void illegalFormat() {
        DateUtil.fromISO8601("Bla");
    }

    @Test(expectedExceptions = {IllegalArgumentException.class})
    public void illegalFormat2() throws NoSuchFieldException, IllegalAccessException {
        Object oldValue = exchangeDataTypeFactory();
        DateUtil.fromISO8601("Bla");
        resetDataTypeFactory(oldValue);
    }

    @Test
    public void conversionWithOwnAlgo() throws NoSuchFieldException, IllegalAccessException {
        Object oldValue = exchangeDataTypeFactory();
        runTests();
        resetDataTypeFactory(oldValue);
    }

    private void resetDataTypeFactory(Object pOldValue) throws NoSuchFieldException, IllegalAccessException {
        Field field = DateUtil.class.getDeclaredField("datatypeFactory");
        field.setAccessible(true);
        field.set(null, pOldValue);
    }

    private Object exchangeDataTypeFactory() throws IllegalAccessException, NoSuchFieldException {
        Field field = DateUtil.class.getDeclaredField("datatypeFactory");
        field.setAccessible(true);
        Object oldValue = field.get(null);
        field.set(null,null);
        return oldValue;
    }
}
