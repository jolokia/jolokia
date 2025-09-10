package org.jolokia.service.serializer.json;

import java.lang.reflect.InvocationTargetException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Deque;
import java.util.TimeZone;

import javax.management.AttributeNotFoundException;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.service.serializer.object.Converter;
import org.jolokia.server.core.util.DateUtil;

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


/**
 * Extractor for sophisticated date handling which support virtual
 * path handling (i.e for converting to epoch time or an ISO-8601 format)
 *
 * @author roland
 * @since 17.04.11
 */
public class DateExtractor implements Extractor {

    protected SimpleDateFormat dateFormat;
    protected boolean useUnixTimestamp = false;
    protected boolean useUnixMillis = false;
    protected boolean useUnixNanos = false;

    public DateExtractor(String dateFormat, TimeZone timeZone) {
        if ("time".equals(dateFormat) || "long".equals(dateFormat) || "millis".equals(dateFormat)) {
            useUnixMillis = true;
        } else if ("unix".equals(dateFormat)) {
            useUnixTimestamp = true;
        } else if ("nanos".equals(dateFormat)) {
            useUnixNanos = true;
        } else {
            try {
                this.dateFormat = new SimpleDateFormat(dateFormat);
                this.dateFormat.setTimeZone(timeZone);
            } catch (IllegalArgumentException e) {
                this.dateFormat = new SimpleDateFormat(ConfigKey.DATE_FORMAT.getDefaultValue());
                this.dateFormat.setTimeZone(TimeZone.getTimeZone(ConfigKey.DATE_FORMAT_ZONE.getDefaultValue()));
            }
        }
    }

    /** {@inheritDoc} */
    public Class<?> getType() {
        return Date.class;
    }

    /** {@inheritDoc} */
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Deque<String> pPathParts, boolean jsonify) throws AttributeNotFoundException {
        if (!jsonify) {
            return pValue;
        }
        Date date = (Date) pValue;
        if (useUnixTimestamp) {
            return date.getTime() / 1000;
        }
        if (useUnixMillis) {
            return date.getTime();
        }
        if (useUnixNanos) {
            return date.getTime() * 1_000_000;
        }

        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathPart != null) {
            if (!"time".equals(pathPart)) {
                return pConverter.getValueFaultHandler().handleException(
                        new AttributeNotFoundException("A date accepts only a single inner path element " +
                                                       "of value 'time' (and not '" + pathPart + "')"));
            }
            return date.getTime();
        } else {
            return dateFormat.format(date);
        }
    }

    // Set the the date. The value must be either a <code>long</code> in which case the
    // it is converted directly to a date or a string formatted according to configured
    // date format.
    // This method is called for changing an existing date object, i.e. when it is called with a path to
    // date. Contrast this to the case, where the date is set directly (without a path). For this,
    // the StringToObjectConverter is responsible (along with its date parser)
    /** {@inheritDoc} */
    public Object setObjectValue(Converter<String> pConverter, Object pInner, String pAttribute, Object pValue)
            throws IllegalAccessException, InvocationTargetException {
        Date date = (Date) pInner;
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
                Date newDate = dateFormat.parse(pValue.toString());
                String oldValue = dateFormat.format(date);
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

    // For now, we only return dates;
    /** {@inheritDoc} */
    public boolean canSetValue() {
        return true;
    }
}
