package org.jolokia.client.request;

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

import java.util.ArrayList;
import java.util.List;

import javax.management.ObjectName;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * A request dealing with a single MBean.
 *
 * @author roland
 * @since Apr 24, 2010
 */
public abstract class AbtractJ4pMBeanRequest extends J4pRequest {

    // name of MBean to execute a request on
    private ObjectName objectName;

    protected AbtractJ4pMBeanRequest(J4pType pType,ObjectName pMBeanName) {
        super(pType);
        objectName = pMBeanName;
    }

    /**
     * Get the object name for the MBean on which this request
     * operates
     *
     * @return MBean's name
     */
    public ObjectName getObjectName() {
        return objectName;
    }

    @Override
    List<String> getRequestParts() {
        List<String> ret = new ArrayList<String>();
        ret.add(objectName.getCanonicalName());
        return ret;
    }

    @Override
    JSONObject toJson() {
        JSONObject ret =  super.toJson();
        ret.put("mbean",objectName.getCanonicalName());
        return ret;
    }

    /**
     * Serialize an object to a string which can be uses as URL part in a GET request
     * when object should be transmitted <em>to</em> the agent. The serialization is
     * rather limited: If it is an array, the array's member's string representation are used
     * in a comma separated list (without escaping so far, so the strings must not contain any
     * commas themselfes). If it is not an array, the string representation ist used (<code>Object.toString()</code>)
     * Any <code>null</code> value is transformed in the special marker <code>[null]</code> which on the
     * agent side is converted back into a <code>null</code>.
     *
     * You should consider using POST requests when you need a more sophisticated JSON serialization.
     *
     * @param pArg the argument to serialize for an GET request
     * @return the string representation
     */
    protected String serializeArgumentToRequestPart(Object pArg) {
        if (pArg != null) {
            if (pArg.getClass().isArray()) {
                return getArrayForArgument((Object[]) pArg);
            } else if (List.class.isAssignableFrom(pArg.getClass())) {
                List list = (List) pArg;
                Object[] args = new Object[list.size()];
                int i = 0;
                for (Object e : list) {
                    args[i++] = e;
                }
                return getArrayForArgument(args);
            }
        }
        return nullEscape(pArg);
    }

    /**
     * Serialize an object to an string or JSON structure for write/exec POST requests.
     * Serialization is up to now rather limited:
     * <ul>
     *    <li>
     *      If the argument is of type {@see org.json.simple.JSONAware}, the it is used directly for inclusion
     *      in the POST request.
     *    </li>
     *    <li>
     *      If the argument is an array, this array's content is put into
     *      an {@see org.json.simple.JSONArray}, where each array member is serialized to it string representation.
     *      So this is only save for pure string arrays so far.
     *    </li>
     *    <li>
     *      If the argument is a map, it is transformed into a {@see org.json.simple.JSONObject} with the keys
     *      and values are used with the string representation of the map's content. So it is only save for a simple
     *      map with string keys and values
     *    </li>
     *    <li>
     *      Otherwise the object's <code>toString()</code> representatin is used (or <code>[null]</code> if it is null)
     *    </li>
     * </ul>
     *
     * Future version of this lib will probably provide a more sophisticated serialization mechanism.
     * <em>This is how it is supposed to be for the next release, currently a simplified serializatin is in place</em>
     * 
     * @param pArg the object to serialize
     * @return a JSON serialized object
     */
    protected Object serializeArgumentToJson(Object pArg) {
        if (pArg != null && pArg.getClass().isArray()) {
            JSONArray innerArray = new JSONArray();
            for (Object inner : (Object []) pArg) {
                innerArray.add(inner);
            }
            return innerArray;
        }
        else {
            return pArg;
        }
    }

    // ======================================================================================

    private String getArrayForArgument(Object[] pArg) {
        StringBuilder inner = new StringBuilder();
        for (int i = 0; i< pArg.length; i++) {
            inner.append(nullEscape(pArg[i]));
            if (i < pArg.length - 1) {
                inner.append(",");
            }
        }
        return inner.toString();
    }

    private String nullEscape(Object pArg) {
        if (pArg == null) {
            return "[null]";
        } else if (pArg instanceof String && ((String) pArg).length() == 0) {
            return "\"\"";
        } else {
            return pArg.toString();
        }
    }

}
