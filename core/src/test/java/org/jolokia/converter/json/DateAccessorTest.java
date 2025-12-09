package org.jolokia.converter.json;

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
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedList;
import java.util.TimeZone;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.json.DateAccessor;
import org.jolokia.core.service.serializer.SerializeOptions;
import org.jolokia.core.service.serializer.ValueFaultHandler;
import org.jolokia.core.util.DateUtil;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 18.04.11
 */
public class DateAccessorTest {

    private DateAccessor extractor;
    private org.jolokia.converter.json.ObjectToJsonConverter converter;


    @BeforeMethod
    public void setup() {
        extractor = new DateAccessor(new org.jolokia.converter.json.DateFormatConfiguration());

        // Needed for subclassing final object
        converter = new org.jolokia.converter.json.ObjectToJsonConverter(null, null, null);
        converter.setupContext(
            new SerializeOptions.Builder()
                .faultHandler(ValueFaultHandler.THROWING_VALUE_FAULT_HANDLER)
                .build());
    }

    @Test
    public void type() {
        assertEquals(extractor.getType(),Date.class);
    }

    @Test
    public void canSetValue() {
        assertTrue(extractor.canSetValue());
    }

    @Test
    public void directExtract() throws AttributeNotFoundException {
        Date date = new Date();
        Deque<String> stack = new LinkedList<>();
        Object result = extractor.extractObject(null,date,stack,false);
        assertEquals(result,date);
        stack.add("time");
        result = extractor.extractObject(null,date,stack,false);
        assertEquals(result,date);
    }

    @Test
    public void simpleJsonExtract() throws AttributeNotFoundException {
        Date date = new Date();
        Deque<String> stack = new LinkedList<>();
        Object result = extractor.extractObject(null,date,stack,true);
        assertEquals(result, DateUtil.toISO8601WithMilliseconds(date));
        stack.add("time");
        result = extractor.extractObject(null,date,stack,true);
        assertEquals(result,date.getTime());
    }

    @Test
    public void customFormatExtract() throws AttributeNotFoundException {
        extractor = new DateAccessor(new org.jolokia.converter.json.DateFormatConfiguration("yyyyMMddHHmmssSSS", TimeZone.getDefault()));
        Date date = new Date(1231231231231L);
        Deque<String> stack = new LinkedList<>();
        Object result = extractor.extractObject(null,date,stack,true);
        assertEquals(result, DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(1231231231231L)));
        stack.add("time");
        result = extractor.extractObject(null,date,stack,true);
        assertEquals(result,date.getTime());
    }

    // Disabled until Mockin fixed (seems that the mock is still active in future, unrelated tests
    @Test(enabled = false, expectedExceptions = AttributeNotFoundException.class)
    public void simpleJsonExtractWithWrongPath() throws AttributeNotFoundException {
        Date date = new Date();
        Deque<String> stack = new LinkedList<>();
        stack.add("blablub");

        extractor.extractObject(converter, date, stack, true);
    }

    @Test
    public void timeSet() throws InvocationTargetException, IllegalAccessException {
        Date date = new Date();
        long currentTime = date.getTime();
        Object oldVal = extractor.setObjectValue(null,date,"time",0L);
        assertEquals(oldVal,currentTime);
        assertEquals(date.getTime(),0L);

        oldVal = extractor.setObjectValue(null,date,"time","1000");
        assertEquals(oldVal,0L);
        assertEquals(date.getTime(),1000L);
    }

    @Test
    public void iso8601Set() throws InvocationTargetException, IllegalAccessException {
        Date date = new Date();
        String current = DateUtil.toISO8601(date);
        Object oldVal = extractor.setObjectValue(null,date,"iso8601",DateUtil.toISO8601(new Date(0)));
        assertEquals(oldVal,current);
        assertEquals(date.getTime(),0L);
    }

    @Test
    public void formatSet() throws InvocationTargetException, IllegalAccessException {
        {
            extractor = new DateAccessor(new org.jolokia.converter.json.DateFormatConfiguration("yyyyMMddHHmmssSSS", TimeZone.getTimeZone("UTC")));
            Date date = new Date();
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmssSSS");
            // this is the same as the one used by the extractor
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            String current = format.format(date);
            Object oldVal = extractor.setObjectValue(null, date, "format", "20420101010203456");
            assertEquals(oldVal, current);
            assertEquals(date.getTime(), 2272150923456L);
        }
        {
            extractor = new DateAccessor(new org.jolokia.converter.json.DateFormatConfiguration("yyyyMMddHHmmssSSS", TimeZone.getTimeZone("CET")));
            Date date = new Date();
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmssSSS");
            // this is the same as the one used by the extractor
            format.setTimeZone(TimeZone.getTimeZone("CET"));
            String current = format.format(date);
            Object oldVal = extractor.setObjectValue(null, date, "format", "20420101010203456");
            assertEquals(oldVal, current);
            assertEquals(date.getTime(), 2272147323456L);
        }
    }

    @Test(expectedExceptions = { UnsupportedOperationException.class })
    public void invalidSet() throws InvocationTargetException, IllegalAccessException {
        Object oldVal = extractor.setObjectValue(null,new Date(),"blubbla",0L);
    }
}
