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

import java.util.*;

import javax.management.MalformedObjectNameException;

import org.jolokia.config.ProcessingParameters;
import org.jolokia.converter.object.StringToObjectConverter;
import org.jolokia.util.RequestType;
import org.json.simple.JSONObject;

/**
 * A JMX request for a <code>write</code> operation
 *
 * @author roland
 * @since 15.03.11
 */
public class JmxWriteRequest extends JmxObjectNameRequest {

    // The value to set
    private Object value;

    // The attribute name
    private String attributeName;

    /**
     * Constructor for creating a JmxRequest resulting from an HTTP GET request
     *
     * @param pObjectName the name of the MBean to set the object on (must be not null)
     * @param pAttribute attribute to the the value on. Must not be null.
     * @param pValue The value to set
     * @param pPathParts path parts to the inner part to set the valu on
     * @param pInitParams optional processing parameter
     * @throws MalformedObjectNameException if the object name is not well formed.
     */
    JmxWriteRequest(String pObjectName,String pAttribute,Object pValue,List<String> pPathParts,
                    ProcessingParameters pInitParams) throws MalformedObjectNameException {
        super(RequestType.WRITE, pObjectName, pPathParts, pInitParams);
        attributeName = pAttribute;
        value = pValue;
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     * @throws MalformedObjectNameException if the name is not a proper object name
     */
    JmxWriteRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams) throws MalformedObjectNameException {
        super(pRequestMap, pParams);
        value = pRequestMap.get("value");
        attributeName = (String) pRequestMap.get("attribute");
    }

    /**
     * Value to set for a write request
     *
     * @return the value to set
     */
    public Object getValue() {
        return value;
    }

    /**
     * Name of the attribute to set the value on
     *
     * @return the attribute name
     */
    public String getAttributeName() {
        return attributeName;
    }


    @Override
    public String toString() {
        StringBuffer ret = new StringBuffer("JmxWriteRequest[");
        ret.append("attribute=").append(getAttributeName())
                    .append(", value=").append(getValue());
        String baseInfo = getInfo();
        if (baseInfo != null) {
            ret.append(", ").append(baseInfo);
        }
        ret.append("]");
        return ret.toString();
    }

    @Override
    public JSONObject toJSON() {
        JSONObject ret = super.toJSON();
        if (attributeName != null) {
            ret.put("attribute", attributeName);
        }
        if (value != null) {
            ret.put("value", value);
        }
        return ret;
    }

    // ===========================================================================

    /**
     * Creator for {@link JmxWriteRequest}s
     *
     * @return the creator implementation
     */
    static RequestCreator<JmxWriteRequest> newCreator() {
        return new RequestCreator<JmxWriteRequest>() {
            /** {@inheritDoc} */
            public JmxWriteRequest create(Stack<String> pStack, ProcessingParameters pParams) throws MalformedObjectNameException {
                return new JmxWriteRequest(
                        pStack.pop(), // object name
                        pStack.pop(), // attribute name
                        StringToObjectConverter.convertSpecialStringTags(pStack.pop()), // value
                        prepareExtraArgs(pStack), // path
                        pParams);
            }

            /** {@inheritDoc} */
            public JmxWriteRequest create(Map<String, ?> requestMap, ProcessingParameters pParams)
                    throws MalformedObjectNameException {
                return new JmxWriteRequest(requestMap,pParams);
            }
        };
    }
}
