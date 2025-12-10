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
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.client.EscapeUtil;
import org.jolokia.client.JolokiaOperation;
import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.client.response.JolokiaWriteResponse;
import org.jolokia.json.JSONObject;

/**
 * <p>A write request to set the value of a single MBean attribute, optionally with an inner path.
 * Previous value is returned in a response. The {@link ObjectName} for write request can not be a pattern (or null
 * which effectively means {@code *:*}).</p>
 *
 * <p>JSON form of a "write" {@link HttpMethod#POST} is:<pre>{@code
 * {
 *     "type": "write",
 *     "mbean": "object-name",
 *     "attribute": "attribute",
 *     "path": "path/to/inner/object",
 *     "target": {
 *         "url": "target remote JMX URI",
 *         "user": "remote JMX connector username",
 *         "password": "remote JMX connector password"
 *     }
 * }
 * }</pre>
 * </p>
 *
 * <p>For {@link HttpMethod#GET}, the URI is: {@code /mbean/attribute/value/path/to/inner/object}</p>
 *
 * @author roland
 * @since Jun 5, 2010
 */
public class JolokiaWriteRequest extends JolokiaMBeanRequest {

    /** Attribute name to set */
    private final String attribute;

    /** The value to be set. Subject to serialization using Jolokia converters. */
    private final Object value;

    /**
     * Path to retrieve an inner value of the response (which is a previous value). This path is already
     * escaped and uses {@code /} separator for nested path.
     */
    private String path;

    // constructors without a path, which indicates an inner value of the attribute being written

    /**
     * Constructor for a write request
     *
     * @param pMBeanName MBean name which attribute should be set
     * @param pAttribute name of the attribute to set
     * @param pValue     new value
     */
    public JolokiaWriteRequest(ObjectName pMBeanName, String pAttribute, Object pValue) {
        this(pMBeanName, pAttribute, pValue, null);
    }

    /**
     * Constructor for a write request
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pMBeanName    MBean name which attribute should be set
     * @param pAttribute    name of the attribute to set
     * @param pValue        new value
     */
    public JolokiaWriteRequest(JolokiaTargetConfig pTargetConfig, ObjectName pMBeanName, String pAttribute, Object pValue) {
        this(pTargetConfig, pMBeanName, pAttribute, pValue, null);
    }

    /**
     * Constructor for a write request
     *
     * @param pMBeanName MBean name which attribute should be set
     * @param pAttribute name of the attribute to set
     * @param pValue     new value
     * @throws MalformedObjectNameException if the mbean name is invalid
     */
    public JolokiaWriteRequest(String pMBeanName, String pAttribute, Object pValue)
            throws MalformedObjectNameException {
        this(pMBeanName, pAttribute, pValue, null);
    }

    /**
     * Constructor for a write request
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pMBeanName    MBean name which attribute should be set
     * @param pAttribute    name of the attribute to set
     * @param pValue        new value
     * @throws MalformedObjectNameException if the mbean name is invalid
     */
    public JolokiaWriteRequest(JolokiaTargetConfig pTargetConfig, String pMBeanName, String pAttribute, Object pValue)
            throws MalformedObjectNameException {
        this(pTargetConfig, pMBeanName, pAttribute, pValue, null);
    }

    // constructors with a path, which indicates an inner value of the attribute being written

    /**
     * Constructor for a write request
     *
     * @param pMBeanName MBean name which attribute should be set
     * @param pAttribute name of the attribute to set
     * @param pValue     new value
     * @param pPath         optional path for setting an inner value
     */
    public JolokiaWriteRequest(ObjectName pMBeanName, String pAttribute, Object pValue, String pPath) {
        this(null, pMBeanName, pAttribute, pValue, pPath);
    }

    /**
     * Constructor for a write request
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pMBeanName    MBean name which attribute should be set
     * @param pAttribute    name of the attribute to set
     * @param pValue        new value
     * @param pPath         optional path for setting an inner value
     */
    public JolokiaWriteRequest(JolokiaTargetConfig pTargetConfig, ObjectName pMBeanName, String pAttribute, Object pValue, String pPath) {
        super(JolokiaOperation.WRITE, pMBeanName, pTargetConfig);
        attribute = pAttribute;
        value = pValue;
        path = pPath;
    }

    /**
     * Constructor for a write request
     *
     * @param pMBeanName MBean name which attribute should be set
     * @param pAttribute name of the attribute to set
     * @param pValue     new value
     * @param pPath      optional path for setting an inner value
     * @throws MalformedObjectNameException if the mbean name is invalid
     */
    public JolokiaWriteRequest(String pMBeanName, String pAttribute, Object pValue, String pPath)
            throws MalformedObjectNameException {
        this(null, pMBeanName, pAttribute, pValue, pPath);
    }

    /**
     * Constructor for a write request
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pMBeanName    MBean name which attribute should be set
     * @param pAttribute    name of the attribute to set
     * @param pValue        new value
     * @param pPath         optional path for setting an inner value
     * @throws MalformedObjectNameException if the mbean name is invalid
     */
    public JolokiaWriteRequest(JolokiaTargetConfig pTargetConfig, String pMBeanName, String pAttribute, Object pValue, String pPath)
            throws MalformedObjectNameException {
        this(pTargetConfig, new ObjectName(pMBeanName), pAttribute, pValue, pPath);
    }

    @Override
    @SuppressWarnings("unchecked")
    public JolokiaWriteResponse createResponse(JSONObject pResponse) {
        return new JolokiaWriteResponse(this, pResponse);
    }

    @Override
    public List<String> getRequestParts() {
        List<String> parts = super.getRequestParts();
        parts.add(attribute);
        parts.add(serializeArgumentToRequestPart(value));
        parts.addAll(EscapeUtil.splitPath(path));
        return parts;
    }

    @Override
    public JSONObject toJson() {
        JSONObject ret = super.toJson();
        ret.put("attribute", attribute);
        ret.put("value", serializeArgumentToJson(value));
        if (path != null) {
            ret.put("path", path);
        }
        return ret;
    }

    /**
     * The attribute encapsulated within this request
     *
     * @return the attribute's name
     */
    public String getAttribute() {
        return attribute;
    }

    /**
     * The new value to set
     *
     * @return value
     */
    public Object getValue() {
        return value;
    }

    /**
     * Get path to previous value from write response.
     *
     * @return path or null if no path is set
     */
    public String getPath() {
        return path;
    }

    /**
     * Set the path to retrieve from the previous value of the write response
     *
     * @param pPath inner path
     */
    public void setPath(String pPath) {
        path = pPath;
    }

}
