package org.jolokia.converter.object;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import javax.management.openmbean.*;

import org.json.simple.JSONAware;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Abstract base class for all open type converters
 *
 * @author roland
 * @since 28.09.11
 */
abstract class OpenTypeConverter<T extends OpenType> {

    // parent converter
    private OpenTypeConverter dispatcher;

    /**
     * Constructor which need the parent converter. This parent converter
     * can be used to dispatch conversion back for inner objects when it comes
     * to convert collection types (like {@link CompositeType} or {@link ArrayType})
     * @param pDispatcher
     */
    OpenTypeConverter(OpenTypeConverter pDispatcher) {
        dispatcher = pDispatcher;
    }

    /**
     * Check whether this converter can convert a string representation to
     * an object of the given type
     *
     * @param pType type to check
     * @return true if this convert can create objects of the given type
     */
    abstract boolean canConvert(OpenType pType);

    /**
     * Convert string/JSON representation to an open type object of the given type.
     *
     * @param pType type to convert to
     * @param pFrom original data to convert from
     * @return the converted open data
     */
    abstract Object convertToObject(T pType, Object pFrom);

    /**
     * Convert to JSON. The given object must be either a valid JSON string or of type {@link JSONAware}, in which
     * case it is returned directly
     *
     * @param pValue the value to parse (or to return directly if it is a {@link JSONAware}
     * @return the resulting value
     */
    protected JSONAware toJSON(Object pValue) {
        Class givenClass = pValue.getClass();
        if (JSONAware.class.isAssignableFrom(givenClass)) {
            return (JSONAware) pValue;
        } else {
            try {
                return (JSONAware) new JSONParser().parse(pValue.toString());
            } catch (ParseException e) {
                throw new IllegalArgumentException("Cannot parse JSON " + pValue + ": " + e,e);
            } catch (ClassCastException exp) {
                throw new IllegalArgumentException("Given value " + pValue.toString() +
                                                   " cannot be parsed to JSONAware object: " + exp,exp);
            }
        }
    }

    /**
     * Get the dispatcher converter
     * @return dispatcher
     */
    protected OpenTypeConverter getDispatcher() {
        return dispatcher;
    }
}
