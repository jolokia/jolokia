package org.jolokia.handler;/*
 * 
 * Copyright 2015 Roland Huss
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

import java.util.Arrays;

import org.jolokia.config.ConfigKey;

/**
 * Format how to present the value of a request
 *
 * @author roland
 * @since 29/01/16
 */
public enum ValueFormat {

    PLAIN("plain"),
    TAG("tag");

    // the format has given in the configuration
    private String format;

    // Keys used for meta-data included when using the 'tag' format:
    public static final String KEY_VALUE = "jolokia.value";
    public static final String KEY_DOMAIN = "jolokia.domain";
    public static final String KEY_ATTRIBUTE = "jolokia.attribute";
    public static final String KEY_OPERATION = "jolokia.operation";
    public static final String KEY_ARGUMENTS = "jolokia.arguments";

    // Configuration key for the format
    public static ConfigKey KEY = ConfigKey.VALUE_FORMAT;

    ValueFormat(String pFormat) {
        this.format =  pFormat;
    }

    /**
     * Get the format from a string value
     *
     * @param pStringValue value to lookup
     * @return the enum found
     * @throws IllegalArgumentException if no such enum is known
     */
    public static ValueFormat parseString(String pStringValue) {
        for (ValueFormat format : ValueFormat.values()) {
            if (format.format.equals(pStringValue.toLowerCase())) {
                return format;
            }
        }
        throw new IllegalArgumentException("No format '" + pStringValue +
                                           "' available. Known formats: " + Arrays.asList(ValueFormat.values()));
    }
}
