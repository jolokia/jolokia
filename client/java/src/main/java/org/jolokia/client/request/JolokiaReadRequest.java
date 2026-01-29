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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.client.EscapeUtil;
import org.jolokia.client.JolokiaOperation;
import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.client.response.JolokiaReadResponse;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;

/**
 * <p>A read request to get one or more attributes from one or more MBeans in one HTTP call.</p>
 *
 * <p>JSON form of a "read" {@link HttpMethod#POST} is:<pre>{@code
 * {
 *     "type": "read",
 *     "mbean": "object-name-or-pattern",
 *     "attribute": "attribute" | [ "attribute1", "attribute2", ... ],
 *     "path": "path/to/response",
 *     "target": {
 *         "url": "target remote JMX URI",
 *         "user": "remote JMX connector username",
 *         "password": "remote JMX connector password"
 *     }
 * }
 * }</pre>
 * {@code attribute} field may be a single attribute name or a {@link JSONArray} of attribute names. "path" may
 * be used to extract (drill into) parts of the attribute value when it's a complex value.
 * </p>
 *
 * <p>For {@link HttpMethod#GET}, "attribute" may only be a single attribute.</p>
 *
 * @author roland
 * @since Apr 24, 2010
 */
public class JolokiaReadRequest extends JolokiaMBeanRequest {

    /** Attribute names to retrieve. For {@link HttpMethod#GET} only single attribute can be retrieved */
    private final List<String> attributes;

    /** Whether to use {@link javax.management.MBeanServer#getAttributes} or {@link javax.management.MBeanServer#getAttribute} */
    private final boolean multiAttributes;

    /** Path to retrieve an inner value of the response */
    private String path;

    /**
     * Create a READ request to request one attribute from the remote Jolokia Agent. This translates to
     * {@link javax.management.MBeanServer#getAttribute} call for single attribute and if it doesn't exist, an
     * {@link javax.management.AttributeNotFoundException} is thrown.
     *
     * @param pObjectName Name of the MBean to request, which can be a pattern in
     *                    which case the given attributes are looked at all MBeans matched
     *                    by this pattern. If an attribute does not fit to a matched MBean it is
     *                    ignored.
     * @param pAttribute  one specific attribute to request.
     */
    public JolokiaReadRequest(ObjectName pObjectName, String pAttribute) {
        this(null, pObjectName, pAttribute);
    }

    /**
     * Create a READ request to request one or more attributes from the remote Jolokia Agent with the intention
     * to use {@link javax.management.MBeanServer#getAttributes} call which ignores missing attributes.
     *
     * @param pObjectName Name of the MBean to request, which can be a pattern in
     *                    which case the given attributes are looked at all MBeans matched
     *                    by this pattern. If an attribute does not fit to a matched MBean it is
     *                    ignored.
     * @param pAttributes one or more attributes to request
     */
    public JolokiaReadRequest(ObjectName pObjectName, String... pAttributes) {
        this(null, pObjectName, pAttributes);
    }

    /**
     * Create a READ request to request one attribute from the remote Jolokia Agent. This translates to
     * {@link javax.management.MBeanServer#getAttribute} call for single attribute and if it doesn't exist, an
     * {@link javax.management.AttributeNotFoundException} is thrown.
     *
     * @param pTargetConfig proxy target configuration or {@code null} if no proxy should be used
     * @param pObjectName   Name of the MBean to request, which can be a pattern in
     *                      which case the given attributes are looked at all MBeans matched
     *                      by this pattern. If an attribute does not fit to a matched MBean it is
     *                      ignored.
     * @param pAttribute    one specific attribute to request.
     */
    public JolokiaReadRequest(JolokiaTargetConfig pTargetConfig, ObjectName pObjectName, String pAttribute) {
        super(JolokiaOperation.READ, pObjectName, pTargetConfig);
        attributes = Collections.singletonList(pAttribute);
        multiAttributes = false;
    }

    /**
     * Create a READ request to request one or more attributes from the remote Jolokia Agent with the intention
     * to use {@link javax.management.MBeanServer#getAttributes} call which ignores missing attributes.
     *
     * @param pTargetConfig proxy target configuration or {@code null} if no proxy should be used
     * @param pObjectName   Name of the MBean to request, which can be a pattern in
     *                      which case the given attributes are looked at all MBeans matched
     *                      by this pattern. If an attribute does not fit to a matched MBean it is
     *                      ignored.
     * @param pAttributes   one or more attributes to request.
     */
    public JolokiaReadRequest(JolokiaTargetConfig pTargetConfig, ObjectName pObjectName, String... pAttributes) {
        super(JolokiaOperation.READ, pObjectName, pTargetConfig);
        attributes = Arrays.asList(pAttributes);
        multiAttributes = true;
    }

    /**
     * Create a READ request to request one attribute from the remote Jolokia Agent, MBean name is specified
     * as String.
     *
     * @param pObjectName object name as sting which gets converted to a {@link javax.management.ObjectName}
     * @param pAttribute  one specific attribute to request.
     * @throws javax.management.MalformedObjectNameException when argument is not a valid object name
     */
    public JolokiaReadRequest(String pObjectName, String pAttribute) throws MalformedObjectNameException {
        this(null, pObjectName, pAttribute);
    }

    /**
     * Create a READ request to request one or more attributes from the remote Jolokia Agent, MBean name is specified
     * as String.
     *
     * @param pObjectName object name as sting which gets converted to a {@link javax.management.ObjectName}
     * @param pAttributes zero, one or more attributes to request.
     * @throws javax.management.MalformedObjectNameException when argument is not a valid object name
     */
    public JolokiaReadRequest(String pObjectName, String... pAttributes) throws MalformedObjectNameException {
        this(null, pObjectName, pAttributes);
    }

    /**
     * Create a READ request to request one specific attribute from the remote Jolokia Agent, MBean name is specified
     * as String.
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pObjectName   object name as sting which gets converted to a {@link javax.management.ObjectName}}
     * @param pAttribute    one specific attribute to request.
     * @throws javax.management.MalformedObjectNameException when argument is not a valid object name
     */
    public JolokiaReadRequest(JolokiaTargetConfig pTargetConfig, String pObjectName, String pAttribute) throws MalformedObjectNameException {
        this(pTargetConfig, new ObjectName(pObjectName), pAttribute);
    }

    /**
     * Create a READ request to request one or more attributes from the remote Jolokia Agent, MBean name is specified
     * as String.
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pObjectName   object name as sting which gets converted to a {@link javax.management.ObjectName}}
     * @param pAttributes   zero, one or more attributes to request.
     * @throws javax.management.MalformedObjectNameException when argument is not a valid object name
     */
    public JolokiaReadRequest(JolokiaTargetConfig pTargetConfig, String pObjectName, String... pAttributes) throws MalformedObjectNameException {
        this(pTargetConfig, new ObjectName(pObjectName), pAttributes);
    }

    /**
     * Get all attributes requested. This list can be empty if all attributes should be fetched. Also it is permitted
     * to include <em>wildcard</em> attribute here (explicit {@code null} or {@code *} value). No partial
     * wildcards are supported (like {@code Na*}).
     *
     * @return attributes
     */
    public Collection<String> getAttributes() {
        return attributes;
    }

    /**
     * If this request is for a single attribute, this attribute is returned
     * by this getter.
     *
     * @return single attribute
     * @throws IllegalStateException if no or more than one attribute are used when this request was
     *                               constructed.
     */
    public String getAttribute() {
        if (!hasSingleAttribute()) {
            throw new IllegalStateException("More than one attribute given for this request");
        }
        return attributes.get(0);
    }

    @Override
    public List<String> getRequestParts() {
        if (hasSingleAttribute()) {
            // we support the path to drill into this single attribute value
            List<String> ret = super.getRequestParts();
            ret.add(getAttribute());
            ret.addAll(EscapeUtil.splitPath(path));
            return ret;
        } else if (hasAllAttributes() && path == null) {
            // for "read all attributes" we can't use a path with GET, because we can't then distinguish
            // between "single attribute with path" and "all attributes with a path"
            return super.getRequestParts();
        }

        // A GET request can't be used for multiple attribute fetching or for fetching all attributes with a path
        // returning null will indicate POST request
        return null;
    }

    @Override
    public JSONObject toJson() {
        JSONObject ret = super.toJson();
        if (hasSingleAttribute() && !multiAttributes) {
            // single attribute as string
            ret.put("attribute", attributes.get(0));
        } else {
            // single attribute field sent as array of strings (can be empty == all attributes)
            JSONArray attrs = new JSONArray(attributes.size());
            attrs.addAll(attributes);
            ret.put("attribute", attrs);
        }
        // in POST we can use path whether or not we're using single, more or all attributes
        // (non-existing "attribute" means "all attributes")
        if (path != null) {
            ret.put("path", path);
        }
        return ret;
    }

    @Override
    @SuppressWarnings("unchecked")
    public JolokiaReadResponse createResponse(JSONObject pResponse) {
        return new JolokiaReadResponse(this, pResponse);
    }

    /**
     * Whether this request represents a request for a single attribute
     *
     * @return true if the client request is for a single attribute
     */
    public boolean hasSingleAttribute() {
        return !multiAttributes || attributes.size() == 1;
    }

    /**
     * Whether there's an array of attributes used (even one element)
     * @return
     */
    public boolean isMultiAttributes() {
        return multiAttributes;
    }

    /**
     * Whether all attributes should be fetched
     *
     * @return true if all attributes should be fetched
     */
    public boolean hasAllAttributes() {
        return attributes.isEmpty() || attributes.contains(null) || attributes.contains("*");
    }

    /**
     * Get the path for extracting parts of the return value.
     *
     * @return path used for extracting
     */
    public String getPath() {
        return path;
    }

    /**
     * Set the path for diving into the return value. Can't be specified with constructor, because attributes
     * to retrieve are passed as varargs.
     *
     * @param pPath path to set
     */
    public void setPath(String pPath) {
        path = pPath;
    }

}
