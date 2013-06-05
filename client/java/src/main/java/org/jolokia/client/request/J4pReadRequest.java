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

import java.util.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * A read request to get one or more attributes from
 * one or more MBeans within a single request.
 *
 * @author roland
 * @since Apr 24, 2010
 */
public class J4pReadRequest extends AbtractJ4pMBeanRequest {

    // Name of attribute to request
    private List<String> attributes;

    // Path for extracting return value
    private String path;

    /**
     * Create a READ request to request one or more attributes
     * from the remote j4p agent
     *
     * @param pObjectName Name of the MBean to request, which can be a pattern in
     *                    which case the given attributes are looked at all MBeans matched
     *                    by this pattern. If an attribute does not fit to a matched MBean it is
     *                    ignored.
     * @param pAttribute one or more attributes to request.
     */
    public J4pReadRequest(ObjectName pObjectName,String ... pAttribute) {
        this(null,pObjectName,pAttribute);
    }

    /**
     * Create a READ request to request one or more attributes
     * from the remote j4p agent
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pObjectName Name of the MBean to request, which can be a pattern in
     *                    which case the given attributes are looked at all MBeans matched
     *                    by this pattern. If an attribute does not fit to a matched MBean it is
     *                    ignored.
     * @param pAttribute one or more attributes to request.
     */
    public J4pReadRequest(J4pTargetConfig pTargetConfig,ObjectName pObjectName,String ... pAttribute) {
        super(J4pType.READ, pObjectName,pTargetConfig);
        attributes = Arrays.asList(pAttribute);
    }

    /**
     * Create a READ request to request one or more attributes
     * from the remote j4p agent
     *
     * @param pObjectName object name as sting which gets converted to a {@link javax.management.ObjectName}}
     * @param pAttribute zero, one or more attributes to request.
     * @throws javax.management.MalformedObjectNameException when argument is not a valid object name
     */
    public J4pReadRequest(String pObjectName,String ... pAttribute) throws MalformedObjectNameException {
        this(null,pObjectName,pAttribute);
    }

    /**
     * Create a READ request to request one or more attributes
     * from the remote j4p agent
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pObjectName object name as sting which gets converted to a {@link javax.management.ObjectName}}
     * @param pAttribute zero, one or more attributes to request.
     * @throws javax.management.MalformedObjectNameException when argument is not a valid object name
     */
    public J4pReadRequest(J4pTargetConfig pTargetConfig,String pObjectName,String ... pAttribute) throws MalformedObjectNameException {
        this(pTargetConfig,new ObjectName(pObjectName),pAttribute);
    }

    /**
     * Get all attributes of this request. This list can be empty if all attributes
     * should be fetched.
     *
     * @return attributes
     */
    public Collection<String> getAttributes() {
        return attributes;
    }

    /**
     * If this request is for a single attribute, this attribute is returned
     * by this getter.
     * @return single attribute
     * @throws IllegalArgumentException if no or more than one attribute are used when this request was
     *         constructed.
     */
    public String getAttribute() {
        if (!hasSingleAttribute()) {
            throw new IllegalArgumentException("More than one attribute given for this request");
        }
        return attributes.get(0);
    }

    /** {@inheritDoc} */
    @Override
    List<String> getRequestParts() {
        if (hasSingleAttribute()) {
            List<String> ret = super.getRequestParts();
            ret.add(getAttribute());
            addPath(ret,path);
            return ret;
        } else if (hasAllAttributes() && path == null) {
            return super.getRequestParts();
        }

        // A GET request cant be used for multiple attribute fetching or for fetching
        // all attributes with a path
        return null;
    }

    /** {@inheritDoc} */
    @Override
    JSONObject toJson() {
        JSONObject ret = super.toJson();
        if (hasSingleAttribute()) {
            ret.put("attribute",attributes.get(0));
        } else if (!hasAllAttributes()) {
            JSONArray attrs = new JSONArray();
            attrs.addAll(attributes);
            ret.put("attribute",attrs);
        }
        if (path != null) {
            ret.put("path",path);
        }
        return ret;
    }

    /** {@inheritDoc} */
    @Override
    J4pReadResponse createResponse(JSONObject pResponse) {
        return new J4pReadResponse(this,pResponse);
    }

    /**
     * Whether this request represents a request for a single attribute
     *
     * @return true if the client request is for a single attribute
     */
    public boolean hasSingleAttribute() {
        return attributes.size() == 1;
    }

    /**
     * Whether all attributes should be fetched
     *
     * @return true if all attributes should be fetched
     */
    public boolean hasAllAttributes() {
        return attributes.size() == 0;
    }

    /**
     * Get the path for extracting parts of the return value
     *
     * @return path used for extracting
     */
    public String getPath() {
        return path;
    }

    /**
     * Set the path for diving into the return value
     *
     * @param pPath path to set
     */
    public void setPath(String pPath) {
        path = pPath;
    }
}
