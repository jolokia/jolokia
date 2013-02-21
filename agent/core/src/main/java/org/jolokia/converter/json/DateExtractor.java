package org.jolokia.converter.json;

import java.lang.reflect.InvocationTargetException;
import java.util.Date;
import java.util.Stack;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.StringToObjectConverter;
import org.jolokia.util.DateUtil;

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

    /** {@inheritDoc} */
    public Class getType() {
        return Date.class;
    }

    /** {@inheritDoc} */
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pExtraArgs, boolean jsonify) throws AttributeNotFoundException {
        if (!jsonify) {
            return pValue;
        }
        Date date = (Date) pValue;
        if (!pExtraArgs.isEmpty()) {
            String attribute = pExtraArgs.pop();
            if (!"time".equals(attribute)) {
                throw new IllegalArgumentException("A date accepts only a single inner path element " +
                                                   "of value 'time' (and not '" + attribute + "'");
            }
            return date.getTime();
        } else {
            return DateUtil.toISO8601(date);
        }
    }

    // Set the the date. The value must be either a <code>long</code> in which case the
    // it is converted directly to a date or a ISO8601 formatted string.
    // This method is called for changing an existing date object, i.e. when it is called with a path to
    // date. Contrast this to the case, where the date is set directly (without a path). For this,
    // the StringToObjectConverter is responsible (along with its date parser)
    /** {@inheritDoc} */
    public Object setObjectValue(StringToObjectConverter pConverter, Object pInner, String pAttribute, Object pValue)
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
        }
        throw new UnsupportedOperationException("Setting of date values is not yet supported directly. " +
                                                        "Use a path/attribute 'time' to set the epoch seconds on a date");
    }


    // For now, we only return dates;
    /** {@inheritDoc} */
    public boolean canSetValue() {
        return true;
    }
}
