package org.jolokia.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;

/**
 * Utility used for date handling
 *
 * @author roland
 * @since 17.04.11
 */
public class DateUtil {

    // Dateformat for output
    private final static SimpleDateFormat ISO8601_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

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
        ISO8601_FORMAT.setTimeZone(TimeZone.getDefault());
    }

    /**
     * Parse an ISO-8601 string into an date object
     *
     * @param pDateString date string to parse
     * @return the parse date
     * @throws IllegalArgumentException if the provided string does not conform to ISO-8601
     */
    public static Date fromISO8601(String pDateString) {
        if (datatypeFactory == null) {
            return datatypeFactory.newXMLGregorianCalendar(pDateString.trim()).toGregorianCalendar().getTime();
        } else {
            try {
                // Try on our own, works for most cases
                String date = pDateString.replaceFirst("\\+(0\\d)\\:(\\d{2})$", "+$1$2");
                date = date.replaceFirst("Z$","+0000");
                return ISO8601_FORMAT.parse(date);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Cannot parse date '" + pDateString + "': " +e,e);
            }
        }
    }

    /**
     * Convert a given date to an ISO-8601 compliant string
     * representation with the given timezone or the default timeszone
     * if tz is null
     *
     * @param pDate date to convert
     * @return the ISO-8601 representation of the date
     */
    public static String toISO8601(Date pDate) {
        String ret = ISO8601_FORMAT.format(pDate);
        ret = ret.replaceAll("\\+0000$", "Z");
        return ret.replaceAll("(\\d\\d)$", ":$1");
    }
}
