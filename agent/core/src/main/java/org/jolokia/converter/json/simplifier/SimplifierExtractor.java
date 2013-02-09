package org.jolokia.converter.json.simplifier;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.json.Extractor;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.converter.object.StringToObjectConverter;
import org.json.simple.JSONObject;

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
 * Base class for all simplifiers. A simplifier is a special {@link Extractor} which
 * condense full blown Java beans (like {@link java.io.File}) to a more compact representation.
 * Simplifier extractors cannot be written to and are only used for downstream serialization.
 *
 * Simplifier are registered by listing the classes in a <code>META-INF/simplifiers</code> plain text file and
 * then picked up by the converter. The default simplifiers coming prepackaged are taken from
 * <code>META-INF/simplifiers-default</code>
 *
 * @author roland
 * @since Jul 27, 2009
 */
abstract class SimplifierExtractor<T> implements Extractor {

    private Map<String, AttributeExtractor<T>> extractorMap;

    private Class<T> type;

    /**
     * Super constructor taking the value type as argument
     *
     * @param pType type for which this extractor is responsible
     */
    SimplifierExtractor(Class<T> pType) {
        extractorMap = new HashMap<String, AttributeExtractor<T>>();
        type = pType;
        init(extractorMap);
    }

    /** {@inheritDoc} */
    public Class getType() {
        return type;
    }

    /** {@inheritDoc} */
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pExtraArgs, boolean jsonify)
            throws AttributeNotFoundException {
        if (pExtraArgs.size() > 0) {
            String element = pExtraArgs.pop();
            AttributeExtractor<T> extractor = extractorMap.get(element);
            if (extractor == null) {
                throw new IllegalArgumentException("Illegal path element " + element + " for object " + pValue);
            }

            try {
                Object attributeValue = extractor.extract((T) pValue);
                return pConverter.extractObject(attributeValue, pExtraArgs, jsonify);
            } catch (AttributeExtractor.SkipAttributeException e) {
                throw new IllegalArgumentException("Illegal path element " + element + " for object " + pValue,e);
            }
        } else {
            if (jsonify) {
                JSONObject ret = new JSONObject();
                for (Map.Entry<String, AttributeExtractor<T>> entry : extractorMap.entrySet()) {
                    try {
                        Object value = entry.getValue().extract((T) pValue);
                        ret.put(entry.getKey(),pConverter.extractObject(value, pExtraArgs, jsonify));
                    } catch (AttributeExtractor.SkipAttributeException e) {
                        // Skip this one ...
                        continue;
                    }
                }
                return ret;
            } else {
                return pValue;
            }
        }
    }

    /**
     * No setting for simplifying extractors
     * @return always <code>false</code>
     */
    public boolean canSetValue() {
        return false;
    }

    /**
     * Throws always {@link IllegalArgumentException} since a simplifier cannot be written to
     */
    public Object setObjectValue(StringToObjectConverter pConverter, Object pInner,
                                 String pAttribute, Object pValue) throws IllegalAccessException, InvocationTargetException {
        // never called
        throw new IllegalArgumentException("A simplify handler can't set a value");
    }

    /**
     * Add given extractors to the map. Should be called by a subclass from within init()
     *
     * @param pAttrExtractors extractors
     */
    @SuppressWarnings("unchecked")
    protected void addExtractors(Object[][] pAttrExtractors) {
        for (int i = 0;i< pAttrExtractors.length; i++) {
            extractorMap.put((String) pAttrExtractors[i][0],
                             (AttributeExtractor<T>) pAttrExtractors[i][1]);
        }
    }


    // ============================================================================

    /**
     * Helper interface for extracting and simplifying values
     *
     * @param <T> type to extract
     */
    interface AttributeExtractor<T> {
        /**
         * Extract the real value from a given value
         * @param value to extract from
         * @return the extracted value
         * @throws SkipAttributeException if this value which is about to be extracted
         *                                should be omitted in the result
         */
        Object extract(T value) throws AttributeExtractor.SkipAttributeException;

        /**
         * Exception to be thrown when the result of this extractor should be ommitted in the response
         */
        class SkipAttributeException extends Exception {}
    }


    /**
     * Add extractors to map
     * @param pExtractorMap the map to add the extractors used within this simplifier
     */
    abstract void init(Map<String, AttributeExtractor<T>> pExtractorMap);
}
