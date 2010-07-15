package org.jolokia.converter.json;

import org.jolokia.converter.StringToObjectConverter;
import org.json.simple.JSONObject;

import javax.management.AttributeNotFoundException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.InvalidKeyException;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
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
public class CompositeDataExtractor implements Extractor {

    public Class getType() {
        return CompositeData.class;
    }

    @SuppressWarnings("PMD.PreserveStackTrace")
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue,
                         Stack<String> pExtraArgs,boolean jsonify) throws AttributeNotFoundException {
        CompositeData cd = (CompositeData) pValue;

        if (!pExtraArgs.isEmpty()) {
            String key = pExtraArgs.pop();
            try {
                return pConverter.extractObject(cd.get(key),pExtraArgs,jsonify);
            }  catch (InvalidKeyException exp) {
                throw new AttributeNotFoundException("Invalid path '" + key + "'");
            }
        } else {
            if (jsonify) {
                JSONObject ret = new JSONObject();
                for (String key : (Set<String>) cd.getCompositeType().keySet()) {
                    ret.put(key,pConverter.extractObject(cd.get(key),pExtraArgs,jsonify));
                }
                return ret;
            } else {
                return cd;
            }
        }
    }

    public Object setObjectValue(StringToObjectConverter pConverter, Object pInner, String pAttribute, String pValue)
            throws IllegalAccessException, InvocationTargetException {
        throw new IllegalArgumentException("CompositeData cannot be written to");
    }

    public boolean canSetValue() {
        return false;
    }
}