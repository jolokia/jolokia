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

import java.util.List;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.json.simple.JSONObject;

/**
 * Request for setting the value of an attribute, optionally
 * with an inner path.
 *
 * @author roland
 * @since Jun 5, 2010
 */
public class J4pWriteRequest extends AbtractJ4pMBeanRequest {

    // Name of attribute to set
    private String attribute;

    // Value of the attribute to set
    private Object value;

    // Inner path (optional)
    private String path;

    /**
     * Constructor for a write request
     *
     * @param pMBeanName MBean name which attribute should be set
     * @param pAttribute name of the attribute to set
     * @param pValue new value
     */
    public J4pWriteRequest(ObjectName pMBeanName,String pAttribute,Object pValue,String ... pPath) {
        this(null,pMBeanName,pAttribute,pValue,pPath);
    }

    /**
     * Constructor for a write request
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pMBeanName MBean name which attribute should be set
     * @param pAttribute name of the attribute to set
     * @param pValue new value
     */
    public J4pWriteRequest(J4pTargetConfig pTargetConfig,ObjectName pMBeanName,String pAttribute,Object pValue,String ... pPath) {
        super(J4pType.WRITE, pMBeanName,pTargetConfig);
        attribute = pAttribute;
        value = pValue;
        if (pPath != null && pPath.length > 0) {
            path = pPath[0];
        }
    }

    /**
     * Constructor for a write request
     *
     * @param pMBeanName MBean name which attribute should be set
     * @param pAttribute name of the attribute to set
     * @param pValue new value
     * @param pPath optional path for setting an inner value
     * @throws MalformedObjectNameException if the mbean name is invalid
     */
    public J4pWriteRequest(String pMBeanName,String pAttribute, Object pValue,String ... pPath)
            throws MalformedObjectNameException {
        this(null,pMBeanName,pAttribute,pValue,pPath);
    }

    /**
     * Constructor for a write request
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pMBeanName MBean name which attribute should be set
     * @param pAttribute name of the attribute to set
     * @param pValue new value
     * @param pPath optional path for setting an inner value
     * @throws MalformedObjectNameException if the mbean name is invalid
     */
    public J4pWriteRequest(J4pTargetConfig pTargetConfig,String pMBeanName,String pAttribute, Object pValue,String ... pPath)
            throws MalformedObjectNameException {
        this(pTargetConfig,new ObjectName(pMBeanName),pAttribute,pValue,pPath);
    }

    @Override
    J4pWriteResponse createResponse(JSONObject pResponse) {
        return new J4pWriteResponse(this,pResponse);
    }

    @Override
    List<String> getRequestParts() {
        List<String> parts = super.getRequestParts();
        parts.add(attribute);
        parts.add(serializeArgumentToRequestPart(value));
        addPath(parts,path);
        return parts;
    }

    @Override
    JSONObject toJson() {
        JSONObject ret = super.toJson();
        ret.put("attribute",attribute);
        ret.put("value",serializeArgumentToJson(value));
        if (path != null) {
            ret.put("path",path);
        }
        return ret;
    }

    // ==============================================================================================
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
     * @return value
     */
    public Object getValue() {
        return value;
    }

    /**
     * Get path to value
     *
     * @return path or null if no path is set
     */
    public String getPath() {
        return path;
    }

    /**
     * Set the path to retrieve
     *
     * @param pPath inner path
     */
    public void setPath(String pPath) {
        path = pPath;
    }
}
