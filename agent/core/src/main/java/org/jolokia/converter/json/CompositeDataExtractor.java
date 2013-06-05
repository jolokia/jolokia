package org.jolokia.converter.json;

import org.jolokia.converter.object.StringToObjectConverter;
import org.json.simple.JSONObject;

import javax.management.AttributeNotFoundException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.InvalidKeyException;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.Stack;

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
    public Class getType() {
        return CompositeData.class;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("PMD.PreserveStackTrace")
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue,
                         Stack<String> pExtraArgs,boolean jsonify) throws AttributeNotFoundException {
        CompositeData cd = (CompositeData) pValue;

        if (!pExtraArgs.isEmpty()) {
            String key = pExtraArgs.pop();
            try {
                return pConverter.extractObject(cd.get(key), pExtraArgs, jsonify);
            }  catch (InvalidKeyException exp) {
                throw new AttributeNotFoundException("Invalid path '" + key + "'");
            }
        } else {
            if (jsonify) {
                JSONObject ret = new JSONObject();
                for (String key : (Set<String>) cd.getCompositeType().keySet()) {
                    ret.put(key,pConverter.extractObject(cd.get(key), pExtraArgs, jsonify));
                }
                return ret;
            } else {
                return cd;
            }
        }
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