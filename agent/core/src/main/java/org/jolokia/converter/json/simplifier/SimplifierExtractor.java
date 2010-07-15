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
 * jmx4perl - WAR Agent for exporting JMX via JSON
 *
 * Copyright (C) 2009 Roland Huß, roland@cpan.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * A commercial license is available as well. Please contact roland@cpan.org for
 * further details.
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
                                 String pAttribute, String pValue) throws IllegalAccessException, InvocationTargetException {
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
