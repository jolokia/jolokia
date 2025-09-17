package org.jolokia.service.serializer.json;

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

import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedList;
import java.util.TimeZone;
import javax.management.AttributeNotFoundException;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.jolokia.server.core.service.serializer.ValueFaultHandler;
import org.jolokia.server.core.util.DateUtil;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 18.04.11
 */
public class CalendarAccessorTest {

    private ObjectAccessor objectAccessor;

    @BeforeMethod
    public void setup() {
        objectAccessor = new CalendarAccessor(new DateFormatConfiguration());

        // Needed for subclassing final object
        ObjectToJsonConverter converter = new ObjectToJsonConverter(null, null);
        converter.setupContext(
            new SerializeOptions.Builder()
                .faultHandler(ValueFaultHandler.THROWING_VALUE_FAULT_HANDLER)
                .build());
    }

    @Test
    public void type() {
        assertEquals(objectAccessor.getType(), Calendar.class);
    }

    @Test
    public void canSetValue() {
        assertTrue(objectAccessor.canSetValue());
    }

    @Test
    public void directExtract() throws AttributeNotFoundException {
        Calendar c = Calendar.getInstance();
        Deque<String> stack = new LinkedList<>();
        Object result = objectAccessor.extractObject(null, c, stack, false);
        assertEquals(result, c);
        stack.add("time");
        result = objectAccessor.extractObject(null, c, stack, false);
        assertEquals(result, c);
    }

    @Test
    public void customFormatExtract() throws AttributeNotFoundException {
        Calendar cal = Calendar.getInstance();

        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        ObjectAccessor objectAccessor = new CalendarAccessor(new DateFormatConfiguration("yyyy-MM-dd HH:mm:ss (XXX)", TimeZone.getTimeZone("Europe/Warsaw")));
        ObjectAccessor objectAccessor2 = new CalendarAccessor(new DateFormatConfiguration("yyyy-MM-dd HH:mm:ss (XXX)", TimeZone.getTimeZone("US/Eastern")));
        // we want this calendar instance to represent afternoon at US East Coast
        cal.setTimeZone(TimeZone.getTimeZone("US/Eastern"));
        cal.set(2024, Calendar.JULY, 23, 15, 26, 28);

        Deque<String> stack = new LinkedList<>();

        // so it should be already past 9PM in Central Europe (6 hours span)
        assertEquals(objectAccessor.extractObject(null, cal, stack, true), "2024-07-23 21:26:28 (+02:00)");
        assertEquals(objectAccessor2.extractObject(null, cal, stack, true), "2024-07-23 15:26:28 (-04:00)");

        // in early November, Europe turns off DST, but US/Eastern stays at UTC-4
        cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getTimeZone("US/Eastern"));
        cal.set(2024, Calendar.NOVEMBER, 2, 15, 26, 28);
        assertEquals(objectAccessor.extractObject(null, cal, stack, true), "2024-11-02 20:26:28 (+01:00)");
        assertEquals(objectAccessor2.extractObject(null, cal, stack, true), "2024-11-02 15:26:28 (-04:00)");

        // in late November, EST moves to UTC-5
        cal = Calendar.getInstance();
        cal.set(2024, Calendar.NOVEMBER, 23, 15, 26, 28);
        cal.setTimeZone(TimeZone.getTimeZone("US/Eastern"));
        assertEquals(objectAccessor.extractObject(null, cal, stack, true), "2024-11-23 21:26:28 (+01:00)");
        assertEquals(objectAccessor2.extractObject(null, cal, stack, true), "2024-11-23 15:26:28 (-05:00)");

        stack.add("time");
        assertEquals(objectAccessor.extractObject(null, cal, stack, true), cal.getTime().getTime());
    }

    @Test
    public void timeSet() throws InvocationTargetException, IllegalAccessException {
        Date date = new Date();
        Calendar cal = Calendar.getInstance();
        long currentTime = cal.getTime().getTime();
        Object oldVal = objectAccessor.setObjectValue(null, cal, "time", 0L);
        assertEquals(oldVal, currentTime);
        assertEquals(cal.getTime().getTime(), 0L);

        oldVal = objectAccessor.setObjectValue(null, cal, "time", "1000");
        assertEquals(oldVal, 0L);
        assertEquals(cal.getTime().getTime(), 1000L);
    }

    @Test
    public void iso8601Set() throws InvocationTargetException, IllegalAccessException {
        Date date = new Date();
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        String current = DateUtil.toISO8601(date);
        Object oldVal = objectAccessor.setObjectValue(null, cal, "iso8601", DateUtil.toISO8601(new Date(0)));
        assertEquals(oldVal, current);
        assertEquals(cal.getTime().getTime(), 0L);
    }

    @Test
    public void formatSet() throws InvocationTargetException, IllegalAccessException {
        {
            objectAccessor = new DateAccessor(new DateFormatConfiguration("yyyyMMddHHmmssSSS", TimeZone.getTimeZone("UTC")));
            Date date = new Date();
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmssSSS");
            // this is the same as the one used by the extractor
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            String current = format.format(date);
            Object oldVal = objectAccessor.setObjectValue(null, date, "format", "20420101010203456");
            assertEquals(oldVal, current);
            assertEquals(date.getTime(), 2272150923456L);
        }
        {
            objectAccessor = new DateAccessor(new DateFormatConfiguration("yyyyMMddHHmmssSSS", TimeZone.getTimeZone("CET")));
            Date date = new Date();
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmssSSS");
            // this is the same as the one used by the extractor
            format.setTimeZone(TimeZone.getTimeZone("CET"));
            String current = format.format(date);
            Object oldVal = objectAccessor.setObjectValue(null, date, "format", "20420101010203456");
            assertEquals(oldVal, current);
            assertEquals(date.getTime(), 2272147323456L);
        }
    }

    @Test(expectedExceptions = {UnsupportedOperationException.class})
    public void invalidSet() throws InvocationTargetException, IllegalAccessException {
        objectAccessor.setObjectValue(null, Calendar.getInstance(), "blubbla", 0L);
    }

}
