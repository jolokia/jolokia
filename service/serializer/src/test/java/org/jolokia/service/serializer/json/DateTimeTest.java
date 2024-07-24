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

import java.text.ParseException;
import java.text.SimpleDateFormat;
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
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.UnsupportedTemporalTypeException;
import java.util.Calendar;
import java.util.Date;
import java.util.EnumSet;
import java.util.TimeZone;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class DateTimeTest {

    @Test
    public void playingWithTime() throws ParseException {

        // SimpleDateFormat itself uses time zone from default locale using
        // java.util.Calendar.defaultTimeZone(java.util.Locale)
        // setting explicit time zone will format dates as we need
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        sdf1.setTimeZone(TimeZone.getTimeZone("CET"));
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        sdf2.setTimeZone(TimeZone.getTimeZone("UTC"));
        assertEquals(sdf2.format(sdf1.parse("20240722153301999")), "20240722133301999");
        assertEquals(sdf1.format(sdf2.parse("20240722133301999")), "20240722153301999");
        // same (unix) epoch time when parsing two different, but equivalent dates
        assertEquals(sdf2.parse("20240722133301999").getTime(), sdf1.parse("20240722153301999").getTime());

        long midnight1 = sdf1.parse("20240722000000000").getTime();
        long midnight2 = sdf2.parse("20240722000000000").getTime();
        // midnight in Poland, summer time is 2 hours earlier than midnight UTC
        assertTrue(midnight1 < midnight2);
        assertEquals(midnight2 - midnight1, 2L * 3600L * 1000L);

        // Instant is kind of like java.util.Date object (just millis after 19700101000000)
        Instant i1 = Instant.ofEpochMilli(midnight1);
        Instant i2 = Instant.ofEpochMilli(midnight2);
        assertFalse(i1.isAfter(new Date(midnight1).toInstant()));
        assertFalse(i1.isBefore(new Date(midnight1).toInstant()));
        assertEquals(Instant.ofEpochSecond(i1.getEpochSecond(), 123456789).toEpochMilli(), midnight1 + 123L);

        DateTimeFormatter dtf1 = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
        DateTimeFormatter dtf2 = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSXX");
        DateTimeFormatter df0 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        DateTimeFormatter df1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS (XXX)");
        DateTimeFormatter df2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS (XXX)")
            .withZone(ZoneId.of("UTC"));
        DateTimeFormatter df3 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS (XXX)")
            .withZone(ZoneId.of("CET"));
        DateTimeFormatter dfDefault = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS (XXX)")
            .withZone(ZoneId.systemDefault());

        // some formatters have zone, some don't - but we can't detect whether zone-related fields were
        // used in the pattern
        assertNull(dtf1.getZone());
        assertNotNull(df2.getZone());

        // there's no DateTimeFormatter flag matching java.time.temporal.ChronoField.INSTANT_SECONDS,
        // and that's the only way an Instant can be formatted. That's why Instant.toString()
        // uses java.time.format.DateTimeFormatter.ISO_INSTANT which uses
        // java.time.format.DateTimeFormatterBuilder.appendInstant()
        try {
            df1.format(i1);
            fail("Should have thrown exception");
        } catch (UnsupportedTemporalTypeException ignored) {
        }
        // it's kind of hardcoded to use ISO-8601 format and we can't change it without
        // converting the Instant to something more flexible
        DateTimeFormatter dfi = new DateTimeFormatterBuilder().appendInstant().toFormatter();
        assertEquals(dfi.format(i1), "2024-07-21T22:00:00Z");

        // LocalDateTime is year-month-day-hour-minute-seconds-nanos from local perspective,
        // so it's actually different instant depending on the timezone
        // TimeZone appears in OffsetDateTime/ZonedDateTime and when using DateTimeFormatter
        //
        // to convert an instant to LocalDateTime we need a zone - to tell at which zone
        // we interpret this instant
        // midnight in Poland interpreted as CET
        LocalDateTime ldt11 = LocalDateTime.ofInstant(i1, TimeZone.getTimeZone("CET").toZoneId());
        assertEquals(ldt11.getHour(), 0);
        // midnight in Poland interpreted as UTC
        LocalDateTime ldt12 = LocalDateTime.ofInstant(i1, TimeZone.getTimeZone("UTC").toZoneId());
        assertEquals(ldt12.getHour(), 22);
        // midnight in UK interpreted as CET
        LocalDateTime ldt21 = LocalDateTime.ofInstant(i2, TimeZone.getTimeZone("CET").toZoneId());
        assertEquals(ldt21.getHour(), 2);
        // midnight in UK interpreted as UTC
        LocalDateTime ldt22 = LocalDateTime.ofInstant(i2, TimeZone.getTimeZone("UTC").toZoneId());
        assertEquals(ldt22.getHour(), 0);
        // we can't format local date times using pattern with time zone, because there's no such information
        // it was lost after Instant + ZoneId was used to construct LocalDateTime
        try {
            df1.format(ldt11);
            fail("Should have thrown exception");
        } catch (UnsupportedTemporalTypeException ignored) {
        }
        assertEquals(df0.format(ldt11), "2024-07-22 00:00:00.000");
        assertEquals(df0.format(ldt12), "2024-07-21 22:00:00.000");
        assertEquals(df0.format(ldt21), "2024-07-22 02:00:00.000");
        assertEquals(df0.format(ldt22), "2024-07-22 00:00:00.000");

        // Offset and Zoned DateTimes that interpret midnight, 22nd July 2024 in CET zone
        OffsetDateTime odt = ldt11.atOffset(ZoneOffset.ofHours(2));
        ZonedDateTime zdt = ldt11.atZone(TimeZone.getTimeZone("CET").toZoneId());
        // OffsetDateTime with artificial zone (AFAIK, there's no +1 during summer time)
        OffsetDateTime odtWeirdButWorks = ldt11.atOffset(ZoneOffset.ofHours(1));

        // no zone specified on df1, so default should be used
        assertEquals(df1.format(odt), dfDefault.format(odt));
        assertEquals(df1.format(zdt), dfDefault.format(zdt));
        // UTC specified on the formatter, so should be "Z" (with "xxx" it'd be "+0000")
        // but also it's 2 hours earlier in Greenwich
        assertEquals(df2.format(odt), "2024-07-21 22:00:00.000 (Z)");
        assertEquals(df2.format(zdt), "2024-07-21 22:00:00.000 (Z)");
        // CET specified, it's summer time, so "+02:00"
        assertEquals(df3.format(odt), "2024-07-22 00:00:00.000 (+02:00)");
        assertEquals(df3.format(zdt), "2024-07-22 00:00:00.000 (+02:00)");
        // midnight interpreted in +01:00 zone is an hour later in CET and hour earlier in Greenwich
        assertEquals(df2.format(odtWeirdButWorks), "2024-07-21 23:00:00.000 (Z)");
        assertEquals(df3.format(odtWeirdButWorks), "2024-07-22 01:00:00.000 (+02:00)");

        // DateTimeFormatter may have zone specified in the pattern and by withZone()
        // "odt" has long history:
        //  - "midnight1" long (UNIX epoch millis) is "20240722000000000" string parsed with DateFormat using "CET" zone
        //    so we took a human-readable (almost) datetime and converted it to UNIX epoch time at midnight CET
        //    if we parsed the same string using DateFormat with UTC, we'd get higher number (because midnight UTC
        //    is after midnight CET)
        //  - "i1" Instant is "midnight1" long millis, so actually equivalent without messing with TimeZones
        //    this is quite straightforward
        //  - "ldt11" LocalDateTime is "i1" Instant in "CET" zone
        //    this operation is opposite to parsing with DateFormat - it's kind of "interpretation" of the instant
        //    at given TimeZone. interpreting the same instant at UTC zone would give earlier LocalDateTime
        //  - "odt" OffsetDateTime is "ldt11" LocalDateTime in a zone with +2 offset
        //    this operation isn't actually changing the LocalDateTime part - it's rather combining zone-less
        //    LocalDateTime with specific zone/offset
        //
        // dtf1 doesn't have zone object and zone pattern field, so whatever the offset, we get the same value
        assertEquals(dtf1.format(odt), "20240722000000000");
        assertEquals(
            dtf1.format(odt.toLocalDateTime().atOffset(ZoneOffset.ofHours(1))),
            dtf1.format(odt.toLocalDateTime().atOffset(ZoneOffset.ofHours(-1))));

        // adding a zone object to DateTimeFormatter causes adjustment of the formatted Temporal using
        // formatter's zone
        DateTimeFormatter estFormatter = dtf1.withZone(TimeZone.getTimeZone("EST").toZoneId());
        assertEquals(estFormatter.format(odt), "20240721170000000");
        // getHour() always takes the hour from LocalDateTime (not dealing with offsets/zones)
        assertEquals(odt.toLocalDateTime().getHour(), 0);
        assertEquals(odt.toLocalDateTime().atOffset(ZoneOffset.ofHours(1)).getHour(), 0);
        // formatter takes its offset and Temporal offset into account
        // -5..+1 -> 6 hours span
        assertEquals(estFormatter.format(odt.toLocalDateTime().atOffset(ZoneOffset.ofHours(1))), "20240721180000000");
        // -5..-1 -> 4 hours span
        assertEquals(estFormatter.format(odt.toLocalDateTime().atOffset(ZoneOffset.ofHours(-1))), "20240721200000000");

        // dtf2 doesn't have a zone object, but prints Temporal's Zone (fails if passing an Instant for example)
        assertEquals(dtf2.format(odt), "20240722000000000+0200");
        assertEquals(dtf2.format(odt.toLocalDateTime().atOffset(ZoneOffset.ofHours(1))), "20240722000000000+0100");
        assertEquals(dtf2.format(odt.toLocalDateTime().atOffset(ZoneOffset.ofHours(-1))), "20240722000000000-0100");
        try {
            dtf2.format(odt.toLocalDateTime());
            fail("Should have thrown exception");
        } catch (UnsupportedTemporalTypeException ignored) {
        }

        // finally adding a zone to a formatter using zone pattern field prints the zone and reinterprets the
        // Temporal values
        DateTimeFormatter estFormatter2 = dtf2.withZone(TimeZone.getTimeZone("EST").toZoneId());
        assertEquals(estFormatter2.format(odt), "20240721170000000-0500");
        // midnight, moved +1 eastwards and printed as EST zone -> 6 hours earlier
        assertEquals(estFormatter2.format(odt.toLocalDateTime().atOffset(ZoneOffset.ofHours(1))), "20240721180000000-0500");
        // midnight, moved -1 westwards and printed as EST zone -> 4 hours earlier
        assertEquals(estFormatter2.format(odt.toLocalDateTime().atOffset(ZoneOffset.ofHours(-1))), "20240721200000000-0500");

        // finally Calendars combine Dates and TimeZones and add calendar calculation methods
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(i1.getLong(ChronoField.INSTANT_SECONDS) * 1000L));
        cal.setTimeZone(TimeZone.getTimeZone("CET"));
        // "formatting a Calendar" is an incorrect statement. We can only format it's "time" part
        // and reinterpret it in format's timezone
        // we could create dedicated DateFormats with zones obtained from Calendar instance, but
        // usually a single DateFormat is used with fixed zone.
        // when it's midnight in CET, it's still 10PM in Greenwich
        assertEquals(sdf1.format(cal.getTime()), "20240722000000000");
        assertEquals(sdf2.format(cal.getTime()), "20240721220000000");
    }

    @Test
    public void chronoFieldsSupportedByInstant() {
        assertTrue(Instant.now().isSupported(ChronoField.INSTANT_SECONDS));
        assertTrue(Instant.now().isSupported(ChronoField.MILLI_OF_SECOND));
        assertTrue(Instant.now().isSupported(ChronoField.MICRO_OF_SECOND));
        assertTrue(Instant.now().isSupported(ChronoField.NANO_OF_SECOND));
        EnumSet<ChronoField> fields = EnumSet.allOf(ChronoField.class);
        fields.remove(ChronoField.INSTANT_SECONDS);
        fields.remove(ChronoField.MILLI_OF_SECOND);
        fields.remove(ChronoField.MICRO_OF_SECOND);
        fields.remove(ChronoField.NANO_OF_SECOND);
        for (ChronoField f : fields) {
            assertFalse(Instant.now().isSupported(f));
        }
    }

    @Test
    public void whatTemporalImplementationsSupport() {
        Temporal[] instantTemporals = new Temporal[] {
            Instant.now(),
            OffsetDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")),
            ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"))
        };
        Temporal[] nonInstantTemporals = new Temporal[] {
            LocalDate.of(2024, 7, 23),
            LocalDateTime.of(2024, 7, 23, 13, 20, 48, 123456789),
            LocalTime.of(13, 20, 48, 123456789),
            OffsetTime.of(13, 20, 48, 123456789, ZoneOffset.UTC),
            Year.of(2024),
            YearMonth.of(2024, 7)
        };
        for (Temporal t : instantTemporals) {
            assertTrue(t.isSupported(ChronoField.INSTANT_SECONDS));
            assertTrue(t.isSupported(ChronoField.MILLI_OF_SECOND));
            assertTrue(t.isSupported(ChronoField.MICRO_OF_SECOND));
            assertTrue(t.isSupported(ChronoField.NANO_OF_SECOND));
        }
        for (Temporal t : nonInstantTemporals) {
            assertFalse(t.isSupported(ChronoField.INSTANT_SECONDS));
            if (t.getClass() == OffsetTime.class) {
                assertTrue(t.isSupported(ChronoField.OFFSET_SECONDS));
            } else {
                assertFalse(t.isSupported(ChronoField.OFFSET_SECONDS));
            }
        }
    }

}
