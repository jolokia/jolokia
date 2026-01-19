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
package org.jolokia.converter.json;

import java.lang.reflect.InvocationTargetException;
import java.util.Deque;
import java.util.LinkedList;
import javax.management.AttributeNotFoundException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.InvalidKeyException;

import org.jolokia.core.service.serializer.ValueFaultHandler;
import org.jolokia.json.JSONObject;
import org.jolokia.converter.object.Converter;

/**
 * {@link org.jolokia.converter.json.ObjectAccessor} for {@link CompositeData}.
 *
 * @author roland
 * @since Apr 19, 2009
 */
public class CompositeDataAccessor implements org.jolokia.converter.json.ObjectAccessor {

    @Override
    public Class<?> getType() {
        return CompositeData.class;
    }

    @Override
    public Object extractObject(ObjectToJsonConverter pConverter, Object pCompositeData, Deque<String> pPathParts, boolean pJsonify)
            throws AttributeNotFoundException {
        CompositeData cd = (CompositeData) pCompositeData;

        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathPart != null) {
            try {
                return pConverter.extractObject(cd.get(pathPart), pPathParts, pJsonify);
            } catch (InvalidKeyException exp) {
                return pConverter.getValueFaultHandler().handleException(new AttributeNotFoundException("Invalid key for CompositeData '" + pathPart + "'"));
            }
        } else {
            return pJsonify ? compositeDataToJSON(pConverter, cd, pPathParts) : cd;
        }
    }

    @Override
    public boolean canSetValue() {
        return false;
    }

    @Override
    public Object setObjectValue(Converter<String> pConverter, Object pObject, String pAttribute, Object pValue) {
        throw new UnsupportedOperationException("CompositeData cannot be written to");
    }

    /**
     * Serialize given {@lin CompositeData into a {@link JSONObject}
     *
     * @param pConverter
     * @param pData
     * @param pPath
     * @return
     * @throws AttributeNotFoundException
     */
    private Object compositeDataToJSON(ObjectToJsonConverter pConverter, CompositeData pData, Deque<String> pPath)
            throws AttributeNotFoundException {
        JSONObject ret = new JSONObject();
        for (String key : pData.getCompositeType().keySet()) {
            Deque<String> paths = new LinkedList<>(pPath);
            try {
                // fortunately key is always a String
                ret.put(key, pConverter.extractObject(pData.get(key), paths, true));
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

}
