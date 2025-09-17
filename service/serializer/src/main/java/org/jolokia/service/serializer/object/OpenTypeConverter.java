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
package org.jolokia.service.serializer.object;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.management.openmbean.*;

import org.jolokia.json.JSONObject;
import org.jolokia.json.JSONStructure;
import org.jolokia.json.parser.JSONParser;
import org.jolokia.json.parser.ParseException;

/**
 * Abstract base class for all converters to one of four {@link OpenType open types}.
 *
 * @author roland
 * @since 28.09.11
 */
abstract class OpenTypeConverter<T extends OpenType<?>> implements Converter<T> {

    protected boolean forgiving = false;

    // parent converter, that can be called for inner elements of given OpenType
    protected final ObjectToOpenTypeConverter objectToOpenTypeConverter;

    /**
     * Constructor which needs the <em>parent</em> converter. This parent converter
     * can be used to dispatch conversion back for inner objects when it comes
     * to convert collection types (like {@link CompositeType} or {@link ArrayType})
     *
     * @param objectToOpenTypeConverter
     */
    OpenTypeConverter(ObjectToOpenTypeConverter objectToOpenTypeConverter) {
        this.objectToOpenTypeConverter = objectToOpenTypeConverter;
    }

    /**
     * Check whether this converter can convert a value to an object of the given {@link OpenType}
     *
     * @param pType type to check
     * @return true if this convert can create objects of the given type
     */
    abstract boolean canConvert(OpenType<?> pType);

    /**
     * Get a name for given {@link OpenType} to be used when creating composite type names
     * @param type
     * @return
     */
    protected static String getMXBeanTypeName(OpenType<?> type) {
        if (type.isArray()) {
            // [I -> java.lang.Integer[]
            return getMXBeanTypeName(((ArrayType<?>) type).getElementOpenType());
        } else {
            return type.getTypeName();
        }
    }

    /**
     * Convert a value to {@link JSONStructure}. The given object must be either a valid JSON string or of type
     * {@link JSONStructure}, in which case it is returned directly if it matches desired target type.
     *
     * @param pValue the value to parse (or to return directly if it is a {@link JSONStructure}
     * @return the resulting value
     */
    protected <J extends JSONStructure> J toJSON(Object pValue, Class<J> jsonType) {
        Class<?> givenClass = pValue.getClass();
        if (givenClass == jsonType) {
            return jsonType.cast(pValue);
        } else if (pValue instanceof String) {
            try {
                return new JSONParser().parse((String) pValue, jsonType);
            } catch (ParseException | IOException e) {
                throw new IllegalArgumentException("Cannot parse JSON " + trim((String) pValue) + ": " + e.getMessage(), e);
            }
        }
        throw new IllegalArgumentException("Given value " + pValue + " cannot be parsed to a " + jsonType.getName() + " object");
    }

    /**
     * Check if the generic {@link Map} uses only String-based keys
     *
     * @param pValue
     * @param identities to track recursive instances - invoke first with empty set
     */
    protected void ensureStringKeys(Map<?, ?> pValue, Set<Integer> identities) {
        boolean added = identities.add(System.identityHashCode(pValue));
        if (!added) {
            return;
        }
        for (Map.Entry<?, ?> e : pValue.entrySet()) {
            if (!(e.getKey() instanceof String)) {
                throw new IllegalArgumentException("Can't process Map with non-String keys");
            }
            if (e.getValue() instanceof Map) {
                ensureStringKeys((Map<?, ?>) e.getValue(), identities);
            }
        }
    }

    protected Map<String, Object> toMap(Object pValue) {
        if (pValue instanceof JSONObject) {
            return (JSONObject) pValue;
        } else if (pValue instanceof Map) {
            // we need to check if the keys are Strings - no way we can handle other keys and we
            // can also catch some JMX violations here
            ensureStringKeys((Map<?, ?>) pValue, new HashSet<>());
            //noinspection unchecked
            return (Map<String, Object>) pValue;
        } else {
            return toJSON(pValue, JSONObject.class);
        }
    }

    /**
     * @return whether I accept (and ignore) values that are not in the target type
     */
    protected boolean isForgiving() {
        return this.forgiving || (this.objectToOpenTypeConverter != null && this.objectToOpenTypeConverter.isForgiving());
    }

    /**
     * Trims non-null value before printing.
     * @param pValue
     * @return
     */
    protected String trim(String pValue) {
        return pValue.length() > 64 ? pValue.substring(0, 64) + "..." : pValue;
    }

}
