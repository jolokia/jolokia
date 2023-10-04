package org.jolokia.service.serializer.json.simplifier;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.AttributeNotFoundException;

import org.jolokia.server.core.service.serializer.ValueFaultHandler;
import org.jolokia.service.serializer.json.Extractor;
import org.jolokia.service.serializer.json.ObjectToJsonConverter;
import org.jolokia.service.serializer.object.StringToObjectConverter;
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
public abstract class SimplifierExtractor<T> implements Extractor {

    private final Map<String, AttributeExtractor<T>> extractorMap;

    private final Class<T> type;

    /**
     * Super constructor taking the value type as argument
     *
     * @param pType type for which this extractor is responsible
     */
    protected SimplifierExtractor(Class<T> pType) {
        extractorMap = new HashMap<>();
        type = pType;
        // Old method, here only for backwards compatibility. Please initialize in the constructor instead
        init(extractorMap);
    }

    /** {@inheritDoc} */
    public Class<?> getType() {
        return type;
    }

    /** {@inheritDoc} */
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pPathParts, boolean jsonify)
            throws AttributeNotFoundException {
        String path = pPathParts.isEmpty() ? null : pPathParts.pop();
        ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
        if (path != null) {
            return extractWithPath(pConverter, pValue, pPathParts, jsonify, path, faultHandler);
        } else {
            //noinspection unchecked
            return jsonify ? extractAll(pConverter, (T) pValue, pPathParts, jsonify) : pValue;
        }
    }

    private Object extractAll(ObjectToJsonConverter pConverter, T pValue, Stack<String> pPathParts, boolean jsonify) throws AttributeNotFoundException {
        JSONObject ret = new JSONObject();
        for (Map.Entry<String, AttributeExtractor<T>> entry : extractorMap.entrySet()) {
            @SuppressWarnings("unchecked")
            Stack<String> paths = (Stack<String>) pPathParts.clone();
            try {
                Object value = entry.getValue().extract(pValue);
                //noinspection unchecked
                ret.put(entry.getKey(),pConverter.extractObject(value, paths, jsonify));
            } catch (AttributeExtractor.SkipAttributeException | ValueFaultHandler.AttributeFilteredException e) {
                // Skip this one
            }
        }
        if (ret.isEmpty()) {
            // Everything filtered, bubble up ...
            throw new ValueFaultHandler.AttributeFilteredException();
        }
        return ret;
    }

    private Object extractWithPath(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pPathParts, boolean jsonify, String pPath, ValueFaultHandler pFaultHandler) throws AttributeNotFoundException {
        AttributeExtractor<T> extractor = extractorMap.get(pPath);
        if (extractor == null) {
            return pFaultHandler.handleException(new AttributeNotFoundException("Illegal path element " + pPath + " for object " + pValue));
        }

        try {
            @SuppressWarnings("unchecked")
            Object attributeValue = extractor.extract((T) pValue);
            return pConverter.extractObject(attributeValue, pPathParts, jsonify);
        } catch (AttributeExtractor.SkipAttributeException e) {
            return pFaultHandler.handleException(new AttributeNotFoundException("Illegal path element " + pPath + " for object " + pValue));
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
    protected final void addExtractors(Object[][] pAttrExtractors) {
        for (Object[] pAttrExtractor : pAttrExtractors) {
            extractorMap.put((String) pAttrExtractor[0],
                             (AttributeExtractor<T>) pAttrExtractor[1]);
        }
    }

    /**
     * Add a single extractor
     * @param pName name of the extractor
     * @param pExtractor the extractor itself
     */
    protected final void addExtractor(String pName, AttributeExtractor<T> pExtractor) {
        extractorMap.put(pName,pExtractor);
    }

    // ============================================================================

    /**
     * Helper interface for extracting and simplifying values
     *
     * @param <T> type to extract
     */
    public interface AttributeExtractor<T> {
        /**
         * Exception to be thrown when the result of this extractor should be omitted in the response
         */
        class SkipAttributeException extends Exception {}

        /**
         * Extract the real value from a given value
         * @param value to extract from
         * @return the extracted value
         * @throws SkipAttributeException if this value which is about to be extracted
         *                                should be omitted in the result
         */
        Object extract(T value) throws SkipAttributeException;
    }


    /**
     * Add extractors to map
     *
     * @deprecated Initialize in the constructor instead.
     * @param pExtractorMap the map to add the extractors used within this simplifier
     */
     void init(Map<String, AttributeExtractor<T>> pExtractorMap) {}
}
