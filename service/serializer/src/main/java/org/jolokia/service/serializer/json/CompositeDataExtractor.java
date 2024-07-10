package org.jolokia.service.serializer.json;

import java.lang.reflect.InvocationTargetException;
import java.util.Deque;
import java.util.LinkedList;
import javax.management.AttributeNotFoundException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.InvalidKeyException;

import org.jolokia.server.core.service.serializer.ValueFaultHandler;
import org.jolokia.service.serializer.object.StringToObjectConverter;
import org.jolokia.json.JSONObject;

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
 * Extractor for {@link CompositeData}
 *
 * @author roland
 * @since Apr 19, 2009
 */
public class CompositeDataExtractor implements Extractor {

    /** {@inheritDoc} */
    public Class<?> getType() {
        return CompositeData.class;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("PMD.PreserveStackTrace")
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue,
                                Deque<String> pPathParts, boolean jsonify) throws AttributeNotFoundException {
        CompositeData cd = (CompositeData) pValue;

        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathPart != null) {
            try {
                return pConverter.extractObject(cd.get(pathPart), pPathParts, jsonify);
            } catch (InvalidKeyException exp) {
                return pConverter.getValueFaultHandler().handleException(new AttributeNotFoundException("Invalid path '" + pathPart + "'"));
            }
        } else {
            return jsonify ? extractCompleteCdAsJson(pConverter, cd, pPathParts) : cd;
        }
    }

    private Object extractCompleteCdAsJson(ObjectToJsonConverter pConverter, CompositeData pData, Deque<String> pPath) throws AttributeNotFoundException {
        JSONObject ret = new JSONObject();
        for (String key : pData.getCompositeType().keySet()) {
            Deque<String> path = new LinkedList<>(pPath);
            try {
                //noinspection unchecked
                ret.put(key, pConverter.extractObject(pData.get(key), path, true));
            } catch (ValueFaultHandler.AttributeFilteredException exp) {
                // Ignore this key;
            }
        }
        if (ret.isEmpty()) {
            // If every key was filtered, this composite data should be skipped completely
            throw new ValueFaultHandler.AttributeFilteredException();
        }
        return ret;
    }

    /** {@inheritDoc} */
    public Object setObjectValue(StringToObjectConverter pConverter, Object pInner, String pAttribute, Object pValue)
            throws IllegalAccessException, InvocationTargetException {
        throw new IllegalArgumentException("CompositeData cannot be written to");
    }

    /** {@inheritDoc} */
    public boolean canSetValue() {
        return false;
    }
}
