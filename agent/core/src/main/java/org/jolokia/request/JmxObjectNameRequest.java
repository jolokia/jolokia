/*
 * Copyright 2011 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.request;

import java.util.List;
import java.util.Map;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

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
     * @param pInitParams optional init params
     * @throws MalformedObjectNameException if the given MBean name is not a valid object name
     */
    public JmxObjectNameRequest(RequestType pType, String pObjectName, List<String> pPathParts, Map<String, String> pInitParams)
            throws MalformedObjectNameException {
        super(pType,pPathParts,pInitParams);
        initObjectName(pObjectName);
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     * @throws MalformedObjectNameException if the given MBean name (key: "mbean") is not a valid object name.
     */
    public JmxObjectNameRequest(Map<String, ?> pRequestMap, Map<String, String> pParams) throws MalformedObjectNameException {
        super(pRequestMap, pParams);
        initObjectName((String) pRequestMap.get("mbean"));
    }


    @Override
    public JSONObject toJSON() {
        JSONObject ret = super.toJSON();
        ret.put("mbean",objectName.getCanonicalName());
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
     * String representation of the object name
     *
     * @return the object name a string representation
     */
    public String getObjectNameAsString() {
        return objectName.getCanonicalName();
    }


    private void initObjectName(String pObjectName) throws MalformedObjectNameException {
        if (pObjectName == null) {
            throw new IllegalArgumentException("Objectname can not be null");
        }
        objectName = new ObjectName(pObjectName);
    }

}
