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

import java.util.Map;
import java.util.Stack;

import javax.management.MalformedObjectNameException;

import org.jolokia.config.ProcessingParameters;
import org.jolokia.util.RequestType;

/**
 * A JMX request for a <code>search</code> operation, i.e. for searching MBeans.
 *
 * @author roland
 * @since 15.03.11
 */
public class JmxSearchRequest extends JmxObjectNameRequest {

    /**
     * Constructor for GET requests.
     *
     * @param pObjectName object name pattern to search for, which must not be null.
     * @param pParams optional processing parameters
     * @throws MalformedObjectNameException if the name is not a proper object name
     */
    JmxSearchRequest(String pObjectName, ProcessingParameters pParams) throws MalformedObjectNameException {
        super(RequestType.SEARCH, pObjectName, null, pParams);
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     * @throws MalformedObjectNameException if the name is not a proper object name
     */
    JmxSearchRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams) throws MalformedObjectNameException {
        super(pRequestMap, pParams);
    }


    @Override
    public String toString() {
        StringBuffer ret = new StringBuffer("JmxSearchRequest[");
        String baseInfo = getInfo();
        if (baseInfo != null) {
            ret.append(baseInfo);
        }
        ret.append("]");
        return ret.toString();
    }

    // ===========================================================================================

    /**
     * Creator for {@link JmxSearchRequest}s
     *
     * @return the creator implementation
     */
    static RequestCreator<JmxSearchRequest> newCreator() {
        return new RequestCreator<JmxSearchRequest>() {
            /** {@inheritDoc} */
            public JmxSearchRequest create(Stack<String> pStack, ProcessingParameters pParams) throws MalformedObjectNameException {
                return new JmxSearchRequest(pStack.pop(),pParams);
            }

            /** {@inheritDoc} */
            public JmxSearchRequest create(Map<String, ?> requestMap, ProcessingParameters pParams)
                    throws MalformedObjectNameException {
                return new JmxSearchRequest(requestMap,pParams);
            }
        };
    }
}
