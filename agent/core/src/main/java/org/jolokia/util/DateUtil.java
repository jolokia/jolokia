package org.jolokia.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

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
 * Utility used for date handling
 *
 * @author roland
 * @since 17.04.11
 */
public final class DateUtil {

    // factory used for conversion
    private static DatatypeFactory datatypeFactory;

    private DateUtil() { }

    // Use XML DataType factory if available
    static {
        try {
            datatypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            datatypeFactory = null;
        }
    }

    /**
     * Parse an ISO-8601 string into an date object
     *
     * @param pDateString date string to parse
     * @return the parse date
     * @throws IllegalArgumentException if the provided string does not conform to ISO-8601
     */
    public static Date fromISO8601(String pDateString) {
        if (datatypeFactory != null) {
            return datatypeFactory.newXMLGregorianCalendar(pDateString.trim()).toGregorianCalendar().getTime();
        } else {
            try {
                // Try on our own, works for most cases
                String date = pDateString.replaceFirst("\\+(0\\d)\\:(\\d{2})$", "+$1$2");
                date = date.replaceFirst("Z$","+0000");
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
                return dateFormat.parse(date);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Cannot parse date '" + pDateString + "': " +e,e);
            }
        }
    }

    /**
     * Convert a given date to an ISO-8601 compliant string
     * representation for the default timezone
     *
     * @param pDate date to convert
     * @return the ISO-8601 representation of the date
     */
    public static String toISO8601(Date pDate) {
        return toISO8601(pDate,TimeZone.getDefault());
    }

    /**
     * Convert a given date to an ISO-8601 compliant string
     * representation for a given timezone
     *
     * @param pDate date to convert
     * @param pTimeZone timezone to use
     * @return the ISO-8601 representation of the date
     */
    public static String toISO8601(Date pDate,TimeZone pTimeZone) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        dateFormat.setTimeZone(pTimeZone);
        String ret = dateFormat.format(pDate);
        ret = ret.replaceAll("\\+0000$", "Z");
        return ret.replaceAll("(\\d\\d)$", ":$1");
    }
}
