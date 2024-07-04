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

import java.util.Deque;

import javax.management.MalformedObjectNameException;

import org.jolokia.server.core.util.RequestType;
import org.json.JSONObject;

/**
 * A JMX request for a <code>search</code> operation, i.e. for searching MBeans.
 *
 * @author roland
 * @since 15.03.11
 */
public class JolokiaSearchRequest extends JolokiaObjectNameRequest {

    /**
     * Constructor for GET requests.
     *
     * @param pObjectName object name pattern to search for, which must not be null.
     * @param pParams optional processing parameters
     * @throws MalformedObjectNameException if the name is not a proper object name
     */
    JolokiaSearchRequest(String pObjectName, ProcessingParameters pParams) throws MalformedObjectNameException {
        super(RequestType.SEARCH, pObjectName, null, pParams, false);
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     * @throws MalformedObjectNameException if the name is not a proper object name
     */
    JolokiaSearchRequest(JSONObject pRequestMap, ProcessingParameters pParams) throws MalformedObjectNameException {
        super(pRequestMap, pParams, false);
    }


    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("JmxSearchRequest[");
        String baseInfo = getInfo();
        if (baseInfo != null) {
            ret.append(baseInfo);
        }
        ret.append("]");
        return ret.toString();
    }

    // ===========================================================================================

    /**
     * Creator for {@link JolokiaSearchRequest}s
     *
     * @return the creator implementation
     */
    static RequestCreator<JolokiaSearchRequest> newCreator() {
        return new RequestCreator<>() {
            /** {@inheritDoc} */
            public JolokiaSearchRequest create(Deque<String> pStack, ProcessingParameters pParams) throws MalformedObjectNameException {
                return new JolokiaSearchRequest(pStack.pop(),pParams);
            }

            /** {@inheritDoc} */
            public JolokiaSearchRequest create(JSONObject requestMap, ProcessingParameters pParams)
                    throws MalformedObjectNameException {
                return new JolokiaSearchRequest(requestMap,pParams);
            }
        };
    }
}
