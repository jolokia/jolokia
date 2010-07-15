package org.jolokia.converter.json;

import org.jolokia.converter.StringToObjectConverter;
import org.json.simple.JSONArray;

import javax.management.AttributeNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
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