package org.jolokia.util;

/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.lang.reflect.Field;
import java.util.Date;
import java.util.TimeZone;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 19.04.11
 */
public class DateUtilTest {

    @Test
    public void conversion() {
        runTests();
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

    // ====================================================

    private void runTests() {

        Date testDate = new Date(1303195711000L);

        // Check date formatting
        assertEquals(DateUtil.toISO8601(testDate, TimeZone.getTimeZone("Europe/Berlin")),
                     "2011-04-19T08:48:31+02:00");
        assertEquals(DateUtil.toISO8601(testDate, TimeZone.getTimeZone("Europe/London")),
                     "2011-04-19T07:48:31+01:00");

        // Check date parsing
        String[] dateStrings = {
                "2011-04-19T08:48:31+02:00",
                "2011-04-19T06:48:31+00:00",
                "2011-04-19T06:48:31Z",
                "2011-04-19T07:18:31+00:30",
        };
        for (String toParse : dateStrings) {
            Date date =  DateUtil.fromISO8601(toParse);
            assertEquals(date, testDate);
        }
        
        checkRoundTripConsideringIgnoredMilliseconds();
    }

	/**
	 * Milliseconds are not part of ISO8601
	 */
	private void checkRoundTripConsideringIgnoredMilliseconds() {
		Date currentDate = new Date(1303195711111L);
		Date expectedDateIsWithoutMilliSeconds = new Date();
		expectedDateIsWithoutMilliSeconds.setTime((currentDate.getTime() / 1000) * 1000);
		Date roundtripValue = DateUtil.fromISO8601(DateUtil.toISO8601(currentDate));
		assertEquals(roundtripValue, expectedDateIsWithoutMilliSeconds);
	}
}
