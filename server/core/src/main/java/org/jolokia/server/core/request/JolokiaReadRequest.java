/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.server.core.request;

import java.util.*;

import org.jolokia.core.util.EscapeUtil;
import org.jolokia.json.JSONArray;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.json.JSONObject;

/**
 * A JMX request for <code>read</code> operations, i.e. for reading JMX attributes
 * on MBeans.
 *
 * @author roland
 * @since 15.03.11
 */
public class JolokiaReadRequest extends JolokiaObjectNameRequest {

    /** Single attribute mode */
    private String attributeName = null;

    /** Multi-attribute mode (including all attributes when the list is empty */
    private List<String> attributeNames;

    /** Whether multiple (or all) attributes are to be fetched. This is false for a request to get one attribute. */
    private boolean multiAttributeMode = false;

    /**
     * Constructor for GET requests
     *
     * @param pObjectName object name of MBean to read attributes from
     * @param pAttribute a one or more attribute to lookup. Can be null in which case all attributes
     *                   are to be fetched. More than one attribute can be provided by giving it a comma
     *                   separated list (attribute names with commas can not be use then, though).
     * @param pPathParts optional path parts from for filtering the return value
     * @param pInitParams optional processing parameters
     * @throws BadRequestException if the name is not a proper object name.
     */
    JolokiaReadRequest(String pObjectName, String pAttribute, List<String> pPathParts, ProcessingParameters pInitParams)
            throws BadRequestException {
        super(RequestType.READ, pObjectName, pPathParts, pInitParams, true);
        detectAttributesToRead(pAttribute);
    }

    /**
     * Constructor for creating a {@link JolokiaReadRequest} resulting from an HTTP POST request
     *
     * @param pRequestMap request in object format
     * @param pParams optional processing parameters
     * @throws BadRequestException if the object name extracted is not a proper object name.
     */
    JolokiaReadRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams) throws BadRequestException {
        super(pRequestMap, pParams, true);
        detectAttributesToRead(pRequestMap.get("attribute"));
    }

    /**
     * Get a single attribute name. If this request was initialized with more than one attribute this
     * methods throws an exception.
     * @return the attribute's name
     * @throws IllegalStateException if this request was initialized with more than one attribute.
     */
    public String getAttributeName() {
        if (multiAttributeMode) {
            throw new IllegalStateException("READ Request is for " + (attributeNames.isEmpty() ? "all" : attributeNames.size())
                + " attributes, use getAttributeNames() instead.");
        }
        return attributeName;
    }

    /**
     * Get the list of all attribute names (possibly empty = all attributes).
     *
     * @return list of attributes names to read
     * @throws IllegalStateException if this request was initialized for only one attribute
     */
    public List<String> getAttributeNames() {
        if (!multiAttributeMode) {
            throw new IllegalStateException("READ Request is for single \"" + attributeName
                + "\" attribute, use getAttributeName() instead.");
        }
        return attributeNames;
    }

    /**
     * Whether this is a multi-attribute request (all or more than one attributes to read from an MBean)
     * @return true if this is a multi attribute request, false otherwise.
     */
    public boolean isMultiAttributeMode() {
        return multiAttributeMode;
    }

    @Override
    public JSONObject toJSON() {
        JSONObject ret = super.toJSON();
        ret.put("attribute", multiAttributeMode ? new JSONArray(attributeNames) : attributeName);
        return ret;
    }

    /**
     * Creator for {@link JolokiaReadRequest}s
     *
     * @return the creator implementation
     */
    static RequestCreator<JolokiaReadRequest> newCreator() {
        return new RequestCreator<>() {
            @Override
            public JolokiaReadRequest create(Deque<String> pStack, ProcessingParameters pParams)
                    throws BadRequestException {
                if (pStack == null || pStack.isEmpty()) {
                    throw new BadRequestException("Read GET requests require at least 1 path element for ObjectName to read from");
                }
                return new JolokiaReadRequest(
                        pStack.pop(),             // object name
                        popOrNull(pStack),        // attribute(s) (can be null)
                        prepareExtraArgs(pStack), // path
                        pParams);
            }

            @Override
            public JolokiaReadRequest create(JSONObject requestMap, ProcessingParameters pParams)
                    throws BadRequestException {
                if (requestMap == null || !requestMap.containsKey("mbean")) {
                    throw new BadRequestException("Read POST requests require an ObjectName to read from (may be a pattern)");
                }
                return new JolokiaReadRequest(requestMap, pParams);
            }
        };
    }

    @Override
    public String toString() {
        StringBuffer ret = new StringBuffer("JolokiaReadRequest[");
        appendReadParameters(ret);
        String baseInfo = getInfo();
        if (baseInfo != null) {
            ret.append(", ").append(baseInfo);
        }
        ret.append("]");
        return ret.toString();
    }

    private void appendReadParameters(StringBuffer pRet) {
        if (multiAttributeMode) {
            pRet.append("attribute=[");
            for (int i = 0; i < attributeNames.size(); i++) {
                pRet.append(attributeNames.get(i));
                if (i < attributeNames.size() - 1) {
                    pRet.append(",");
                }
            }
            pRet.append("]");
        } else {
            pRet.append("attribute=").append(getAttributeName());
        }
    }

    /**
     * Roughly we can get one, more or all of attributes for an MBean.
     *
     * @param config
     */
    private void detectAttributesToRead(Object config) throws BadRequestException {
        List<String> names;
        if (config instanceof String) {
            names = EscapeUtil.split((String) config, EscapeUtil.CSV_ESCAPE, ",");
            multiAttributeMode = names == null || names.size() != 1;
        } else if (config instanceof Collection<?> col) {
            names = new ArrayList<>();
            for (Object v : col) {
                if (v instanceof String name && !name.trim().isEmpty()) {
                    names.add(name);
                }
            }
            // Even one-element collection means "fetch me all attributes" - for compatibility reasons
//            multiAttributeMode = names.size() != 1;
            multiAttributeMode = true;
        } else if (config == null) {
            names = new ArrayList<>();
            // we want all attributes
            multiAttributeMode = true;
        } else {
            throw new BadRequestException("Can't determine attribute names to read from " + this.getObjectName()
                    + " from " + config.getClass() + " parameter");
        }

        if (!multiAttributeMode) {
            attributeName = names.get(0);
            // just to be safe:
            if (attributeName == null || attributeName.trim().isEmpty()) {
                multiAttributeMode = true;
                attributeName = null;
            }
        }
        if (multiAttributeMode) {
            attributeNames = names == null ? new ArrayList<>() : new ArrayList<>(names);
        }
    }

}
