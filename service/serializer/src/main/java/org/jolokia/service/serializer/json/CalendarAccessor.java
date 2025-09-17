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
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Deque;
import java.util.TimeZone;
import javax.management.AttributeNotFoundException;

import org.jolokia.server.core.util.DateUtil;
import org.jolokia.service.serializer.object.Converter;

/**
 * {@link ObjectAccessor} for {@link Calendar} instances.
 */
public class CalendarAccessor implements ObjectAccessor {

    private final DateFormatConfiguration configuration;

    /**
     * Create this accessor using configurable format and {@link TimeZone}.
     *
     * @param configuration
     */
    public CalendarAccessor(DateFormatConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Class<?> getType() {
        return Calendar.class;
    }

    @Override
    public Object extractObject(ObjectToJsonConverter pConverter, Object pCalendar, Deque<String> pPathParts, boolean pJsonify) throws AttributeNotFoundException {
        if (!pJsonify || pCalendar == null) {
            return pCalendar;
        }

        Calendar cal = (Calendar) pCalendar;
        if (configuration.usesUnixTime()) {
            return configuration.toUnixTime(cal);
        }


        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathPart != null) {
            if (!"time".equals(pathPart)) {
                return pConverter.getValueFaultHandler().handleException(
                    new AttributeNotFoundException("A calendar accepts only a single inner path element " +
                        "of value 'time' (and not '" + pathPart + "')"));
            }
            return cal.getTimeInMillis();
        }

        // the calendar has its timezone and the formatter has one, but it's adjusted internally
        return configuration.format(cal);
    }

    @Override
    public boolean supportsStringConversion() {
        return true;
    }

    @Override
    public String extractString(Object pCalendar) {
        Calendar calendar = (Calendar) pCalendar;
        if (configuration.usesUnixTime()) {
            return Long.toString(configuration.toUnixTime(calendar));
        }
        return configuration.format(calendar);
    }

    @Override
    public boolean canSetValue() {
        return true;
    }

    @Override
    public Object setObjectValue(Converter<String> pConverter, Object pCalendar, String pAttribute, Object pValue)
            throws IllegalAccessException, InvocationTargetException, IllegalArgumentException {
        Calendar cal = (Calendar) pCalendar;
        if ("time".equals(pAttribute)) {
            long time;
            long oldValue = cal.getTime().getTime();
            if (pValue instanceof String) {
                time = Long.parseLong((String) pValue);
            } else {
                time = (Long) pValue;
            }
            cal.setTime(new Date(time));
            return oldValue;
        } else if ("iso8601".equals(pAttribute)) {
            Date newDate = DateUtil.fromISO8601(pValue.toString());
            String oldValue = DateUtil.toISO8601(cal.getTime());
            cal.setTime(newDate);
            return oldValue;
        } else if ("format".equals(pAttribute)) {
            // we assume that the value is set within java.util.Date object as unix time
            // after parsing the value according to configured date format
            try {
                Date newDate = configuration.parseAsDate(pValue.toString());
                String oldValue = configuration.format(cal);
                cal.setTime(newDate);
                return oldValue;
            } catch (ParseException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
        throw new UnsupportedOperationException("Setting of calendar values is not yet supported directly. " +
            "Use a path/attribute 'time', 'iso8601' or 'format' " +
            "to set the epoch seconds on a date of the calendar");
    }

}
