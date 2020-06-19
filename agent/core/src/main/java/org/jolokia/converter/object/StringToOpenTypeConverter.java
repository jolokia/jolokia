package org.jolokia.converter.object;

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
public class StringToOpenTypeConverter extends OpenTypeConverter {

    // List of converters used
    private List<OpenTypeConverter<? extends OpenType>> converters;

    /**
     * Constructor
     *
     * @param pStringToObjectConverter converter for the 'leaf' values.
     */
    public StringToOpenTypeConverter(StringToObjectConverter pStringToObjectConverter) {
        super(null);
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
     * @param openType target type
     * @param pValue value to convert from
     * @return the converted value
     */
    @SuppressWarnings("unchecked")
    @Override
	public Object convertToObject(OpenType openType, Object pValue) {
        if (pValue == null) {
            return null;
        } else {
            for (OpenTypeConverter converter : converters) {
                if (converter.canConvert(openType)) {
                    return converter.convertToObject(openType,pValue);
                }
            }
            throw new IllegalArgumentException(
                    "Cannot convert " + pValue + " to " + openType + ": " + "No converter could be found");
        }
	}

    /**
     * This converter is the parent converter can hence can convert
     * all open types
     * @param pType type (ignored)
     * @return always true
     */
    @Override
    boolean canConvert(OpenType pType) {
        return true;
    }


    public StringToOpenTypeConverter makeForgiving() {
        this.forgiving = true;
        return this;
    }
}
