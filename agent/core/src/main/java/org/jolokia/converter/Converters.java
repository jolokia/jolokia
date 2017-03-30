package org.jolokia.converter;

import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.converter.object.StringToObjectConverter;
import org.jolokia.converter.object.StringToOpenTypeConverter;

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
 * Wrapper class holding various converters
 *
 * @author roland
 * @since 02.08.11
 */
public class Converters {

    // From object to json:
    private ObjectToJsonConverter toJsonConverter;

    // From string/json to object:
    private StringToObjectConverter toObjectConverter;
    private StringToOpenTypeConverter toOpenTypeConverter;

    /**
     * Create converters (string-to-object, string-to-openType and object-to-json)
     *
     */
    public Converters() {
        toObjectConverter = new StringToObjectConverter();
        toOpenTypeConverter = new StringToOpenTypeConverter(toObjectConverter);
        toJsonConverter = new ObjectToJsonConverter(toObjectConverter);
    }

    /**
     * Get the converter which is responsible for converting objects to JSON
     *
     * @return converter
     */
    public ObjectToJsonConverter getToJsonConverter() {
        return toJsonConverter;
    }

    /**
     * Get the converter which translates a given string value to a certain object (depending
     * on type)
     *
     * @return converter
     */
    public StringToObjectConverter getToObjectConverter() {
        return toObjectConverter;
    }

    /**
     * Get the converter for strings to {@link javax.management.openmbean.OpenType}
     *
     * @return converter
     */
    public StringToOpenTypeConverter getToOpenTypeConverter() {
        return toOpenTypeConverter;
    }
}
