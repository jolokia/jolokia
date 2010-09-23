package org.jolokia.converter.json;

import org.jolokia.converter.StringToObjectConverter;
import org.json.simple.JSONArray;

import javax.management.AttributeNotFoundException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
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
public class TabularDataExtractor implements Extractor {

    public Class getType() {
        return TabularData.class;
    }

    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue,
                         Stack<String> pExtraArgs,boolean jsonify) throws AttributeNotFoundException {
        TabularData td = (TabularData) pValue;

        if (!pExtraArgs.isEmpty()) {
            String index = pExtraArgs.pop();
            int idx = Integer.valueOf(index).intValue();
            CompositeData cd = getRow(idx, td.values().iterator());
            return pConverter.extractObject(cd,pExtraArgs,jsonify);
        } else {
            if (jsonify) {
                JSONArray ret = new JSONArray();
                for (CompositeData cd : (Collection <CompositeData>) td.values()) {
                    ret.add(pConverter.extractObject(cd,pExtraArgs,jsonify));
                }
                return ret;
            } else {
                return td;
            }
        }
    }

    public Object setObjectValue(StringToObjectConverter pConverter, Object pInner, String pAttribute, String pValue)
            throws IllegalAccessException, InvocationTargetException {
        throw new IllegalArgumentException("TabularData cannot be written to");
    }

    public boolean canSetValue() {
        return false;
    }

    private static CompositeData getRow(int idx, Iterator it) {
        try {
            for (int i = 0; i < idx; i++) {
                it.next();
            }
        } catch (NoSuchElementException ex) {
             throw new IllegalArgumentException(
                     "Index " + idx + " out of range. Ex: " + ex.toString(),ex);
        }
        return (CompositeData) it.next();
    }

}