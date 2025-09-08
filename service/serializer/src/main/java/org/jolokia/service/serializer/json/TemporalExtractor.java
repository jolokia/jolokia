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

import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.util.Deque;
import java.util.TimeZone;
import javax.management.AttributeNotFoundException;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.service.serializer.object.Deserializer;
import org.jolokia.service.serializer.object.StringToObjectConverter;

/**
 * Extractor for implementations of {@link Temporal}, like:<ul>
 *     <li>{@link java.time.LocalDateTime}</li>
 *     <li>{@link java.time.OffsetDateTime}</li>
 *     <li>{@link java.time.Instant}</li>
 *     <li>{@link java.time.Year}</li>
 *     <li>...</li>
 * </ul>
 */
public class TemporalExtractor implements Extractor {

    private DateTimeFormatter formatter;
    protected boolean useUnixTimestamp = false;
    protected boolean useUnixMillis = false;
    protected boolean useUnixNanos = false;

    public TemporalExtractor(String dateFormat, TimeZone timeZone) {
        if ("time".equals(dateFormat) || "long".equals(dateFormat) || "millis".equals(dateFormat)) {
            useUnixMillis = true;
        } else if ("unix".equals(dateFormat)) {
            useUnixTimestamp = true;
        } else if ("nanos".equals(dateFormat)) {
            useUnixNanos = true;
        } else {
            // there's always a zone, so zone-less Temporals will be adjusted
            try {
                this.formatter = DateTimeFormatter.ofPattern(dateFormat).withZone(timeZone.toZoneId());
            } catch (IllegalArgumentException e) {
                this.formatter = DateTimeFormatter.ofPattern(ConfigKey.DATE_FORMAT.getDefaultValue())
                    .withZone(TimeZone.getTimeZone(ConfigKey.DATE_FORMAT_ZONE.getDefaultValue()).toZoneId());
            }
        }
    }

    @Override
    public Class<?> getType() {
        return Temporal.class;
    }

    @Override
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Deque<String> pExtraArgs, boolean jsonify) throws AttributeNotFoundException {
        if (!jsonify || pValue == null) {
            return pValue;
        }

        Temporal temporal = (Temporal) pValue;

        // if a Temporal supports INSTANT_SECONDS, it also supports MILLI_OF_SECOND, MICRO_OF_SECOND and
        // NANO_OF_SECOND
        boolean hasInstant = temporal.isSupported(ChronoField.INSTANT_SECONDS);

        // if we have access to INSTANT_SECONDS fields it's easier
        if (hasInstant) {
            // the pattern has enforced time zone, so whether or not there's a pattern field
            // for the zone or whether or not the temporal contains
            // the zone field, we can proceed without conversion (formatter will do that for us)

            if (useUnixTimestamp) {
                return temporal.getLong(ChronoField.INSTANT_SECONDS);
            }
            if (useUnixMillis) {
                return temporal.getLong(ChronoField.INSTANT_SECONDS) * 1000L
                    + temporal.getLong(ChronoField.MILLI_OF_SECOND);
            }
            if (useUnixNanos) {
                return temporal.getLong(ChronoField.INSTANT_SECONDS) * 1_000_000_000L
                    + temporal.getLong(ChronoField.NANO_OF_SECOND);
            }

            return formatter.format(temporal);
        }

        // we don't have ChronoField.INSTANT_SECONDS, so we may be dealing with Year, YearMonth, LocalTime
        // or any zone-less/offset-less Temporals
        // out of all Temporal implementations (not counting Chrono* ones), that don't support
        // INSTANT_SECONDS, only java.time.OffsetTime supports OFFSET_SECONDS
        //
        // the weird scenario is if someone defines "yyyy" in the pattern, but expects something valid
        // when serializing LocalTime - we have to assume something. See documentation for details
        LocalDateTime now = LocalDateTime.now();
        int year = temporal.isSupported(ChronoField.YEAR) ? temporal.get(ChronoField.YEAR) : now.getYear();
        int month = temporal.isSupported(ChronoField.MONTH_OF_YEAR) ? temporal.get(ChronoField.MONTH_OF_YEAR) : now.getMonthValue();
        int day = temporal.isSupported(ChronoField.DAY_OF_MONTH) ? temporal.get(ChronoField.DAY_OF_MONTH) : now.getDayOfMonth();
        int hour = temporal.isSupported(ChronoField.HOUR_OF_DAY) ? temporal.get(ChronoField.HOUR_OF_DAY) : 0;
        int minute = temporal.isSupported(ChronoField.MINUTE_OF_HOUR) ? temporal.get(ChronoField.MINUTE_OF_HOUR) : 0;
        int seconds = temporal.isSupported(ChronoField.SECOND_OF_MINUTE) ? temporal.get(ChronoField.SECOND_OF_MINUTE) : 0;
        int nanos = temporal.isSupported(ChronoField.NANO_OF_SECOND) ? temporal.get(ChronoField.NANO_OF_SECOND) : 0;
        ZoneOffset offset = null;
        if (temporal.isSupported(ChronoField.OFFSET_SECONDS)) {
            offset = ZoneOffset.ofTotalSeconds(temporal.get(ChronoField.OFFSET_SECONDS));
        } else if (temporal instanceof LocalDateTime) {
            offset = formatter.getZone().getRules().getOffset((LocalDateTime) temporal);
        } else if (temporal instanceof LocalDate) {
            offset = formatter.getZone().getRules().getOffset(LocalDateTime.of((LocalDate) temporal, LocalTime.of(0, 0, 0)));
        } else if (temporal instanceof YearMonth) {
            int y = ((YearMonth) temporal).getYear();
            int m = ((YearMonth) temporal).getMonthValue();
            offset = formatter.getZone().getRules().getOffset(LocalDateTime.of(LocalDate.of(y, m, 1), LocalTime.of(0, 0, 0)));
        } else if (temporal instanceof OffsetTime) {
            offset = ((OffsetTime) temporal).getOffset();
        } else {
            offset = formatter.getZone().getRules().getOffset(Instant.now());
        }

        OffsetDateTime odt = OffsetDateTime.of(year, month, day, hour, minute, seconds, nanos, offset);

        return formatter.format(odt);
    }

    @Override
    public Object setObjectValue(Deserializer<String> pConverter, Object pInner, String pAttribute, Object pValue) throws IllegalAccessException, InvocationTargetException, IllegalArgumentException {
        throw new IllegalArgumentException("java.time.Temporal instance is immutable an cannot change its value");
    }

    @Override
    public boolean canSetValue() {
        // Temporal instances are immutable
        return false;
    }

}
