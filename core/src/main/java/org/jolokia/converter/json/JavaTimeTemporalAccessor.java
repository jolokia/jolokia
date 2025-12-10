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
package org.jolokia.converter.json;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.util.Deque;
import java.util.TimeZone;
import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.Converter;

/**
 * {@link org.jolokia.converter.json.ObjectAccessor} for implementations of {@link Temporal}, in particular:<ul>
 *     <li>{@link java.time.Instant}</li>
 *     <li>{@link java.time.LocalDate}</li>
 *     <li>{@link java.time.LocalDateTime}</li>
 *     <li>{@link java.time.LocalTime}</li>
 *     <li>{@link java.time.OffsetDateTime}</li>
 *     <li>{@link java.time.OffsetTime}</li>
 *     <li>{@link java.time.Year}</li>
 *     <li>{@link java.time.YearMonth}</li>
 *     <li>{@link java.time.ZonedDateTime}</li>
 * </ul>
 */
public class JavaTimeTemporalAccessor implements org.jolokia.converter.json.ObjectAccessor {

    private final org.jolokia.converter.json.DateFormatConfiguration configuration;

    /**
     * Create this accessor using configurable format and {@link TimeZone}. No paths into the {@link Temporal}
     * are supported. The JSON representation is:<ul>
     *     <li>{@link Long} if {@link Temporal#isSupported INSTANT_SECONDS} is supported</li>
     *     <li>{@link String} otherwise - formatted to configurable pattern</li>
     * </ul>
     *
     * @param configuration
     */
    public JavaTimeTemporalAccessor(org.jolokia.converter.json.DateFormatConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Class<?> getType() {
        return Temporal.class;
    }

    @Override
    public Object extractObject(ObjectToJsonConverter pConverter, Object pTemporal, Deque<String> pPathParts, boolean pJsonify) {
        if (!pJsonify || pTemporal == null) {
            return pTemporal;
        }

        Temporal temporal = (Temporal) pTemporal;

        if (configuration.usesUnixTime(temporal)) {
            // unix time is only supported for temporals which support INSTANT_SECONDS field
            return configuration.toUnixTime(temporal);
        }

        // we don't support drilling into the Temporal objects
        return extractString(pTemporal);
    }

    @Override
    public boolean supportsStringConversion() {
        return true;
    }

    @Override
    public String extractString(Object pTemporal) {
        Temporal temporal = (Temporal) pTemporal;

        if (configuration.usesUnixTime(temporal)) {
            return Long.toString(configuration.toUnixTime(temporal));
        }

        if (temporal.isSupported(ChronoField.INSTANT_SECONDS)) {
            // Easy - just use the format - even if user has configured awkward format like "dd" only.
            // There are actually only 3 basic Temporal implementations that support this field:
            //  - Instant (because that's what this actually is)
            //  - ZonedDateTime/OffsetDateTime - because there's date, time and zone
            // Instant instances lack zone information, but java.time.format.DateTimePrintContext.adjust()
            // will fix this by applying formatter's Zone to the Instant, turning it into ZonedDateTime
            return configuration.format(temporal);
        }

        // not knowing how long we're since the dawn of UNIX era is problematic and there are two problems:
        // 1) we want to format java.time.Year object using "MM-dd" pattern (missing field in the format)
        // 2) we want to format java.time.LocalDate using "yyyy-MM-dd HH:MM:ss" format (extra fields in the format)
        // so we want to be clever and support these:
        //  - java.time.LocalDateTime - date + time, no zone
        //  - java.time.LocalDate - date only, no zone
        //  - java.time.LocalTime - time only, no zone
        //  - java.time.OffsetDate - there's no such thing ;) Even if there's DateTimeFormatter.ISO_OFFSET_DATE
        //  - java.time.OffsetTime - time only + offset/zone, but without actual date...
        //  - java.time.Year - just a year. no time or zone
        //  - java.time.YearMonth - year + month. no time or zone
        //
        // java.time.Year could be formatted as 4 digits, but other types should be somehow derived from user
        // format (otherwise we'd have to guess if YearMonth should be yyyy-MM, yyyyMM or MM/yyyy)...

        // 1. LocalDateTime misses only the zone, so we'll get _local_ zone here and use configured formatter
        if (temporal instanceof LocalDateTime) {
            ZoneOffset offset = configuration.getZoneRules().getOffset((LocalDateTime) temporal);
            return configuration.format(((LocalDateTime) temporal).atOffset(offset));
        }
        // 2. OffsetTime misses the date, so starting with #2, we ignore user configuration of the pattern and stick
        //    here to ISO_OFFSET_TIME.
        //    15:53:48.000071533+02:00
        if (temporal instanceof OffsetTime) {
            // also ignore user configuration of the zone, because it won't help with OffsetTime, as we don't
            // know what day it is (and whether we're in Daylight Saving Time)
            return DateTimeFormatter.ISO_OFFSET_TIME.format(temporal);
        }
        // 3. LocalDate - ISO_LOCAL_DATE
        //    "2025-09-15"
        if (temporal instanceof LocalDate) {
            return DateTimeFormatter.ISO_LOCAL_DATE.format(temporal);
        }
        // 4. LocalTime - ISO_LOCAL_TIME without bothering with the zone/offset because we don't know the day
        //    which could affect DST
        //    "15:53:48.000071533"
        if (temporal instanceof LocalTime) {
            return DateTimeFormatter.ISO_LOCAL_TIME.format(temporal);
        }
        // 5. Year - without formatter
        if (temporal instanceof Year) {
            return Integer.toString(((Year) temporal).getValue());
        }
        // 6. YearMonth - no ISO formatter here, but let's assume it's yyyy-MM matching ISO format
        if (temporal instanceof YearMonth) {
            YearMonth ym = (YearMonth) temporal;
            int month = ym.getMonthValue();
            return ym.getYear() + "-" + (month < 10 ? "0" + month : month);
        }

        throw new IllegalArgumentException("Can't convert " + temporal.getClass() + " value");
    }

    @Override
    public boolean canSetValue() {
        // Temporal instances are immutable
        return false;
    }

    @Override
    public Object setObjectValue(Converter<String> pConverter, Object pObject, String pAttribute, Object pValue) throws IllegalAccessException, InvocationTargetException, IllegalArgumentException {
        throw new IllegalArgumentException("java.time.Temporal instance is immutable and cannot change its value");
    }

}
