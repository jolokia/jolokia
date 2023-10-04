package org.jolokia.service.serializer.object;

import java.util.Arrays;
import java.util.List;

import javax.management.openmbean.OpenType;


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


/**
 * Converter which converts an string or JSON representation to
 * an object represented by an {@link OpenType}.
 *
 * @author Assaf Berg, roland
 * @since 02.08.11
 */
public class OpenTypeDeserializer {

    protected boolean forgiving = false;

    // List of converters used
    @SuppressWarnings("rawtypes")
    private final List<OpenTypeConverter<? extends OpenType>> converters;

    /**
     * Constructor
     *
     * @param pStringToObjectConverter converter for the 'leaf' values.
     */
    public OpenTypeDeserializer(StringToObjectConverter pStringToObjectConverter) {
        converters = Arrays.asList(
                new SimpleTypeConverter(this,pStringToObjectConverter),
                new ArrayTypeConverter(this),
                new CompositeTypeConverter(this),
                new TabularDataConverter(this));
    }

    /**
     * Handle conversion for OpenTypes. The value is expected to be in JSON (either
     * an {@link org.json.simple.JSONAware} object or its string representation.
     *
     * @param pOpenType target type
     * @param pValue value to convert from
     * @return the converted value
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object deserialize(OpenType pOpenType, Object pValue) {
        if (pValue == null) {
            return null;
        } else {
            for (OpenTypeConverter converter : converters) {
                if (converter.canConvert(pOpenType)) {
                    return converter.convertToObject(pOpenType,pValue);
                }
            }
            throw new IllegalArgumentException(
                    "Cannot convert " + pValue + " to " + pOpenType + ": " + "No converter could be found");
        }
	}

    public void makeForgiving() {
        this.forgiving = true;
    }

    public boolean isForgiving() {
        return forgiving;
    }

}
