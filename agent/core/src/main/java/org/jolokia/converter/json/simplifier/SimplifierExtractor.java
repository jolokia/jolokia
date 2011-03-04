package org.jolokia.converter.json.simplifier;

import org.jolokia.converter.StringToObjectConverter;
import org.jolokia.converter.json.Extractor;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.json.simple.JSONObject;

import javax.management.AttributeNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/*
 *  Copyright 2009-2010 Roland Huss
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


/**
 * @author roland
 * @since Jul 27, 2009
 */
abstract class SimplifierExtractor<T> implements Extractor {

    private Map<String, AttributeExtractor<T>> extractorMap;

    private Class<T> type;

    SimplifierExtractor(Class<T> pType) {
        extractorMap = new HashMap<String, AttributeExtractor<T>>();
        type = pType;
        init(extractorMap);
    }

    public Class getType() {
        return type;
    }

    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pExtraArgs, boolean jsonify)
            throws AttributeNotFoundException {
        if (pExtraArgs.size() > 0) {
            String element = pExtraArgs.pop();
            AttributeExtractor<T> extractor = extractorMap.get(element);
            if (extractor == null) {
                throw new IllegalArgumentException("Illegal path element " + element + " for object " + pValue);
            }

            Object attributeValue = null;
            try {
                attributeValue = extractor.extract((T) pValue);
                return pConverter.extractObject(attributeValue,pExtraArgs,jsonify);
            } catch (SkipAttributeException e) {
                throw new IllegalArgumentException("Illegal path element " + element + " for object " + pValue,e);
            }
        } else {
            if (jsonify) {
                JSONObject ret = new JSONObject();
                for (Map.Entry<String, AttributeExtractor<T>> entry : extractorMap.entrySet()) {
                    Object value = null;
                    try {
                        value = entry.getValue().extract((T) pValue);
                    } catch (SkipAttributeException e) {
                        // Skip this one ...
                        continue;
                    }
                    ret.put(entry.getKey(),
                            pConverter.extractObject(value,pExtraArgs,jsonify));
                }
                return ret;
            } else {
                return pValue;
            }
        }
    }

    // No setting for simplifying handlers
    public boolean canSetValue() {
        return false;
    }

    public Object setObjectValue(StringToObjectConverter pConverter, Object pInner,
                                 String pAttribute, Object pValue) throws IllegalAccessException, InvocationTargetException {
        // never called
        throw new IllegalArgumentException("A simplify handler can't set a value");
    }

    @SuppressWarnings("unchecked")
    protected void addExtractors(Object[][] pAttrExtractors) {
        for (int i = 0;i< pAttrExtractors.length; i++) {
            extractorMap.put((String) pAttrExtractors[i][0],
                             (AttributeExtractor<T>) pAttrExtractors[i][1]);
        }
    }


    // ============================================================================
    interface AttributeExtractor<T> {
        Object extract(T value) throws SkipAttributeException;
    }

    static class SkipAttributeException extends Exception {}

    // Add extractors to map
    abstract void init(Map<String, AttributeExtractor<T>> pExtractorMap);
}
