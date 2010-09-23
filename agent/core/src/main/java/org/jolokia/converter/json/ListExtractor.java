package org.jolokia.converter.json;

import org.jolokia.converter.StringToObjectConverter;
import org.json.simple.JSONArray;

import javax.management.AttributeNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.List;
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
public class ListExtractor implements Extractor {

    public Class getType() {
        return List.class;
    }

    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pExtraArgs,boolean jsonify)
            throws AttributeNotFoundException {
        List list = (List) pValue;
        int length = pConverter.getCollectionLength(list.size());
        List ret;
        Iterator it = list.iterator();
        if (!pExtraArgs.isEmpty()) {
            int idx = Integer.parseInt(pExtraArgs.pop());
            return pConverter.extractObject(list.get(idx),pExtraArgs,jsonify);
        } else {
            if (jsonify) {
                ret = new JSONArray();
                for (int i = 0;i < length; i++) {
                    Object val = it.next();
                    ret.add(pConverter.extractObject(val,pExtraArgs,jsonify));
                }
                return ret;
            } else {
                return list;
            }
        }
    }

    public Object setObjectValue(StringToObjectConverter pConverter, Object pInner, String pAttribute, String pValueS)
            throws IllegalAccessException, InvocationTargetException {
        List list = (List) pInner;
        int idx;
        try {
            idx = Integer.parseInt(pAttribute);
        } catch (NumberFormatException exp) {
            throw new IllegalArgumentException("Non-numeric index for accessing collection " + pInner +
                    ". (index = " + pAttribute + ", value to set = " + pValueS + ")",exp);
        }

        // For a collection, we can infer the type within the collection. We are trying to fetch
        // the old value, and if set, we use its type. Otherwise, we simply use string as value.
        Object oldValue = list.get(idx);
        Object value =
                oldValue != null ?
                        pConverter.convertFromString(oldValue.getClass().getName(),pValueS) :
                        pValueS;
        list.set(idx,value);
        return oldValue;
    }

    public boolean canSetValue() {
        return true;
    }

}