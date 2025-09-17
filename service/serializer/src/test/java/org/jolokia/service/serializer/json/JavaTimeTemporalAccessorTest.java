/*
 * Copyright 2009-2024 Roland Huss
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
package org.jolokia.service.serializer.json;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.TimeZone;
import javax.management.AttributeNotFoundException;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.jolokia.server.core.service.serializer.ValueFaultHandler;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class JavaTimeTemporalAccessorTest {

    private ObjectAccessor objectAccessor;

    @BeforeMethod
    public void setup() {
        objectAccessor = new JavaTimeTemporalAccessor(new DateFormatConfiguration());

        ObjectToJsonConverter converter = new ObjectToJsonConverter(null, null);
        converter.setupContext(
            new SerializeOptions.Builder()
                .faultHandler(ValueFaultHandler.THROWING_VALUE_FAULT_HANDLER)
                .build());
    }

    @Test
    public void type() {
        assertEquals(objectAccessor.getType(), Temporal.class);
    }

    @Test
    public void canSetValue() {
        assertFalse(objectAccessor.canSetValue());
    }

    @Test
    public void directExtract() throws AttributeNotFoundException {
        Instant now = Instant.now();
        Deque<String> stack = new LinkedList<>();
        Object result = objectAccessor.extractObject(null, now, stack, false);
        assertEquals(result, now);
    }

    @Test
    public void jsonExtractOfInstant() throws Exception {
        Instant now = Instant.now();
        Deque<String> stack = new LinkedList<>();
        Object result = objectAccessor.extractObject(null, now, stack, true);
        assertTrue(result instanceof String);
        TemporalAccessor accessor = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse((String) result);
        assertEquals(Instant.from(accessor), now/*.with(ChronoField.NANO_OF_SECOND, 0L)*/);
    }

    @Test
    public void jsonExtract() throws AttributeNotFoundException {
        ObjectAccessor objectAccessor1 = new JavaTimeTemporalAccessor(new DateFormatConfiguration("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone("UTC")));
        ObjectAccessor objectAccessor2 = new JavaTimeTemporalAccessor(new DateFormatConfiguration("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone("EST")));
        ObjectAccessor objectAccessor1Z = new JavaTimeTemporalAccessor(new DateFormatConfiguration("yyyy-MM-dd HH:mm:ssXXX", TimeZone.getTimeZone("UTC")));
        ObjectAccessor objectAccessor2Z = new JavaTimeTemporalAccessor(new DateFormatConfiguration("yyyy-MM-dd HH:mm:ssXXX", TimeZone.getTimeZone("EST")));

        DateTimeFormatter dtzf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        Deque<String> stack = new LinkedList<>();

        // we care about these temporals:
        // - java.time.Instant
        // - java.time.LocalDate
        // - java.time.LocalDateTime
        // - java.time.LocalTime
        // - java.time.OffsetDateTime
        // - java.time.OffsetTime
        // - java.time.Year
        // - java.time.YearMonth
        // - java.time.ZonedDateTime

        // instants

        Object result = objectAccessor1.extractObject(null, dtzf.parse("2024-07-23 12:21:00Z", Instant::from), stack, true);
        assertEquals(result, "2024-07-23 12:21:00");
        // CET instant is two hours earlier in UTC zone during summer
        result = objectAccessor1.extractObject(null, dtzf.parse("2024-07-23 12:21:00+02:00", Instant::from), stack, true);
        assertEquals(result, "2024-07-23 10:21:00");

        // and one hour earlier during winter
        LocalDateTime november23 = dtf.parse("2024-11-23 12:21:00", LocalDateTime::from);
        result = objectAccessor1.extractObject(null, november23.toInstant(ZoneId.of("CET").getRules().getOffset(november23)), stack, true);
        assertEquals(result, "2024-11-23 11:21:00");
        result = objectAccessor2.extractObject(null, november23.toInstant(ZoneId.of("CET").getRules().getOffset(november23)), stack, true);
        assertEquals(result, "2024-11-23 06:21:00");
        result = objectAccessor1Z.extractObject(null, november23.toInstant(ZoneId.of("CET").getRules().getOffset(november23)), stack, true);
        assertEquals(result, "2024-11-23 11:21:00Z");
        result = objectAccessor2Z.extractObject(null, november23.toInstant(ZoneId.of("CET").getRules().getOffset(november23)), stack, true);
        assertEquals(result, "2024-11-23 06:21:00-05:00");

        // other temporals that contain ChronoField.INSTANT_SECONDS

        OffsetDateTime odt = OffsetDateTime.of(
            LocalDate.of(2024, 7, 23),
            LocalTime.of(13, 55, 3),
            ZoneOffset.ofHours(2)
        );
        result = objectAccessor1.extractObject(null, odt, stack, true);
        // formatter is UTC, so it's earlier than CET by 2 hours
        assertEquals(result, "2024-07-23 11:55:03");
        result = objectAccessor2.extractObject(null, odt, stack, true);
        // formatter is EST, so it's earlier than CET by 5 + 2 hours
        assertEquals(result, "2024-07-23 06:55:03");
        result = objectAccessor1Z.extractObject(null, odt, stack, true);
        assertEquals(result, "2024-07-23 11:55:03Z");
        result = objectAccessor2Z.extractObject(null, odt, stack, true);
        assertEquals(result, "2024-07-23 06:55:03-05:00");

        ZonedDateTime zdt = ZonedDateTime.of(
            LocalDate.of(2024, 7, 23),
            LocalTime.of(13, 55, 3),
            TimeZone.getTimeZone("CET").toZoneId()
        );
        result = objectAccessor1.extractObject(null, zdt, stack, true);
        // formatter is UTC, so it's earlier than CET by 2 hours
        assertEquals(result, "2024-07-23 11:55:03");
        result = objectAccessor2.extractObject(null, zdt, stack, true);
        // formatter is EST, so it's earlier than CET by 5 + 2 hours
        assertEquals(result, "2024-07-23 06:55:03");
        result = objectAccessor1Z.extractObject(null, zdt, stack, true);
        assertEquals(result, "2024-07-23 11:55:03Z");
        result = objectAccessor2Z.extractObject(null, zdt, stack, true);
        assertEquals(result, "2024-07-23 06:55:03-05:00");

        // local dates (no time, no time-zone)

        result = new JavaTimeTemporalAccessor(new DateFormatConfiguration("yyyy-MM-dd", TimeZone.getTimeZone("UTC")))
            .extractObject(null, LocalDate.of(2024, 7, 23), stack, true);
        // we need to use adjustment of the zone from the formatter used by the extractor
        if (ZoneId.of("UTC").getRules().getOffset(Instant.now()).getTotalSeconds() > 0) {
            // we're east of Greenwich, so the formatted date is actually a day before
            // this is expected, as midnight is assumed for time-less Temporals and local zone is used, while
            // formatter's zone is UTC
            assertEquals(result, "2024-07-22");
        } else {
            assertEquals(result, "2024-07-23");
        }
        result = new JavaTimeTemporalAccessor(new DateFormatConfiguration("yyyy-MM-dd", TimeZone.getDefault()))
            .extractObject(null, LocalDate.of(2024, 7, 23), stack, true);
        // the same zone is used by the extractor's formatter and extractor's default offset
        assertEquals(result, "2024-07-23");
        result = objectAccessor1.extractObject(null, LocalDate.of(2024, 7, 23), stack, true);
        assertEquals(result, df.withZone(ZoneId.of("UTC")).format(OffsetDateTime.of(
            LocalDate.of(2024, 7, 23),
            LocalTime.of(0, 0, 0, 0),
            ZoneId.of("UTC").getRules().getOffset(Instant.now())
        )));
    }

    @Test
    public void jsonExtractMillis() throws AttributeNotFoundException {
        ObjectAccessor objectAccessor1 = new JavaTimeTemporalAccessor(new DateFormatConfiguration("millis", TimeZone.getTimeZone("UTC")));
        ObjectAccessor objectAccessor2 = new JavaTimeTemporalAccessor(new DateFormatConfiguration("nanos", TimeZone.getTimeZone("UTC")));
        ObjectAccessor objectAccessor3 = new JavaTimeTemporalAccessor(new DateFormatConfiguration("nanos", TimeZone.getTimeZone("Europe/Warsaw")));

        DateTimeFormatter dtzf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSSXXX");
        Deque<String> stack = new LinkedList<>();

        Instant nowUTC = dtzf.parse("2024-07-24 08:21:04.543454341Z", Instant::from);
        Instant nowCET = dtzf.parse("2024-07-24 08:21:04.543454341+02:00", Instant::from);
        // 8AM CEST is 6AM UTC
        assertTrue(nowCET.isBefore(nowUTC));

        // instants

        Object result = objectAccessor1.extractObject(null, dtzf.parse("2024-07-24 08:21:04.543999999Z", Instant::from), stack, true);
        assertEquals(result, nowUTC.toEpochMilli());
        // CET instant is two hours earlier in UTC zone during summer
        result = objectAccessor1.extractObject(null, dtzf.parse("2024-07-24 08:21:04.543999999+02:00", Instant::from), stack, true);
        assertEquals(result, nowUTC.toEpochMilli() - (2L * 3600L * 1000L));
        result = objectAccessor2.extractObject(null, dtzf.parse("2024-07-24 08:21:04.543454341+02:00", Instant::from), stack, true);
        assertEquals(result, (nowUTC.getEpochSecond() - (2L * 3600L)) * 1_000_000_000L + nowUTC.getNano());
        result = objectAccessor3.extractObject(null, dtzf.parse("2024-07-24 08:21:04.543454341+02:00", Instant::from), stack, true);
        assertEquals(result, (nowUTC.getEpochSecond() - (2L * 3600L)) * 1_000_000_000L + nowUTC.getNano());
        result = objectAccessor3.extractObject(null, dtzf.parse("2024-07-24 08:21:04.543454341Z", Instant::from), stack, true);
        assertEquals(result, (nowUTC.getEpochSecond()) * 1_000_000_000L + nowUTC.getNano());

        // other temporals that contain ChronoField.INSTANT_SECONDS

        OffsetDateTime odt = OffsetDateTime.of(
            LocalDate.of(2024, 7, 24),
            LocalTime.of(8, 21, 4, 543454341),
            ZoneOffset.ofHours(2)
        );
        result = objectAccessor1.extractObject(null, odt, stack, true);
        assertEquals(result, nowUTC.toEpochMilli() - (2L * 3600L * 1000L));
        result = objectAccessor2.extractObject(null, odt, stack, true);
        assertEquals(result, (nowUTC.getEpochSecond() - (2L * 3600L)) * 1_000_000_000L + nowUTC.getNano());
    }

    @Test
    public void temporalTypes() {
        // these 3 have everything

        Instant instant = Instant.now();
        ZonedDateTime zonedDateTime = ZonedDateTime.now();
        OffsetDateTime offsetDateTime = OffsetDateTime.now();

        // these miss some information, so we don't know everything

        // this misses the time and zone
        LocalDate localDate = LocalDate.now();
        // this misses the zone
        LocalDateTime localDateTime = LocalDateTime.now();
        // this misses the date and zone
        LocalTime localTime = LocalTime.now();
        // this misses the date
        OffsetTime offsetTime = OffsetTime.now();
        // this misses the time, zone, month and the day
        Year year = Year.now();
        // this misses the time, zone and day
        YearMonth yearMonth = YearMonth.now();

        for (Temporal t : new Temporal[]{ instant, zonedDateTime, offsetDateTime }) {
            assertTrue(t.isSupported(ChronoField.INSTANT_SECONDS));
            if (t == instant) {
                assertFalse(t.isSupported(ChronoField.OFFSET_SECONDS));
            } else {
                assertTrue(t.isSupported(ChronoField.OFFSET_SECONDS));
            }
            assertTrue(t.isSupported(ChronoField.MILLI_OF_SECOND));
            assertTrue(t.isSupported(ChronoField.MICRO_OF_SECOND));
            assertTrue(t.isSupported(ChronoField.NANO_OF_SECOND));
        }
        for (Temporal t : new Temporal[]{ localDate, localDateTime, localTime, offsetTime, year, yearMonth }) {
            assertFalse(t.isSupported(ChronoField.INSTANT_SECONDS));
            if (t == offsetTime) {
                assertTrue(t.isSupported(ChronoField.OFFSET_SECONDS));
            } else {
                assertFalse(t.isSupported(ChronoField.INSTANT_SECONDS));
            }
        }
    }

    @Test
    public void temporalFields() {
        // how to know which fields are included in the pattern?
        DateTimeFormatter formatter;
        TemporalAccessor accessor;
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));

        now.getYear();       // java.time.LocalDateTime.getYear       -> java.time.LocalDate.getYear       -> this.year
        now.getMonth();      // java.time.LocalDateTime.getMonth      -> java.time.LocalDate.getMonth      -> Month.of(this.month)
        now.getMonthValue(); // java.time.LocalDateTime.getMonthValue -> java.time.LocalDate.getMonthValue -> this.month
        now.getDayOfYear();  // java.time.LocalDateTime.getDayOfYear  -> java.time.LocalDate.getDayOfYear  -> calculate
        now.getDayOfMonth(); // java.time.LocalDateTime.getDayOfMonth -> java.time.LocalDate.getDayOfMonth -> this.day
        now.getDayOfWeek();  // java.time.LocalDateTime.getDayOfWeek  -> java.time.LocalDate.getDayOfWeek  -> calculate
        now.getHour();       // java.time.LocalDateTime.getHour       -> java.time.LocalTime.getHour       -> this.hour
        now.getMinute();     // java.time.LocalDateTime.getMinute     -> java.time.LocalTime.getMinute     -> this.minute
        now.getSecond();     // java.time.LocalDateTime.getSecond     -> java.time.LocalTime.getSecond     -> this.second
        now.getNano();       // java.time.LocalDateTime.getNano       -> java.time.LocalTime.getNano       -> this.nano
        now.getZone();       // this.zone
        now.getOffset();     // this.offset

        // java.time.ZonedDateTime supports ALL fields
        accessor = now;
        assertTrue(accessor.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH));
        assertTrue(accessor.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR));
        assertTrue(accessor.isSupported(ChronoField.ALIGNED_WEEK_OF_MONTH));
        assertTrue(accessor.isSupported(ChronoField.ALIGNED_WEEK_OF_YEAR));
        assertTrue(accessor.isSupported(ChronoField.AMPM_OF_DAY));
        assertTrue(accessor.isSupported(ChronoField.CLOCK_HOUR_OF_AMPM));
        assertTrue(accessor.isSupported(ChronoField.CLOCK_HOUR_OF_DAY));
        assertTrue(accessor.isSupported(ChronoField.DAY_OF_MONTH));
        assertTrue(accessor.isSupported(ChronoField.DAY_OF_WEEK));
        assertTrue(accessor.isSupported(ChronoField.DAY_OF_YEAR));
        assertTrue(accessor.isSupported(ChronoField.EPOCH_DAY));
        assertTrue(accessor.isSupported(ChronoField.ERA));
        assertTrue(accessor.isSupported(ChronoField.HOUR_OF_AMPM));
        assertTrue(accessor.isSupported(ChronoField.HOUR_OF_DAY));
        assertTrue(accessor.isSupported(ChronoField.INSTANT_SECONDS));
        assertTrue(accessor.isSupported(ChronoField.MICRO_OF_DAY));
        assertTrue(accessor.isSupported(ChronoField.MICRO_OF_SECOND));
        assertTrue(accessor.isSupported(ChronoField.MILLI_OF_DAY));
        assertTrue(accessor.isSupported(ChronoField.MILLI_OF_SECOND));
        assertTrue(accessor.isSupported(ChronoField.MINUTE_OF_DAY));
        assertTrue(accessor.isSupported(ChronoField.MINUTE_OF_HOUR));
        assertTrue(accessor.isSupported(ChronoField.MONTH_OF_YEAR));
        assertTrue(accessor.isSupported(ChronoField.NANO_OF_DAY));
        assertTrue(accessor.isSupported(ChronoField.NANO_OF_SECOND));
        assertTrue(accessor.isSupported(ChronoField.OFFSET_SECONDS));
        assertTrue(accessor.isSupported(ChronoField.PROLEPTIC_MONTH));
        assertTrue(accessor.isSupported(ChronoField.SECOND_OF_DAY));
        assertTrue(accessor.isSupported(ChronoField.SECOND_OF_MINUTE));
        assertTrue(accessor.isSupported(ChronoField.YEAR));
        assertTrue(accessor.isSupported(ChronoField.YEAR_OF_ERA));

        formatter = DateTimeFormatter.ofPattern("yyyy");
        accessor = formatter.parse(formatter.format(now));
        // after parsing with "yyyy" formatter, ONLY YEAR is supported
        assertFalse(accessor.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_MONTH));
        assertFalse(accessor.isSupported(ChronoField.ALIGNED_DAY_OF_WEEK_IN_YEAR));
        assertFalse(accessor.isSupported(ChronoField.ALIGNED_WEEK_OF_MONTH));
        assertFalse(accessor.isSupported(ChronoField.ALIGNED_WEEK_OF_YEAR));
        assertFalse(accessor.isSupported(ChronoField.AMPM_OF_DAY));
        assertFalse(accessor.isSupported(ChronoField.CLOCK_HOUR_OF_AMPM));
        assertFalse(accessor.isSupported(ChronoField.CLOCK_HOUR_OF_DAY));
        assertFalse(accessor.isSupported(ChronoField.DAY_OF_MONTH));
        assertFalse(accessor.isSupported(ChronoField.DAY_OF_WEEK));
        assertFalse(accessor.isSupported(ChronoField.DAY_OF_YEAR));
        assertFalse(accessor.isSupported(ChronoField.EPOCH_DAY));
        assertFalse(accessor.isSupported(ChronoField.ERA));
        assertFalse(accessor.isSupported(ChronoField.HOUR_OF_AMPM));
        assertFalse(accessor.isSupported(ChronoField.HOUR_OF_DAY));
        assertFalse(accessor.isSupported(ChronoField.INSTANT_SECONDS));
        assertFalse(accessor.isSupported(ChronoField.MICRO_OF_DAY));
        assertFalse(accessor.isSupported(ChronoField.MICRO_OF_SECOND));
        assertFalse(accessor.isSupported(ChronoField.MILLI_OF_DAY));
        assertFalse(accessor.isSupported(ChronoField.MILLI_OF_SECOND));
        assertFalse(accessor.isSupported(ChronoField.MINUTE_OF_DAY));
        assertFalse(accessor.isSupported(ChronoField.MINUTE_OF_HOUR));
        assertFalse(accessor.isSupported(ChronoField.MONTH_OF_YEAR));
        assertFalse(accessor.isSupported(ChronoField.NANO_OF_DAY));
        assertFalse(accessor.isSupported(ChronoField.NANO_OF_SECOND));
        assertFalse(accessor.isSupported(ChronoField.OFFSET_SECONDS));
        assertFalse(accessor.isSupported(ChronoField.PROLEPTIC_MONTH));
        assertFalse(accessor.isSupported(ChronoField.SECOND_OF_DAY));
        assertFalse(accessor.isSupported(ChronoField.SECOND_OF_MINUTE));
        assertTrue(accessor.isSupported(ChronoField.YEAR));
        assertFalse(accessor.isSupported(ChronoField.YEAR_OF_ERA));
        // which is weird, because this works:
        accessor = Year.now();
        assertTrue(accessor.isSupported(ChronoField.ERA));
        assertTrue(accessor.isSupported(ChronoField.YEAR_OF_ERA));
        // however this works too
        assertTrue(Year.from(formatter.parse(formatter.format(now))).isSupported(ChronoField.ERA));

        // which temporals support INSTANT_SECONDS? Only the ones that have full information
        // either from all fields + offset or because that's what java.time.Instant actually represents
        assertTrue(Instant.now().isSupported(ChronoField.INSTANT_SECONDS));
        assertTrue(ZonedDateTime.now().isSupported(ChronoField.INSTANT_SECONDS));
        assertTrue(OffsetDateTime.now().isSupported(ChronoField.INSTANT_SECONDS));
        // all the below miss some information
        assertFalse(LocalDate.now().isSupported(ChronoField.INSTANT_SECONDS));
        assertFalse(LocalDateTime.now().isSupported(ChronoField.INSTANT_SECONDS));
        assertFalse(LocalTime.now().isSupported(ChronoField.INSTANT_SECONDS));
        assertFalse(OffsetTime.now().isSupported(ChronoField.INSTANT_SECONDS));
        assertFalse(Year.now().isSupported(ChronoField.INSTANT_SECONDS));
        assertFalse(YearMonth.now().isSupported(ChronoField.INSTANT_SECONDS));
    }

    @Test
    public void temporalFormatters() {
        ZonedDateTime now = LocalDateTime.now().atZone(ZoneId.of("Europe/Warsaw"));

        // date, time, offset, zone
        // 2025-09-15T15:53:48.000071533+02:00[Europe/Warsaw]
        System.out.println(DateTimeFormatter.ISO_ZONED_DATE_TIME.format(now));
        // 2025-09-15T15:53:48.000071533+02:00[Europe/Warsaw]
        System.out.println(DateTimeFormatter.ISO_DATE_TIME.format(now));

        // date, time, offset
        // 2025-09-15T15:53:48.000071533+02:00
        System.out.println(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now));
        // 2025-09-15T08:53:48.000071533-05:00
        System.out.println("~ " + DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(TimeZone.getTimeZone("EST").toZoneId()).format(now));

        // date, time, UTC always, because ISO_INSTANT uses java.time.format.DateTimeFormatterBuilder.InstantPrinterParser
        // which always prints "Z"
        // 2025-09-15T13:53:48.000071533Z
        System.out.println(DateTimeFormatter.ISO_INSTANT.format(now));
        // 2025-09-15T13:53:48.000071533Z
        System.out.println("~ " + DateTimeFormatter.ISO_INSTANT.withZone(TimeZone.getTimeZone("EST").toZoneId()).format(now));
        // 2025-09-15T13:53:48.000071533Z
        System.out.println("~ " + DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

        // date, time
        // 2025-09-15T15:53:48.000071533
        System.out.println(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(now));

        // date, offset
        // 2025-09-15+02:00
        System.out.println(DateTimeFormatter.ISO_OFFSET_DATE.format(now));
        // 2025-09-15+02:00
        System.out.println(DateTimeFormatter.ISO_DATE.format(now));
        // 20250915+0200
        System.out.println(DateTimeFormatter.BASIC_ISO_DATE.format(now));

        // date
        // 2025-09-15
        System.out.println(DateTimeFormatter.ISO_LOCAL_DATE.format(now));

        // time, offset
        // 15:53:48.000071533+02:00
        System.out.println(DateTimeFormatter.ISO_OFFSET_TIME.format(now));
        // 15:53:48.000071533+02:00
        System.out.println(DateTimeFormatter.ISO_TIME.format(now));

        // time
        // 15:53:48.000071533
        System.out.println(DateTimeFormatter.ISO_LOCAL_TIME.format(now));

        // weird ones
        // 2025-258+02:00c
        System.out.println(DateTimeFormatter.ISO_ORDINAL_DATE.format(now));
        // 2025-W38-1+02:00
        System.out.println(DateTimeFormatter.ISO_WEEK_DATE.format(now));

        // additionally...

        // Instant.now() can't be formatted by any ISO_xxx formatter other than ISO_INSTANT, unless atZone() is
        // called (returning Offset/ZonedDateTime)
        System.out.println(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.now().atZone(ZoneId.systemDefault())));
        // zone can be defined at the formatter level too and java.time.format.DateTimePrintContext.adjust()
        // will convert Instant into ZonedDateTime
        System.out.println(DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(TimeZone.getTimeZone("EST").toZoneId()).format(Instant.now()));
        try {
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.now());
            fail("Should fail parsing java.time.Instant without time zone");
        } catch (UnsupportedTemporalTypeException ignored) {
        }
    }

}
