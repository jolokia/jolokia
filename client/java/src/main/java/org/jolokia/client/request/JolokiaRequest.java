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
package org.jolokia.client.request;

import java.lang.reflect.Array;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.jolokia.client.JolokiaOperation;
import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.client.response.JolokiaResponse;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.json.JSONStructure;

/**
 * <p>Abstract representation of <em>Jolokia request</em> targeted at Jolokia agent. Each Jolokia request has single
 * type (indicating an operation to perform, like reading MBean attribute or invoking JMX operation).</p>
 *
 * <p>Each supported request should have two representations specific to {@link HttpMethod} chosen:<ul>
 *     <li>{@link HttpMethod#GET} - request arguments are encoded into request URI, so it is subject to
 *     URI compliance, as specified in RFC 3986. Some values simply can't be encoded into a URI</li>
 *     <li>{@link HttpMethod#POST} - request arguments are encoded into JSON fields of the request body serialized
 *     from {@link JSONObject}.</li>
 * </ul></p>
 *
 * <p>JSON form of generic {@link HttpMethod#POST} is:<pre>{@code
 * {
 *     "type": "operation-id",
 *     "path": "path/to/response",
 *     "target": {
 *         "url": "target remote JMX URI",
 *         "user": "remote JMX connector username",
 *         "password": "remote JMX connector password"
 *     }
 * }
 * }</pre>
 * {@code target} field is optional. {@code path} field is for requests that use a path and is the same as
 * if it was used with {@link HttpMethod#GET}.
 * </p>
 *
 * <p>For {@link HttpMethod#GET}, "type" is encoded as first path segment after Jolokia Agent base URI and "path"
 * is appended to the end. In between we may have request-specific arguments.</p>
 *
 * <p>A request may be a proxied request - to be passed by target Jolokia agent to yet another JVM using Remote
 * JMX connection (JSR-160). JMX Remote specification defines {@link javax.management.remote.JMXServiceURL} in
 * several forms:<ul>
 *     <li>{@code service:jmx:rmi://host:port/xxx} - communication based on RMI - this is required protocol from
 *     JSR-160.</li>
 *     <li>{@code service:jmx:jmxmp://host:port} - communication based on non-RMI protocol based
 *     on object serialization, TLS and SASL - this is optional protocol from JSR-160</li>
 *     <li>{@code service:jmx:iiop://host:port} - based on CORBA Protocol - no longer supported in JDK.</li>
 * </ul>
 * Nothing prevents to use URI like {@code service:jmx:jolokia://host:port/path}!</p>
 *
 * @author roland
 * @since Apr 24, 2010
 */
public abstract class JolokiaRequest {

    /** Specific {@link JolokiaOperation request type} of this request */
    private final JolokiaOperation type;

    /**
     * Request should be associated with some HTTP method. Sometimes the method may be overridden, if a method is not
     * suitable (for example we can't use {@link HttpMethod#GET} with proxy Jolokia requests.
     */
    private HttpMethod preferredHttpMethod;

    /**
     * <p>For proxied requests, we may specify {@link javax.management.remote.JMXServiceURL} of the target JVM.
     * The request will be sent to one Jolokia agent, but it'll check the {@code target} and possibly send the
     * request to another JVM (which may run ordinary, RMI-based {@link javax.management.remote.JMXConnectorServer}.</p>
     *
     * <p>Proxied requests will always used {@link HttpMethod#POST}.</p>
     */
    private final JolokiaTargetConfig targetConfig;

    /**
     * Constructor for subclasses
     *
     * @param pType         type of this request
     * @param pTargetConfig a target configuration if used in proxy mode or <code>null</code>
     *                      if this is a direct request
     */
    protected JolokiaRequest(JolokiaOperation pType, JolokiaTargetConfig pTargetConfig) {
        type = pType;
        targetConfig = pTargetConfig;
    }

    /**
     * Get the type of the request
     *
     * @return request's type
     */
    public JolokiaOperation getType() {
        return type;
    }

    /**
     * Get a target configuration for use with an agent in JSR-160 proxy mode
     *
     * @return the target config or <code>null</code> if this is a direct request
     */
    public JolokiaTargetConfig getTargetConfig() {
        return targetConfig;
    }

    /**
     * The preferred HTTP method to use (either 'GET' or 'POST')
     * @return the HTTP method to use for this request, or <code>null</code> if the method should be automatically selected.
     */
    public HttpMethod getPreferredHttpMethod() {
        return preferredHttpMethod;
    }

    /**
     * Set the preferred HTTP method, either 'GET' or 'POST'.
     *
     * @param pPreferredHttpMethod HTTP method to use.
     */
    public void setPreferredHttpMethod(HttpMethod pPreferredHttpMethod) {
        preferredHttpMethod = pPreferredHttpMethod;
    }

    /**
     * Create a {@link JolokiaResponse} based on {@link JSONObject} from the remote Jolokia Agent. Each
     * {@link JolokiaRequest} knows what kind of {@link JolokiaResponse} to create.
     *
     * @param pResponse http response as obtained from the remote Jolokia Agent over HTTP.
     * @return the create response
     */
    public abstract <RES extends JolokiaResponse<REQ>, REQ extends JolokiaRequest> RES createResponse(JSONObject pResponse);

    // ==================================================================================================
    // Methods used for building up HTTP Requests and setting up the response
    // These methods are package visible only since are used only internally

    /**
     * <p>When a {@link JolokiaRequest} is used with {@link HttpMethod#GET}, arguments should be encoded in
     * the request URI. And opposite - if a request returns {@code null} here, {@link HttpMethod#POST} will
     * be used. For {@link HttpMethod#GET} request, the operation type is not returned among the parts.</p>
     *
     * <p>Not every argument is easily convertible and may be illegal and rejected by some
     * security-aware HTTP servers. But in case everything works fine (at user's responsibility), this is the
     * method that should be implemented by subclasses.</p>
     *
     * @return
     */
    public abstract List<String> getRequestParts();

    /**
     * Get a JSON representation of this request as {@link JSONObject}. Subclasses should call this method
     * and add operation-specific fields.
     *
     * @return
     */
    public JSONObject toJson() {
        JSONObject ret = new JSONObject();
        ret.put("type", type.getValue());
        if (targetConfig != null) {
            // information about ultimate target of the request when this client connects to Jolokia
            // Agent running in Proxy mode.
            ret.put("target", targetConfig.toJson());
        }
        return ret;
    }

    // TODO: the below methods should use low-level conversion methods now available in jolokia-service-serializer

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
     * TODO: Move to serializer
     *
     * @param pArg the argument to serialize for an GET request
     * @return the string representation
     */
    protected String serializeArgumentToRequestPart(Object pArg) {
        if (pArg != null) {
            if (pArg.getClass().isArray()) {
                return getArrayForArgument((Object[]) pArg);
            } else if (List.class.isAssignableFrom(pArg.getClass())) {
                List<?> list = (List<?>) pArg;
                Object[] args = new Object[list.size()];
                int i = 0;
                for (Object e : list) {
                    args[i++] = e;
                }
                return getArrayForArgument(args);
            } else if (Date.class == pArg.getClass()) {
                // pass Date as long (there's no TZ information here just like in java.time.Instant)
                Date d = (Date) pArg;
                return Long.toString(d.getTime());
            } else if (Temporal.class.isAssignableFrom(pArg.getClass())) {
                // special handling for the temporals that can easily be converted to unix time (in nanos)
                Temporal t = (Temporal) pArg;
                if (t.isSupported(ChronoField.INSTANT_SECONDS)) {
                    long instant = t.getLong(ChronoField.INSTANT_SECONDS) * 1_000_000_000L
                        + t.getLong(ChronoField.NANO_OF_SECOND);
                    return Long.toString(instant);
                } else {
                    // for now we can't nicely convert it and we don't know what's the pattern used
                    // at server side
                    return t.toString();
                }
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
     *      If the argument is of type {@link org.jolokia.json.JSONStructure}, then it is used directly for inclusion
     *      in the POST request.
     *    </li>
     *    <li>
     *      If the argument is an array, this array's content is put into
     *      an {@link org.jolokia.json.JSONArray}, where each array member is serialized recursively.
     *    </li>
     *    <li>
     *      If the argument is a map, it is transformed into a {@link org.jolokia.json.JSONObject} with the keys taken
     *      directly from the map and the values recursively serialized to their JSON representation.
     *      So it is only save fto use or a simple map with string keys.
     *    </li>
     *    <li>
     *      If the argument is a {@link Collection}, it is transformed into a {@link JSONArray} with
     *      the values recursively serialized to their JSON representation.
     *    </li>
     *    <li>
     *      Otherwise the object is used directly.
     *    </li>
     * </ul>
     * <p>
     * Future version of this lib will probably provide a more sophisticated serialization mechanism.
     * <em>This is how it is supposed to be for the next release, currently a simplified serialization is in place</em>
     * TODO: Move to serializer
     *
     * @param pArg the object to serialize
     * @return a JSON serialized object
     */
    public Object serializeArgumentToJson(Object pArg) {
        if (pArg == null) {
            return null;
        } else if (pArg instanceof JSONStructure) {
            return pArg;
        } else if (pArg.getClass().isArray()) {
            return serializeArray(pArg);
        } else if (pArg instanceof Map) {
            //noinspection unchecked
            return serializeMap((Map<String, Object>) pArg);
        } else if (pArg instanceof Collection) {
            return serializeCollection((Collection<?>) pArg);
        } else if (Date.class == pArg.getClass()) {
            // pass Date as long (there's no TZ information here just like in java.time.Instant)
            Date d = (Date) pArg;
            return Long.toString(d.getTime());
        } else if (pArg instanceof Temporal t) {
            // special handling for the temporals that can easily be converted to unix time (in nanos)
            if (t.isSupported(ChronoField.INSTANT_SECONDS)) {
                return t.getLong(ChronoField.INSTANT_SECONDS) * 1_000_000_000L
                    + t.getLong(ChronoField.NANO_OF_SECOND);
            } else {
                // for now we can't nicely convert it and we don't know what's the pattern used
                // at server side
                return t.toString();
            }
        } else {
            return pArg instanceof Number || pArg instanceof Boolean ? pArg : pArg.toString();
        }
    }

    // =====================================================================================================

    private Object serializeCollection(Collection<?> pArg) {
        JSONArray array = new JSONArray(pArg.size());
        for (Object value : pArg) {
            array.add(serializeArgumentToJson(value));
        }
        return array;
    }

    private Object serializeMap(Map<String, Object> pArg) {
        JSONObject map = new JSONObject();
        for (Map.Entry<String, Object> entry : pArg.entrySet()) {
            map.put(entry.getKey(), serializeArgumentToJson(entry.getValue()));
        }
        return map;
    }

    private Object serializeArray(Object pArg) {
        int length = Array.getLength(pArg);
        JSONArray innerArray = new JSONArray(length);
        for (int i = 0; i < length; i++) {
            innerArray.add(serializeArgumentToJson(Array.get(pArg, i)));
        }
        return innerArray;
    }

    private String getArrayForArgument(Object[] pArg) {
        StringBuilder inner = new StringBuilder();
        for (int i = 0; i < pArg.length; i++) {
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
        } else if (pArg instanceof String && ((String) pArg).isEmpty()) {
            return "\"\"";
        } else if (pArg instanceof JSONStructure) {
            return ((JSONStructure) pArg).toJSONString();
        } else {
            return pArg.toString();
        }
    }

}
