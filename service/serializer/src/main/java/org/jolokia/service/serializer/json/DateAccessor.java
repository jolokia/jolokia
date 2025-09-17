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
package org.jolokia.service.serializer.json;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.util.Date;
import java.util.Deque;
import java.util.TimeZone;

import javax.management.AttributeNotFoundException;

import org.jolokia.service.serializer.object.Converter;
import org.jolokia.server.core.util.DateUtil;

/**
 * {@link ObjectAccessor} for sophisticated date handling which support virtual
 * path handling (i.e for converting to epoch time or an ISO-8601 format). The JSON representation is
 * {@link Long} value if seconds/milliseconds/nanoseconds of UNIX time is expected, or String for configured
 * formatter.
 *
 * @author roland
 * @since 17.04.11
 */
public class DateAccessor implements ObjectAccessor {

    private final DateFormatConfiguration configuration;

    /**
     * Create this accessor using configurable format and {@link TimeZone}.
     *
     * @param configuration
     */
    public DateAccessor(DateFormatConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Class<?> getType() {
        return Date.class;
    }

    @Override
    public Object extractObject(ObjectToJsonConverter pConverter, Object pDate, Deque<String> pPathParts, boolean pJsonify)
            throws AttributeNotFoundException {
        if (!pJsonify || pDate == null) {
            return pDate;
        }

        Date date = (Date) pDate;
        if (configuration.usesUnixTime()) {
            return configuration.toUnixTime(date);
        }

        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathPart != null) {
            if (!"time".equals(pathPart)) {
                return pConverter.getValueFaultHandler().handleException(
                        new AttributeNotFoundException("A date accepts only a single inner path element " +
                                                       "of value 'time' (and not '" + pathPart + "')"));
            }
            return date.getTime();
        }

        return configuration.format(date);
    }

    @Override
    public boolean supportsStringConversion() {
        return true;
    }

    @Override
    public String extractString(Object pDate) {
        Date date = (Date) pDate;
        if (configuration.usesUnixTime()) {
            return Long.toString(configuration.toUnixTime(date));
        }
        return configuration.format(date);
    }

    @Override
    public boolean canSetValue() {
        return true;
    }

    /**
     * Sets the content of {@link Date} object. The value must be either a {@code long} in which case
     * it is converted directly to a date, or a String which should be formatted according to configured date format.
     *
     * @param pConverter {@link Converter} used to convert the value being set to a class of the accessed attribute
     * @param pDate    object on which to set the value
     * @param pAttribute attribute of the object to set. (For arrays or lists it should be an index.)
     * @param pValue     the new value to set after {@link Converter#convert conversion}
     * @return
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    @Override
    public Object setObjectValue(Converter<String> pConverter, Object pDate, String pAttribute, Object pValue)
            throws IllegalAccessException, InvocationTargetException {
        Date date = (Date) pDate;
        if ("time".equals(pAttribute)) {
            long time;
            long oldValue = date.getTime();
            if (pValue instanceof String) {
                time = Long.parseLong((String) pValue);
            } else {
                time = (Long) pValue;
            }
            date.setTime(time);
            return oldValue;
        } else if ("iso8601".equals(pAttribute)) {
            Date newDate = DateUtil.fromISO8601(pValue.toString());
            String oldValue = DateUtil.toISO8601(date);
            date.setTime(newDate.getTime());
            return oldValue;
        } else if ("format".equals(pAttribute)) {
            // we assume that the value is set within java.util.Date object as unix time
            // after parsing the value according to configured date format
            try {
                Date newDate = configuration.parseAsDate(pValue.toString());
                String oldValue = configuration.format(date);
                date.setTime(newDate.getTime());
                return oldValue;
            } catch (ParseException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }
        throw new UnsupportedOperationException("Setting of date values is not yet supported directly. " +
            "Use a path/attribute 'time', 'iso8601' or 'format' " +
            "to set the epoch seconds on a date");
    }

}
