/*
 * Copyright 2009-2011 Roland Huss
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.jolokia.service.serializer.object;

import java.util.Arrays;
import java.util.List;

import javax.management.openmbean.OpenType;

/**
 * <p>Dedicated {@link Converter} which specifies target type as {@link OpenType}. It handles null values
 * and delegates non-null values to one of four implementations of {@link OpenTypeConverter} for this fixed
 * list of {@liok OpenType open types}:<ul>
 *     <li>{@link javax.management.openmbean.SimpleType}</li>
 *     <li>{@link javax.management.openmbean.ArrayType}</li>
 *     <li>{@link javax.management.openmbean.CompositeType}</li>
 *     <li>{@link javax.management.openmbean.TabularType}</li>
 * </ul>
 * </p>
 *
 * <p>Delegation to {@link OpenTypeConverter} introduces some constraints on the type of values that can be converted.
 * <ul>
 *     <li>{@link SimpleTypeConverter} handles simple types and it delegates (<em>back</em>) to generic
 *     {@link ObjectToObjectConverter}.</li>
 *     <li>{@link ArrayTypeConverter} handles multidimensional arrays of objects, so the value being converted
 *     has to be a {@link java.util.Collection} or a String parsed using {@link org.jolokia.json.parser.JSONParser}
 *     into a {@link org.jolokia.json.JSONArray}.</li>
 *     <li>{@link CompositeTypeConverter} handles {@link java.util.Map} values (possibly parsed from strings into
 *     {@link org.jolokia.json.JSONObject}) and converts them to {@link javax.management.openmbean.CompositeData}
 *     according to information specified in {@link javax.management.openmbean.CompositeType}.</li>
 *     <li>{@link TabularDataConverter} handles {@link javax.management.openmbean.TabularType}</li>
 * </ul>
 * </p>
 *
 * @author Assaf Berg, roland
 * @since 02.08.11
 */
public class ObjectToOpenTypeConverter implements Converter<OpenType<?>> {

    protected final boolean forgiving;

    // List of converters used
    private final List<OpenTypeConverter<? extends OpenType<?>>> converters;

    /**
     * Constructor
     *
     * @param pObjectToObjectConverter converter for the <em>leaf</em> values which do not contain inner values.
     */
    public ObjectToOpenTypeConverter(Converter<String> pObjectToObjectConverter, boolean pForgiving) {
        converters = Arrays.asList(
            new SimpleTypeConverter(this, pObjectToObjectConverter),
            new ArrayTypeConverter(this),
            new CompositeTypeConverter(this),
            new TabularDataConverter(this)
        );
        this.forgiving = pForgiving;
    }

    /**
     * Handle conversion for OpenTypes. The value is expected to be in JSON (either
     * a {@link org.jolokia.json.JSONStructure} object or its string representation.
     *
     * @param pOpenType target type
     * @param pValue value to convert from
     * @return the converted value
     */
    @Override
    public Object convert(OpenType<?> pOpenType, Object pValue) {
        if (pValue == null) {
            return null;
        } else {
            for (OpenTypeConverter<? extends OpenType<?>> converter : converters) {
                if (converter.canConvert(pOpenType)) {
                    return invokeConverter(converter, pOpenType, pValue);
                }
            }
            throw new IllegalArgumentException(
                    "Cannot convert " + pValue + " to " + pOpenType + ": " + "No converter could be found");
        }
	}

    // capture helper trick
    private <T extends OpenType<?>> Object invokeConverter(OpenTypeConverter<T> converter, OpenType<?> pOpenType, Object pValue) {
        @SuppressWarnings("unchecked")
        T casted = (T) pOpenType;
        return converter.convert(casted, pValue);
    }

    public boolean isForgiving() {
        return forgiving;
    }

}
