package org.jolokia.service.serializer.json;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.management.AttributeNotFoundException;

import org.jolokia.json.JSONArray;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.jolokia.server.core.service.serializer.ValueFaultHandler;
import org.jolokia.json.JSONObject;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.jolokia.service.serializer.json.simplifier.TestSimplifier;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 13.08.11
 */
public class BeanExtractorTest extends AbstractExtractorTest {

    private int number;
    private String text, writeOnly;
    private boolean flag;
    private Inner inner;
    private Nacked nacked;
    private Object nulli;

    private BeanExtractorTest self;
    private BeanExtractorTest hiddenSelf;

    @SuppressWarnings("FieldCanBeLocal")
    private Inner hiddenInner;

    @BeforeMethod
    public void setupValues() {
        number = 10;
        text = "Test";
        writeOnly = "WriteOnly";
        flag = false;
        inner = new Inner("innerValue");
        nacked = new Nacked();
        nulli = null;

        self = this;
        hiddenSelf = this;

        hiddenInner = new Inner("hiddenInnerValue");
    }

    @Test
    public void simple() throws AttributeNotFoundException {
        JSONObject res = (JSONObject) extractJson(this);
        assertEquals(res.get("number"),10);
        assertEquals(res.get("text"),"Test");
        assertFalse((Boolean) res.get("flag"));
        assertEquals( ((JSONObject) res.get("inner")).get("innerText"),"innerValue");
        assertNull(res.get("nulli"));
        assertFalse(res.containsKey("forbiddenStream"));
        assertTrue(res.containsKey("nulli"));
        assertEquals(res.get("nacked"),"nacked object");
        assertEquals(res.get("self"),"[this]");

        JSONObject inner = (JSONObject) extractJson(this,"inner");
        assertEquals(inner.get("innerText"),"innerValue");

        JSONObject innerWithWildcardPath = (JSONObject) extractJson(this,null,"innerDate");
        assertEquals(innerWithWildcardPath.size(),1);
        assertTrue((Long) ((JSONObject) innerWithWildcardPath.get("inner")).get("millis") <= new Date().getTime());

        BeanExtractorTest test = (BeanExtractorTest) extractObject(this);
        assertEquals(test,this);

        Date date = (Date) extractObject(this,"inner","innerDate");
        assertTrue(date.getTime() <= new Date().getTime());
    }

    @Test
    public void simpleWithoutTestSimplifier() throws AttributeNotFoundException, NoSuchFieldException, IllegalAccessException, ParseException {
        converter = new ObjectToJsonConverter(stringToObjectConverter, new TestJolokiaContext() {
            @Override
            public String getConfig(ConfigKey pKey) {
                if (pKey == ConfigKey.DATE_FORMAT) {
                    return "yyyy--MM--dd' | 'HH:mm:ss.SSS";
                } else if (pKey == ConfigKey.DATE_FORMAT_ZONE) {
                    return "UTC";
                }
                return super.getConfig(pKey);
            }
        });
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        sdf.setTimeZone(TimeZone.getTimeZone("CET"));
        this.getInner().setInnerDate(sdf.parse("20240722142242999"));

        converter.setupContext(new SerializeOptions.Builder().useAttributeFilter(true).build());
        Field handlersField = ObjectToJsonConverter.class.getDeclaredField("handlers");
        handlersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Extractor> handlers = (List<Extractor>) handlersField.get(converter);
        // this will switch back to default date conversion
        handlers.removeIf(h -> h instanceof TestSimplifier);

        JSONObject res = (JSONObject) extractJson(this);
        assertEquals(res.get("number"),10);
        assertEquals(res.get("text"),"Test");
        assertFalse((Boolean) res.get("flag"));
        assertEquals( ((JSONObject) res.get("inner")).get("innerText"),"innerValue");
        assertNull(res.get("nulli"));
        assertFalse(res.containsKey("forbiddenStream"));
        assertTrue(res.containsKey("nulli"));
        assertEquals(res.get("nacked"),"nacked object");
        assertEquals(res.get("self"),"[this]");

        JSONObject inner = (JSONObject) extractJson(this,"inner");
        assertEquals(inner.get("innerText"), "innerValue");
        assertEquals(inner.get("innerDate"), "2024--07--22 | 12:22:42.999");

        JSONObject innerWithWildcardPath = (JSONObject) extractJson(this,null,"innerDate");
        assertEquals(innerWithWildcardPath.size(),1);
        assertEquals(innerWithWildcardPath.get("inner"), "2024--07--22 | 12:22:42.999");

        BeanExtractorTest test = (BeanExtractorTest) extractObject(this);
        assertEquals(test,this);

        Date date = (Date) extractObject(this,"inner","innerDate");
        assertEquals(sdf.format(date), "20240722142242999");
    }

    @Test
    public void calendarsDatesAndTemporals() throws AttributeNotFoundException, NoSuchFieldException, IllegalAccessException, ParseException {
        converter = new ObjectToJsonConverter(stringToObjectConverter, new TestJolokiaContext() {
            @Override
            public String getConfig(ConfigKey pKey) {
                if (pKey == ConfigKey.DATE_FORMAT) {
                    return "yyyy--MM--dd' | 'HH:mm:ss.SSS '\"'XXX'\"'";
                } else if (pKey == ConfigKey.DATE_FORMAT_ZONE) {
                    return "CET";
                }
                return super.getConfig(pKey);
            }
        });

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        sdf.setTimeZone(TimeZone.getTimeZone("CET"));
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

        // CET Date
        this.getInner().setInnerDate(sdf.parse("2024-07-22 14:22:42.999"));
        // Calendar
        Calendar c = Calendar.getInstance();
        c.setTimeZone(TimeZone.getTimeZone("EST"));
        c.setTime(sdf.parse("2024-07-22 14:22:42.999"));
        this.getInner().setCalendar(c);

        List<Temporal> temporals = new ArrayList<>();
        // zone-less LocalDateTime
        LocalDateTime ldt = dtf.parse("2024-07-22 14:22:42.999", LocalDateTime::from);
        temporals.add(ldt);
        // zone-less Instant
        temporals.add(ldt.toInstant(ZoneOffset.ofHours(-5)));
        // zone-less LocalDate
        temporals.add(ldt.toLocalDate());
        // zone-less LocalTime
        temporals.add(ldt.toLocalTime());
        // zone-less Year
        temporals.add(Year.of(2024));
        // zone-less YearMonth
        temporals.add(YearMonth.of(2024, 7));
        // instant-less (but with Zone) OffsetTime
        temporals.add(OffsetTime.of(ldt.toLocalTime(), ZoneOffset.ofHours(-5)));
        // OffsetDateTime with Zone and ChronoFields.INSTANT_SECONDS
        temporals.add(OffsetDateTime.of(ldt, ZoneOffset.ofHours(-5)));
        // ZonedDateTime with Zone and ChronoFields.INSTANT_SECONDS
        temporals.add(ZonedDateTime.of(ldt, ZoneOffset.ofHours(-5)));

        this.getInner().setTemporals(temporals.toArray(new Temporal[0]));

        converter.setupContext(new SerializeOptions.Builder().useAttributeFilter(true).build());
        Field handlersField = ObjectToJsonConverter.class.getDeclaredField("handlers");
        handlersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Extractor> handlers = (List<Extractor>) handlersField.get(converter);
        // this will switch back to default date conversion
        handlers.removeIf(h -> h instanceof TestSimplifier);

        JSONObject inner = (JSONObject) extractJson(this,"inner");
        assertEquals(inner.get("calendar"), "2024--07--22 | 14:22:42.999 \"+02:00\"");
        assertEquals(inner.get("innerDate"), "2024--07--22 | 14:22:42.999 \"+02:00\"");

        JSONObject innerWithWildcardPath = (JSONObject) extractJson(this,null,"temporals");
        assertEquals(innerWithWildcardPath.size(), 1);
        JSONArray array = (JSONArray) innerWithWildcardPath.get("inner");
        assertEquals(array.size(), 9);
        // all temporals are formatted using a formatter with CET zone
        // 1. LocaDateTime + zone from the formatter
        assertEquals(array.get(0), "2024--07--22 | 14:22:42.999 \"+02:00\"");
        // 2. Instant from LocalDateTime interpreted at EST, formatted with CET (7 hours span)
        assertEquals(array.get(1), "2024--07--22 | 21:22:42.999 \"+02:00\"");
        // 3. LocalDate - no time, so 00:00:00, zone from the formatter
        assertEquals(array.get(2), "2024--07--22 | 00:00:00.000 \"+02:00\"");
        // 4. LocalTime - no date, so today, zone from the formatter
        assertEquals(array.get(3), DateTimeFormatter.ofPattern("yyyy--MM--dd").format(LocalDate.now())
            + " | 14:22:42.999 \"+02:00\"");
        // 5. Year - no month, day, time, zone from the formatter
        assertEquals(array.get(4), "2024--"
            + DateTimeFormatter.ofPattern("MM--dd").format(LocalDate.now())
            + " | 00:00:00.000 \"+02:00\"");
        // 6. YearMonth - no day, time, zone from the formatter
        assertEquals(array.get(5), "2024--07--"
            + DateTimeFormatter.ofPattern("dd").format(LocalDate.now())
            + " | 00:00:00.000 \"+02:00\"");
        // 7. OffsetTime with EST, formatted as CET (7 hours span)
        assertEquals(array.get(6), DateTimeFormatter.ofPattern("yyyy--MM--dd").format(LocalDate.now())
            + " | 21:22:42.999 \"+02:00\"");
        // 8. OffsetDateTime with EST, formatted as CET (7 hours span)
        assertEquals(array.get(7), "2024--07--22 | 21:22:42.999 \"+02:00\"");
        // 9. ZonedDateTime with EST, formatted as CET (7 hours span)
        assertEquals(array.get(8), "2024--07--22 | 21:22:42.999 \"+02:00\"");
    }

    @Test
    public void hiddenSelfTest() throws AttributeNotFoundException {
        String res = (String) extractJson(this,"hiddenSelf","text");
        assertEquals(res,"Test");
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void unknownMethod() throws Exception {
        extractJson(this,"blablub");
    }

    @Test
    public void simplSet() throws InvocationTargetException, IllegalAccessException {
        assertTrue(extractor.canSetValue());
        String old = (String) setObject(this,"text","NewText");
        assertEquals(old,"Test");
        assertEquals(getText(),"NewText");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*setFlag.*")
    public void invalidSet() throws InvocationTargetException, IllegalAccessException {
        setObject(this,"flag",true);
    }

    @Test
    public void writeOnly() throws InvocationTargetException, IllegalAccessException {
        assertNull(setObject(this,writeOnly,"NewWriteOnly"));
        assertEquals(writeOnly,"NewWriteOnly");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*setWrongSignature.*one parameter.*")
    public void writeWithWrongSignature() throws InvocationTargetException, IllegalAccessException {
        setObject(this,"wrongSignature","bla");
    }

    // =================================================================================

    @Override
    Extractor createExtractor() {
        return new BeanExtractor();
    }

    public int getNumber() {
        return number;
    }

    public String getText() {
        return text;
    }

    public OutputStream forbiddenStream() {
        return new ByteArrayOutputStream();
    }

    public boolean isFlag() {
        return flag;
    }

    public void setText(String pText) {
        text = pText;
    }

    public Inner getInner() {
        return inner;
    }

    public void setInner(Inner pInner) {
        inner = pInner;
    }

    public Nacked getNacked() {
        return nacked;
    }

    public Object getNulli() {
        return nulli;
    }


    public BeanExtractorTest getSelf() {
        return self;
    }

    public BeanExtractorTest hiddenSelf() {
        return hiddenSelf;
    }

    public void setWriteOnly(String pWriteOnly) {
        writeOnly = pWriteOnly;
    }

    public void setWrongSignature(String eins, String zwei) {

    }

    public static class Inner {
        private String innerText;
        private Date innerDate = new Date();
        private Calendar calendar;
        private Temporal[] temporals;

        public Inner(String pInnerValue) {
            innerText = pInnerValue;
        }

        public String getInnerText() {
            return innerText;
        }

        public Date getInnerDate() {
            return innerDate;
        }

        public void setInnerText(String pInnerText) {
            innerText = pInnerText;
        }

        public void setInnerDate(Date innerDate) {
            this.innerDate = innerDate;
        }

        public Calendar getCalendar() {
            return calendar;
        }

        public void setCalendar(Calendar calendar) {
            this.calendar = calendar;
        }

        public Temporal[] getTemporals() {
            return temporals;
        }

        public void setTemporals(Temporal[] temporals) {
            this.temporals = temporals;
        }
    }

    public static class Nacked {
        @Override
        public String toString() {
            return "nacked object";
        }
    }
}
