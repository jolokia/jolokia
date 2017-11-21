package org.jolokia.client.request;

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

import java.lang.reflect.Array;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.*;

/**
 * Request object abstracting a request to a j4p agent.
 *
 * @author roland
 * @since Apr 24, 2010
 */
public abstract class J4pRequest {


    // request type
    private J4pType type;

    // "GET" or "POST"
    private String preferredHttpMethod;

    // target configuration for this request when used as a JSR-160 proxy
    private J4pTargetConfig targetConfig;

    /**
     * Constructor for subclasses
     * @param pType type of this request
     * @param pTargetConfig a target configuration if used in proxy mode or <code>null</code>
     *                      if this is a direct request
     */
    protected J4pRequest(J4pType pType, J4pTargetConfig pTargetConfig) {
        type = pType;
        targetConfig = pTargetConfig;
    }

    /**
     * Escape a input (like the part of an path) so that it can be safely used
     * e.g. as a path
     *
     * @param pInput input to escape
     * @return the escaped input
     */
    public static String escape(String pInput) {
        return pInput.replaceAll("!","!!").replaceAll("/","!/");
    }

    /**
     * Get the type of the request
     *
     * @return request's type
     */
    public J4pType getType() {
        return type;
    }

    /**
     * Get a target configuration for use with an agent in JSR-160 proxy mode
     *
     * @return the target config or <code>null</code> if this is a direct request
     */
    public J4pTargetConfig getTargetConfig() {
        return targetConfig;
    }

    /**
     * The preferred HTTP method to use (either 'GET' or 'POST')
     * @return the HTTP method to use for this request, or <code>null</code> if the method should be automatically selected.
     */
    public String getPreferredHttpMethod() {
        return preferredHttpMethod;
    }

    /**
     * Set the preferred HTTP method, either 'GET' or 'POST'.
     *
     * @param pPreferredHttpMethod HTTP method to use.
     */
    public void setPreferredHttpMethod(String pPreferredHttpMethod) {
        preferredHttpMethod = pPreferredHttpMethod != null ? pPreferredHttpMethod.toUpperCase() : null;
    }

    // ==================================================================================================
    // Methods used for building up HTTP Requests and setting up the reponse
    // These methods are package visible only since are used only internally

    // Get the parts to build up a GET url (without the type as the first part)
    abstract List<String> getRequestParts();

    // Get a JSON representation of this request
    JSONObject toJson() {
        JSONObject ret = new JSONObject();
        ret.put("type",type.name());
        if (targetConfig != null) {
            ret.put("target",targetConfig.toJson());
        }
        return ret;
    }

    /**
     * Create a response from a given JSON response
     *
     * @param pResponse http response as obtained from the Http-Request
     * @return the create response
     */
    abstract <R extends J4pResponse<? extends J4pRequest>> R createResponse(JSONObject pResponse);

    // Helper class
    protected void addPath(List<String> pParts, String pPath) {
        if (pPath != null) {
            // Split up path
            pParts.addAll(splitPath(pPath));
        }
    }

    /**
     * Serialize an object to a string which can be uses as URL part in a GET request
     * when object should be transmitted <em>to</em> the agent. The serialization is
     * rather limited: If it is an array, the array's member's string representation are used
     * in a comma separated list (without escaping so far, so the strings must not contain any
     * commas themselves). If it is not an array, the string representation ist used (<code>Object.toString()</code>)
     * Any <code>null</code> value is transformed in the special marker <code>[null]</code> which on the
     * agent side is converted back into a <code>null</code>.
     * <p>
     * You should consider POST requests when you need a more sophisticated JSON serialization.
     * </p>
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
     *      If the argument is <code>null</code> null is returned.
     *    </li>
     *    <li>
     *      If the argument is of type {@see org.json.simple.JSONAware}, the it is used directly for inclusion
     *      in the POST request.
     *    </li>
     *    <li>
     *      If the argument is an array, this array's content is put into
     *      an {@see org.json.simple.JSONArray}, where each array member is serialized recursively.
     *    </li>
     *    <li>
     *      If the argument is a map, it is transformed into a {@see org.json.simple.JSONObject} with the keys taken
     *      directly from the map and the values recursively serialized to their JSON representation.
     *      So it is only save fto use or a simple map with string keys.
     *    </li>
     *    <li>
     *      If the argument is a {@link Collection}, it is transformed into a {@see JSONArray} with
     *      the values recursively serialized to their JSON representation.
     *    </li>
     *    <li>
     *      Otherwise the object is used directly.
     *    </li>
     * </ul>
     *
     * Future version of this lib will probably provide a more sophisticated serialization mechanism.
     * <em>This is how it is supposed to be for the next release, currently a simplified serialization is in place</em>
     *
     * @param pArg the object to serialize
     * @return a JSON serialized object
     */
    protected Object serializeArgumentToJson(Object pArg) {
        if (pArg == null) {
            return null;
        } else if (pArg instanceof JSONAware) {
            return pArg;
        } else if (pArg.getClass().isArray()) {
            return serializeArray(pArg);
        } else if (pArg instanceof Map) {
            return serializeMap((Map) pArg);
        } else if (pArg instanceof Collection) {
            return serializeCollection((Collection) pArg);
        } else {
            return pArg instanceof Number || pArg instanceof Boolean ? pArg : pArg.toString();
        }
    }

    // pattern used for escaping business
    private static final Pattern SLASH_ESCAPE_PATTERN = Pattern.compile("((?:[^!/]|!.)*)(?:/|$)");
    private static final Pattern UNESCAPE_PATTERN = Pattern.compile("!(.)");

    /**
     * Split up a path taking into account proper escaping (as descibed in the
     * <a href="http://www.jolokia.org/reference">reference manual</a>).
     *
     * @param pArg string to split with escaping taken into account
     * @return splitted element or null if the argument was null.
     */
    protected List<String> splitPath(String pArg) {
        List<String> ret = new ArrayList<String>();
        if (pArg != null) {
            Matcher m = SLASH_ESCAPE_PATTERN.matcher(pArg);
            while (m.find() && m.start(1) != pArg.length()) {
                ret.add(UNESCAPE_PATTERN.matcher(m.group(1)).replaceAll("$1"));
            }
        }
        return ret;
    }

    // =====================================================================================================

    private Object serializeCollection(Collection pArg) {
        JSONArray array = new JSONArray();
        for (Object value : ((Collection) pArg)) {
            array.add(serializeArgumentToJson(value));
        }
        return array;
    }

    private Object serializeMap(Map pArg) {
        JSONObject map  = new JSONObject();
        for (Map.Entry<Object,Object> entry : ((Map<Object,Object>) pArg).entrySet()) {
            map.put(entry.getKey(),serializeArgumentToJson(entry.getValue()));
        }
        return map;
    }

    private Object serializeArray(Object pArg) {
        JSONArray innerArray = new JSONArray();
        for (int i = 0; i < Array.getLength(pArg); i++ ) {
            innerArray.add(serializeArgumentToJson(Array.get(pArg, i)));
        }
        return innerArray;
    }

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

    // null escape used for GET requests
    private String nullEscape(Object pArg) {
        if (pArg == null) {
            return "[null]";
        } else if (pArg instanceof String && ((String) pArg).length() == 0) {
            return "\"\"";
        } else if (pArg instanceof JSONAware) {
            return ((JSONAware) pArg).toJSONString();
        } else {
            return pArg.toString();
        }
    }


}
