package org.jolokia.converter.json;

import org.jolokia.converter.object.StringToObjectConverter;
import org.json.simple.JSONObject;

import javax.management.AttributeNotFoundException;
import java.lang.reflect.InvocationTargetException;
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
 * @since Apr 19, 2009
 */
public class MapExtractor implements Extractor {
    private static final int MAX_STRING_LENGTH = 400;

    public Class getType() {
        return Map.class;
    }

    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue,
                         Stack<String> pExtraArgs,boolean jsonify) throws AttributeNotFoundException {
        Map<Object,Object> map = (Map<Object,Object>) pValue;
        int length = pConverter.getCollectionLength(map.size());
        if (!pExtraArgs.isEmpty()) {
            String decodedKey = pExtraArgs.pop();
            for (Map.Entry entry : map.entrySet()) {
                // We dont access the map via a lookup since the key
                // are potentially object but we have to deal with string
                // representations
                if(decodedKey.equals(entry.getKey().toString())) {
                    return pConverter.extractObject(entry.getValue(), pExtraArgs, jsonify);
                }
            }
            throw new IllegalArgumentException("Map key '" + decodedKey +
                    "' is unknown for map " + trimString(pValue.toString()));
        } else {
            if (jsonify) {
                JSONObject ret = new JSONObject();
                int i = 0;
                for(Map.Entry entry : map.entrySet()) {
                    ret.put(entry.getKey(),
                            pConverter.extractObject(entry.getValue(), pExtraArgs, jsonify));
                    i++;
                    if (i > length) {
                        break;
                    }
                }
                return ret;
            } else {
                return map;
            }
        }
    }

    public Object setObjectValue(StringToObjectConverter pConverter, Object pInner, String pAttribute, Object pValue)
            throws IllegalAccessException, InvocationTargetException {
        Map<Object,Object> map = (Map<Object,Object>) pInner;
        Object oldValue = null;
        Object oldKey = pAttribute;
        for (Map.Entry entry : map.entrySet()) {
            // We dont access the map via a lookup since the key
            // are potentially object but we have to deal with string
            // representations
            if(pAttribute.equals(entry.getKey().toString())) {
                oldValue = entry.getValue();
                oldKey = entry.getKey();
                break;
            }
        }
        Object value =
                oldValue != null ?
                        pConverter.prepareValue(oldValue.getClass().getName(), pValue) :
                        pValue;
        map.put(oldKey,value);
        return oldValue;
    }

    public boolean canSetValue() {
        return true;
    }

    private String trimString(String pString) {
        if (pString.length() > MAX_STRING_LENGTH) {
            return pString.substring(0, MAX_STRING_LENGTH) + " ...";
        } else {
            return pString;
        }
    }
}