package org.jolokia.server.core.request;

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

import org.jolokia.server.core.util.EscapeUtil;
import org.jolokia.server.core.util.RequestType;
import org.json.simple.JSONObject;

/**
 * A JMX request for a <code>write</code> operation
 *
 * @author roland
 * @since 15.03.11
 */
public class JolokiaWriteRequest extends JolokiaObjectNameRequest {

    // The value to set
    private final Object value;

    // The attribute name
    private final String attributeName;

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
    JolokiaWriteRequest(String pObjectName, String pAttribute, Object pValue, List<String> pPathParts,
                        ProcessingParameters pInitParams) throws MalformedObjectNameException {
        super(RequestType.WRITE, pObjectName, pPathParts, pInitParams, true);
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
    JolokiaWriteRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams) throws MalformedObjectNameException {
        super(pRequestMap, pParams, true);
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

    /**
     * The old value is returned directly, hence we do not want any path conversion
     * on this value
     *
     * @return false;
     */
    @Override
    public boolean useReturnValueWithPath() {
        return false;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("JmxWriteRequest[");
        ret.append("attribute=").append(getAttributeName())
                    .append(", value=").append(getValue());
        String baseInfo = getInfo();
        if (baseInfo != null) {
            ret.append(", ").append(baseInfo);
        }
        ret.append("]");
        return ret.toString();
    }

    @SuppressWarnings("unchecked")
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
     * Creator for {@link JolokiaWriteRequest}s
     *
     * @return the creator implementation
     */
    static RequestCreator<JolokiaWriteRequest> newCreator() {
        return new RequestCreator<>() {
            /** {@inheritDoc} */
            public JolokiaWriteRequest create(Stack<String> pStack, ProcessingParameters pParams) throws MalformedObjectNameException {
                return new JolokiaWriteRequest(
                        pStack.pop(), // object name
                        pStack.pop(), // attribute name
                        EscapeUtil.convertSpecialStringTags(pStack.pop()), // value
                        prepareExtraArgs(pStack), // path
                        pParams);
            }

            /** {@inheritDoc} */
            public JolokiaWriteRequest create(Map<String, ?> requestMap, ProcessingParameters pParams)
                    throws MalformedObjectNameException {
                return new JolokiaWriteRequest(requestMap, pParams);
            }
        };
    }
}
