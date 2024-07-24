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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
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

public class TemporalExtractorTest {

    private Extractor extractor;

    @BeforeMethod
    public void setup() {
        extractor = new TemporalExtractor(ConfigKey.DATE_FORMAT.getDefaultValue(),
            TimeZone.getTimeZone(ConfigKey.DATE_FORMAT_ZONE.getDefaultValue()));

        ObjectToJsonConverter converter = new ObjectToJsonConverter(null);
        converter.setupContext(
            new SerializeOptions.Builder()
                .faultHandler(ValueFaultHandler.THROWING_VALUE_FAULT_HANDLER)
                .build());
    }

    @Test
    public void type() {
        assertEquals(extractor.getType(), Temporal.class);
    }

    @Test
    public void canSetValue() {
        assertFalse(extractor.canSetValue());
    }

    @Test
    public void directExtract() throws AttributeNotFoundException {
        Instant now = Instant.now();
        Deque<String> stack = new LinkedList<>();
        Object result = extractor.extractObject(null, now, stack, false);
        assertEquals(result, now);
    }

    @Test
    public void jsonExtract() throws AttributeNotFoundException {
        Extractor extractor1 = new TemporalExtractor("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone("UTC"));
        Extractor extractor2 = new TemporalExtractor("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone("EST"));
        Extractor extractor1z = new TemporalExtractor("yyyy-MM-dd HH:mm:ssXXX", TimeZone.getTimeZone("UTC"));
        Extractor extractor2z = new TemporalExtractor("yyyy-MM-dd HH:mm:ssXXX", TimeZone.getTimeZone("EST"));

        DateTimeFormatter dtzf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
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

        Object result = extractor1.extractObject(null, dtzf.parse("2024-07-23 12:21:00Z", Instant::from), stack, true);
        assertEquals(result, "2024-07-23 12:21:00");
        // CET instant is two hours earlier in UTC zone during summer
        result = extractor1.extractObject(null, dtzf.parse("2024-07-23 12:21:00+02:00", Instant::from), stack, true);
        assertEquals(result, "2024-07-23 10:21:00");

        // and one hour earlier during winter
        LocalDateTime november23 = dtf.parse("2024-11-23 12:21:00", LocalDateTime::from);
        result = extractor1.extractObject(null, november23.toInstant(ZoneId.of("CET").getRules().getOffset(november23)), stack, true);
        assertEquals(result, "2024-11-23 11:21:00");
        result = extractor2.extractObject(null, november23.toInstant(ZoneId.of("CET").getRules().getOffset(november23)), stack, true);
        assertEquals(result, "2024-11-23 06:21:00");
        result = extractor1z.extractObject(null, november23.toInstant(ZoneId.of("CET").getRules().getOffset(november23)), stack, true);
        assertEquals(result, "2024-11-23 11:21:00Z");
        result = extractor2z.extractObject(null, november23.toInstant(ZoneId.of("CET").getRules().getOffset(november23)), stack, true);
        assertEquals(result, "2024-11-23 06:21:00-05:00");

        // other temporals that contain ChronoField.INSTANT_SECONDS

        OffsetDateTime odt = OffsetDateTime.of(
            LocalDate.of(2024, 7, 23),
            LocalTime.of(13, 55, 3),
            ZoneOffset.ofHours(2)
        );
        result = extractor1.extractObject(null, odt, stack, true);
        // formatter is UTC, so it's earlier than CET by 2 hours
        assertEquals(result, "2024-07-23 11:55:03");
        result = extractor2.extractObject(null, odt, stack, true);
        // formatter is EST, so it's earlier than CET by 5 + 2 hours
        assertEquals(result, "2024-07-23 06:55:03");
        result = extractor1z.extractObject(null, odt, stack, true);
        assertEquals(result, "2024-07-23 11:55:03Z");
        result = extractor2z.extractObject(null, odt, stack, true);
        assertEquals(result, "2024-07-23 06:55:03-05:00");

        ZonedDateTime zdt = ZonedDateTime.of(
            LocalDate.of(2024, 7, 23),
            LocalTime.of(13, 55, 3),
            TimeZone.getTimeZone("CET").toZoneId()
        );
        result = extractor1.extractObject(null, zdt, stack, true);
        // formatter is UTC, so it's earlier than CET by 2 hours
        assertEquals(result, "2024-07-23 11:55:03");
        result = extractor2.extractObject(null, zdt, stack, true);
        // formatter is EST, so it's earlier than CET by 5 + 2 hours
        assertEquals(result, "2024-07-23 06:55:03");
        result = extractor1z.extractObject(null, zdt, stack, true);
        assertEquals(result, "2024-07-23 11:55:03Z");
        result = extractor2z.extractObject(null, zdt, stack, true);
        assertEquals(result, "2024-07-23 06:55:03-05:00");

        // local dates (no time, no time-zone)

        result = new TemporalExtractor("yyyy-MM-dd", TimeZone.getTimeZone("UTC")).extractObject(null, LocalDate.of(2024, 7, 23), stack, true);
        // we need to use adjustment of the zone from the formatter used by the extractor
        if (ZoneId.of("UTC").getRules().getOffset(Instant.now()).getTotalSeconds() > 0) {
            // we're east of Greenwich, so the formatted date is actually a day before
            // this is expected, as midnight is assumed for time-less Temporals and local zone is used, while
            // formatter's zone is UTC
            assertEquals(result, "2024-07-22");
        } else {
            assertEquals(result, "2024-07-23");
        }
        result = new TemporalExtractor("yyyy-MM-dd", TimeZone.getDefault()).extractObject(null, LocalDate.of(2024, 7, 23), stack, true);
        // the same zone is used by the extractor's formatter and extractor's default offset
        assertEquals(result, "2024-07-23");
        result = extractor1.extractObject(null, LocalDate.of(2024, 7, 23), stack, true);
        assertEquals(result, dtf.withZone(ZoneId.of("UTC")).format(OffsetDateTime.of(
            LocalDate.of(2024, 7, 23),
            LocalTime.of(0, 0, 0, 0),
            ZoneId.of("UTC").getRules().getOffset(Instant.now())
        )));
    }

    @Test
    public void jsonExtractMillis() throws AttributeNotFoundException {
        Extractor extractor1 = new TemporalExtractor("millis", TimeZone.getTimeZone("UTC"));
        Extractor extractor2 = new TemporalExtractor("nanos", TimeZone.getTimeZone("UTC"));
        Extractor extractor3 = new TemporalExtractor("nanos", TimeZone.getTimeZone("Europe/Warsaw"));

        DateTimeFormatter dtzf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSSXXX");
        Deque<String> stack = new LinkedList<>();

        Instant nowUTC = dtzf.parse("2024-07-24 08:21:04.543454341Z", Instant::from);
        Instant nowCET = dtzf.parse("2024-07-24 08:21:04.543454341+02:00", Instant::from);
        // 8AM CEST is 6AM UTC
        assertTrue(nowCET.isBefore(nowUTC));

        // instants

        Object result = extractor1.extractObject(null, dtzf.parse("2024-07-24 08:21:04.543999999Z", Instant::from), stack, true);
        assertEquals(result, nowUTC.toEpochMilli());
        // CET instant is two hours earlier in UTC zone during summer
        result = extractor1.extractObject(null, dtzf.parse("2024-07-24 08:21:04.543999999+02:00", Instant::from), stack, true);
        assertEquals(result, nowUTC.toEpochMilli() - (2L * 3600L * 1000L));
        result = extractor2.extractObject(null, dtzf.parse("2024-07-24 08:21:04.543454341+02:00", Instant::from), stack, true);
        assertEquals(result, (nowUTC.getEpochSecond() - (2L * 3600L)) * 1_000_000_000L + nowUTC.getNano());
        result = extractor3.extractObject(null, dtzf.parse("2024-07-24 08:21:04.543454341+02:00", Instant::from), stack, true);
        assertEquals(result, (nowUTC.getEpochSecond() - (2L * 3600L)) * 1_000_000_000L + nowUTC.getNano());
        result = extractor3.extractObject(null, dtzf.parse("2024-07-24 08:21:04.543454341Z", Instant::from), stack, true);
        assertEquals(result, (nowUTC.getEpochSecond()) * 1_000_000_000L + nowUTC.getNano());

        // other temporals that contain ChronoField.INSTANT_SECONDS

        OffsetDateTime odt = OffsetDateTime.of(
            LocalDate.of(2024, 7, 24),
            LocalTime.of(8, 21, 4, 543454341),
            ZoneOffset.ofHours(2)
        );
        result = extractor1.extractObject(null, odt, stack, true);
        assertEquals(result, nowUTC.toEpochMilli() - (2L * 3600L * 1000L));
        result = extractor2.extractObject(null, odt, stack, true);
        assertEquals(result, (nowUTC.getEpochSecond() - (2L * 3600L)) * 1_000_000_000L + nowUTC.getNano());
    }

}
