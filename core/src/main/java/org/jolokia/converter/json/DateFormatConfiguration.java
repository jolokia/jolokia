/*
 * Copyright 2009-2025 Roland Huss
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

import java.text.DateFormat;
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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalField;
import java.time.zone.ZoneRules;
import java.util.Calendar;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;

import org.jolokia.core.config.CoreConfiguration;

/**
 * Helper class to simplify management of {@link java.util.Date}/{@link java.util.Calendar}/{@code java.time}
 * formatting and parsing.
 */
public class DateFormatConfiguration {

    // a little copy from ConfigKey.DATE_FORMAT and ConfigKey.DATE_FORMAT_ZONE
    private static final String defaultDateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSSXXX";
    private static final TimeZone defaultTimeZone = TimeZone.getDefault();

    private boolean useUnixTime = false;
    // seconds
    private boolean useUnixTimestamp = false;
    // milliseconds
    private boolean useUnixMillis = false;
    // nanoseconds
    private boolean useUnixNanos = false;

    private final ZoneId zone;
    private DateFormat simpleDateFormat;
    private DateTimeFormatter dateTimeFormatter;

    /**
     * Creates formatting configuration with default pattern and time zone
     */
    public DateFormatConfiguration() {
        this(defaultDateFormat, defaultTimeZone);
    }

    /**
     * Creates formatting configuration with format and timeZone
     *
     * @param format
     * @param timeZone
     */
    public DateFormatConfiguration(String format, String timeZone) {
        this(format, timeZone == null ? TimeZone.getDefault() : TimeZone.getTimeZone(timeZone));
    }

    /**
     * Creates formatting configuration using {@link CoreConfiguration}
     * @param coreConfiguration
     */
    public DateFormatConfiguration(CoreConfiguration coreConfiguration) {
        this(coreConfiguration == null ? defaultDateFormat : coreConfiguration.dateFormat(),
            coreConfiguration == null ? defaultTimeZone : coreConfiguration.dateFormatTimeZone());
    }

    /**
     * Creates formatting configuration with format and timeZone
     *
     * @param format
     * @param timeZone
     */
    public DateFormatConfiguration(String format, TimeZone timeZone) {
        TimeZone dateFormatZone = timeZone == null ? TimeZone.getDefault() : timeZone;
        zone = dateFormatZone.toZoneId();

        if ("time".equals(format) || "long".equals(format) || "millis".equals(format)) {
            useUnixMillis = true;
            useUnixTime = true;
        } else if ("unix".equals(format)) {
            useUnixTimestamp = true;
            useUnixTime = true;
        } else if ("nanos".equals(format)) {
            useUnixNanos = true;
            useUnixTime = true;
        } else {
            try {
                // for SimpleDateFormat we should take care NOT to use:
                //  - "S" pattern more than 3 times, because we'd get 2025-09-16T09:33:13.000000477+02:00
                //    instead of 2025-09-16T09:33:13.477+02:00 (only milliseconds supported)
                //  - "n" pattern which is valid for DateTimeFormatter (nanoseconds of second)
                String dateFormat = format;
                if (dateFormat.matches(".*S{4,}.*")) {
                    dateFormat = dateFormat.replaceAll("S{4,}", "SSS");
                }
                this.simpleDateFormat = new SimpleDateFormat(dateFormat);
                this.simpleDateFormat.setTimeZone(dateFormatZone);
            } catch (IllegalArgumentException e) {
                this.simpleDateFormat = new SimpleDateFormat(defaultDateFormat);
                this.simpleDateFormat.setTimeZone(TimeZone.getDefault());
            }
            try {
                // there's always a zone, so zone-less Temporals will be adjusted
                this.dateTimeFormatter = DateTimeFormatter.ofPattern(format)
                    .withZone(dateFormatZone.toZoneId());
            } catch (IllegalArgumentException e) {
                this.dateTimeFormatter = DateTimeFormatter.ofPattern(defaultDateFormat)
                    .withZone(TimeZone.getDefault().toZoneId());
            }
        }
    }

    /**
     * Get {@link ZoneRules} from the {@link DateTimeFormatter} which should have its zone configured.
     *
     * @return
     */
    public ZoneRules getZoneRules() {
        return dateTimeFormatter.getZone().getRules();
    }

    public Set<TemporalField> getResolverFields() {
        return dateTimeFormatter.getResolverFields();
    }

    public boolean usesUnixTime() {
        return useUnixTime;
    }

    public boolean usesUnixTime(Temporal temporal) {
        // if a Temporal supports INSTANT_SECONDS, it also supports MILLI_OF_SECOND,
        // MICRO_OF_SECOND and NANO_OF_SECOND
        return useUnixTime && temporal.isSupported(ChronoField.INSTANT_SECONDS);
    }

    public Long toUnixTime(Date date) {
        if (useUnixMillis) {
            return date.getTime();
        }
        if (useUnixTimestamp) {
            return date.getTime() / 1000;
        }
        if (useUnixNanos) {
            return date.getTime() * 1_000_000;
        }
        return null;
    }

    public Date unixTimeToDate(Long v) {
        if (!useUnixTime) {
            throw new IllegalArgumentException("DateConfiguration is not configured to support UNIX timestamps");
        }

        if (useUnixMillis) {
            // expect milliseconds - as is
            return new Date(v);
        }
        if (useUnixTimestamp) {
            // expect seconds, so multiply
            return new Date(v * 1000L);
        }
        if (useUnixNanos) {
            // expect nanos, so divide
            return new Date(v / 1_000_000L);
        }
        return null;
    }

    public Date unixTimeInMillisToDate(Long v) {
        return new Date(v);
    }

    public Long toUnixTime(Calendar calendar) {
        if (useUnixMillis) {
            return calendar.getTimeInMillis();
        }
        if (useUnixTimestamp) {
            return calendar.getTimeInMillis() / 1000;
        }
        if (useUnixNanos) {
            return calendar.getTimeInMillis() * 1_000_000;
        }
        return null;
    }

    public Long toUnixTime(Temporal temporal) {
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

        return null;
    }

    public Object unixTimeToTemporal(Class<?> temporalType, Long v) {
        if (!useUnixTime) {
            throw new IllegalArgumentException("DateConfiguration is not configured to support UNIX timestamps");
        }

        // we have a number - all we can do is to create proper temporal with configured TimeZone

        long seconds = 0L;
        long nanos = 0;
        if (useUnixMillis) {
            // expect milliseconds - as is
            seconds = v / 1000L;
            nanos = v % 1000L;
        }
        if (useUnixTimestamp) {
            seconds = v;
            nanos = 0L;
        }
        if (useUnixNanos) {
            // expect nanos, so divide
            seconds = v / 1_000_000_000L;
            nanos = v % 1_000_000_000L;
        }

        Instant instant = Instant.ofEpochSecond(seconds, nanos);
        return instantToTemporal(temporalType, instant);
    }

    public Object unixTimeInNanosToTemporal(Class<?> temporalType, Long v) {
        Instant instant = Instant.ofEpochSecond(v / 1_000_000_000L, v % 1_000_000_000L);
        return instantToTemporal(temporalType, instant);
    }

    private Object instantToTemporal(Class<?> temporalType, Instant instant) {
        if (temporalType == Instant.class) {
            return instant;
        }

        // zone needed
        ZonedDateTime zdt = instant.atZone(zone);
        if (temporalType == LocalDate.class) {
            return zdt.toLocalDate();
        }
        if (temporalType == LocalDateTime.class) {
            return zdt.toLocalDateTime();
        }
        if (temporalType == LocalTime.class) {
            return zdt.toLocalTime();
        }
        if (temporalType == OffsetDateTime.class) {
            return zdt.toOffsetDateTime();
        }
        if (temporalType == OffsetTime.class) {
            return zdt.toOffsetDateTime().toOffsetTime();
        }
        if (temporalType == Year.class) {
            return Year.of(zdt.getYear());
        }
        if (temporalType == YearMonth.class) {
            return YearMonth.of(zdt.getYear(), zdt.getMonthValue());
        }
        return zdt;
    }

    public String format(Date date) {
        return simpleDateFormat.format(date);
    }

    public String format(Calendar cal) {
        return simpleDateFormat.format(cal.getTime());
    }

    public String format(Temporal temporal) {
        return dateTimeFormatter.format(temporal);
    }

    public Date parseAsDate(String value) throws ParseException {
        return simpleDateFormat.parse(value);
    }

    public Temporal parseAsTemporal(Class<?> temporalType, String pValue) {
        TemporalAccessor accessor = dateTimeFormatter.parse(pValue);

        if (temporalType == Instant.class) {
            return Instant.from(accessor);
        }

        // zone needed
        if (temporalType == LocalDate.class) {
            return LocalDate.from(accessor);
        }
        if (temporalType == LocalDateTime.class) {
            return LocalDateTime.from(accessor);
        }
        if (temporalType == LocalTime.class) {
            return LocalTime.from(accessor);
        }
        if (temporalType == OffsetDateTime.class) {
            return OffsetDateTime.from(accessor);
        }
        if (temporalType == OffsetTime.class) {
            return OffsetTime.from(accessor);
        }
        if (temporalType == Year.class) {
            return Year.from(accessor);
        }
        if (temporalType == YearMonth.class) {
            return YearMonth.from(accessor);
        }

        return ZonedDateTime.from(accessor);
    }

}
