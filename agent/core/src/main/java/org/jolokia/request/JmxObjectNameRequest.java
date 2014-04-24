package org.jolokia.request;

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
import java.util.Map;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.config.ConfigKey;
import org.jolokia.config.ProcessingParameters;
import org.jolokia.util.RequestType;
import org.json.simple.JSONObject;

/**
 * Abstract JMX request which takes an object name.
 *
 * @author roland
 * @since 15.03.11
 */
public abstract class JmxObjectNameRequest extends JmxRequest {

    // object name of the MBean
    private ObjectName objectName;

    /**
     * Constructor for GET requests
     *
     * @param pType request type
     * @param pObjectName object name, which must not be null.
     * @param pPathParts parts of an path
     * @param pProcessingParams optional init params
     * @throws MalformedObjectNameException if the given MBean name is not a valid object name
     */
    public JmxObjectNameRequest(RequestType pType, String pObjectName, List<String> pPathParts, ProcessingParameters pProcessingParams)
            throws MalformedObjectNameException {
        super(pType,pPathParts,pProcessingParams);
        initObjectName(pObjectName);
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     * @throws MalformedObjectNameException if the given MBean name (key: "mbean") is not a valid object name.
     */
    public JmxObjectNameRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams) throws MalformedObjectNameException {
        super(pRequestMap, pParams);
        initObjectName((String) pRequestMap.get("mbean"));
    }


    /** {@inheritDoc} */
    @Override
    public JSONObject toJSON() {
        JSONObject ret = super.toJSON();
        ret.put("mbean",getOrderedObjectName(objectName));
        return ret;
    }

    @Override
    protected String getInfo() {
        StringBuffer ret = new StringBuffer("objectName = ").append(objectName.getCanonicalName());
        String baseInfo = super.getInfo();
        if (baseInfo != null) {
            ret.append(", ").append(baseInfo);
        }
        return ret.toString();
    }

    /**
     * Get object name of MBean
     *
     * @return the object name
     */
    public ObjectName getObjectName() {
        return objectName;
    }

    /**
     * String representation of the object name for this request.
     *
     * @return the object name a string representation
     */
    public String getObjectNameAsString() {
        return objectName.getCanonicalName();
    }

    /**
     * Name prepared according to requested formatting note. The key ordering can be influenced by the
     * processing parameter {@link ConfigKey#CANONICAL_NAMING}. If not given or set to "true",
     * then the canonical order is used, if set to "initial" the name is given to construction time
     * is used.
     *
     * @param pName name to format
     * @return formatted string
     */
    public String getOrderedObjectName(ObjectName pName) {
        // For patterns we always return the canonical name
        if (pName.isPattern()) {
            return pName.getCanonicalName();
        }
        if (getParameterAsBool(ConfigKey.CANONICAL_NAMING)) {
            return pName.getCanonicalName();
        } else {
            return pName.getDomain() + ":" + pName.getKeyPropertyListString();
        }
    }

    private void initObjectName(String pObjectName) throws MalformedObjectNameException {
        if (pObjectName == null) {
            throw new IllegalArgumentException("Objectname can not be null");
        }
        objectName = new ObjectName(pObjectName);
    }

}
