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
import java.util.Date;
import java.util.Stack;

import javax.management.AttributeNotFoundException;

import org.easymock.EasyMock;
import org.jolokia.util.DateUtil;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 18.04.11
 */
public class DateExtractorTest {

    private DateExtractor extractor;
    private ObjectToJsonConverter converter;


    @BeforeMethod
    public void setup() {
        extractor = new DateExtractor();

        // Needed for subclassing final object
        converter = new ObjectToJsonConverter(null);
        converter.setupContext(new JsonConvertOptions.Builder().faultHandler(ValueFaultHandler.THROWING_VALUE_FAULT_HANDLER).build());
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
        Stack stack = new Stack();
        Object result = extractor.extractObject(null,date,stack,false);
        assertEquals(result,date);
        stack.add("time");
        result = extractor.extractObject(null,date,stack,false);
        assertEquals(result,date);
    }

    @Test
    public void simpleJsonExtract() throws AttributeNotFoundException {
        Date date = new Date();
        Stack stack = new Stack();
        Object result = extractor.extractObject(null,date,stack,true);
        assertEquals(result, DateUtil.toISO8601(date));
        stack.add("time");
        result = extractor.extractObject(null,date,stack,true);
        assertEquals(result,date.getTime());
    }

    @Test(enabled = true, expectedExceptions = AttributeNotFoundException.class)
    public void simpleJsonExtractWithWrongPath() throws AttributeNotFoundException {
        Date date = new Date();
        Stack stack = new Stack();
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

    @Test(expectedExceptions = { UnsupportedOperationException.class })
    public void invalidSet() throws InvocationTargetException, IllegalAccessException {
        Object oldVal = extractor.setObjectValue(null,new Date(),"blubbla",0L);
    }
}
