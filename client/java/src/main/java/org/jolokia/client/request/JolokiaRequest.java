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

import java.util.List;
import javax.management.AttributeNotFoundException;
import javax.management.openmbean.OpenType;

import org.jolokia.client.JolokiaOperation;
import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.client.response.JolokiaResponse;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.converter.object.Converter;
import org.jolokia.converter.object.ObjectToObjectConverter;
import org.jolokia.converter.object.ObjectToOpenTypeConverter;
import org.jolokia.core.service.serializer.SerializeOptions;
import org.jolokia.json.JSONObject;

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

    /**
     * Most generic converted for any values (usually Strings) to objects of target class
     * specified as {@link String}. It is used by other, specialized converters.
     */
    private static final Converter<String> objectToObjectConverter;

    /**
     * Deserializer from String, {@link org.jolokia.json.JSONStructure} or other supported objects
     * to objects of class specified as {@link OpenType} for specialized JMX object conversion.
     */
    private static final Converter<OpenType<?>> objectToOpenTypeConverter;

    // From object to json:
    private static final ObjectToJsonConverter toJsonConverter;

    static {
        // generic converter of any values (primitive, basic like dates and arrays)
        objectToObjectConverter = new ObjectToObjectConverter();

        objectToOpenTypeConverter = new ObjectToOpenTypeConverter(objectToObjectConverter, false);

        // default version where CoreConfiguration is not available
        toJsonConverter = new ObjectToJsonConverter((ObjectToObjectConverter) objectToObjectConverter,
            (ObjectToOpenTypeConverter) objectToOpenTypeConverter, null);
    }

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

    /**
     * <p>Serialize an object to a string which can be uses as URL part in a GET request
     * when object should be transmitted <em>to</em> the agent. The serialization uses the same
     * mechanisms which are used at the server and {@link Object#toString()} is used only in limited
     * scenarios - and it's always explicit (some types like {@link java.net.URL} have <em>good</em>
     * {@code toString()} method).</p>
     * <p>Any <code>null</code> value is transformed in the special marker <code>[null]</code> which on the
     * agent side is converted back into a <code>null</code>.</p>
     * <p>Sophisticated serialization is available for POST request, where the data is sent as JSON body
     * of the HTTP request.</p>
     *
     * @param pArg the argument to serialize for a {@code GET} request
     * @return the string representation
     * @throws IllegalArgumentException when given object can't be easily converted to a String. This is clearly
     *         an indication that {@link HttpMethod#POST} method should be used.
     */
    protected String serializeArgumentToRequestPart(Object pArg) throws IllegalArgumentException {
        String v = nullEscape(pArg);
        if (v != null) {
            return v;
        }
        return (String) objectToObjectConverter.convert(String.class.getName(), pArg);
    }

    /**
     * <p>Serialize an object to an string or JSON structure for write/exec {@code POST} requests.</p>
     * <p>Since Jolokia 2.5.0, full serialization mechanism is used (the same as at the server side).</p>
     *
     * @param pArg the object to serialize
     * @return a JSON serialized object
     */
    public Object serializeArgumentToJson(Object pArg) {
        if (pArg == null) {
            return null;
        }
        try {
            return toJsonConverter.serialize(pArg, null, SerializeOptions.DEFAULT);
        } catch (AttributeNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Escaping some special values for GET requests
     * @param pArg
     * @return
     */
    private String nullEscape(Object pArg) {
        if (pArg == null) {
            return "[null]";
        } else if (pArg instanceof String && ((String) pArg).isEmpty()) {
            return "\"\"";
        }

        return null;
    }

}
