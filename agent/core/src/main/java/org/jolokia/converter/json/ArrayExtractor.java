package org.jolokia.converter.json;

import org.jolokia.converter.StringToObjectConverter;
import org.json.simple.JSONArray;

import javax.management.AttributeNotFoundException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
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
 * @since Apr 19, 2009
 */
public class ArrayExtractor implements Extractor {

    public Class getType() {
        // Special handler, no specific Type
        return null;
    }

    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pExtraArgs,boolean jsonify) throws AttributeNotFoundException {
        int length = pConverter.getCollectionLength(Array.getLength(pValue));
        if (!pExtraArgs.isEmpty()) {
            Object obj = Array.get(pValue, Integer.parseInt(pExtraArgs.pop()));
            return pConverter.extractObject(obj,pExtraArgs,jsonify);
        } else {
            if (jsonify) {
                List<Object> ret = new JSONArray();
                for (int i=0;i<length;i++) {
                    Object obj = Array.get(pValue, i);
                    ret.add(pConverter.extractObject(obj,pExtraArgs,jsonify));
                }
                return ret;
            } else {
                return pValue;
            }
        }
    }

    public Object setObjectValue(StringToObjectConverter pConverter, Object pInner, String pAttribute, String pValueS)
            throws IllegalAccessException, InvocationTargetException {
        Class clazz = pInner.getClass();
        if (!clazz.isArray()) {
            throw new IllegalArgumentException("Not an array to set a value, but " + clazz +
                    ". (index = " + pAttribute + ", value = " + pValueS + ")");
        }
        int idx;
        try {
            idx = Integer.parseInt(pAttribute);
        } catch (NumberFormatException exp) {
            throw new IllegalArgumentException("Non-numeric index for accessing array " + pInner +
                    ". (index = " + pAttribute + ", value to set = " + pValueS + ")",exp);
        }
        Class type = clazz.getComponentType();
        Object value = pConverter.convertFromString(type.getName(),pValueS);
        Object oldValue = Array.get(pInner, idx);
        Array.set(pInner, idx, value);
        return oldValue;
    }

    public boolean canSetValue() {
        return true;
    }
}
