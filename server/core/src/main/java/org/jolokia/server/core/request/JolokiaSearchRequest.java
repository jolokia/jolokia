/*
 * Copyright 2009-20126 Roland Huss
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

import java.util.Deque;
import java.util.Map;

import org.jolokia.json.JSONObject;
import org.jolokia.server.core.util.RequestType;

/**
 * A JMX request for a {@link RequestType#SEARCH} operation, i.e. for searching MBeans. Handles single
 * additional argument, which may be an {@link javax.management.ObjectName} (or pattern) for filtering out
 * the response.
 *
 * @author roland
 * @since 15.03.11
 */
public class JolokiaSearchRequest extends JolokiaObjectNameRequest {

    /**
     * Constructor for GET requests.
     *
     * @param pObjectName object name pattern to search for. If it's null, it's used as {@code *:*} pattern.
     * @param pParams optional processing parameters
     * @throws BadRequestException if the name is not a proper object name
     */
    JolokiaSearchRequest(String pObjectName, ProcessingParameters pParams) throws BadRequestException {
        super(RequestType.SEARCH, pObjectName, null, pParams, false);
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     * @throws BadRequestException if the name is not a proper object name
     */
    JolokiaSearchRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams) throws BadRequestException {
        super(pRequestMap, pParams, false);
    }


    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("JolokiaSearchRequest[");
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
            @Override
            public JolokiaSearchRequest create(Deque<String> pStack, ProcessingParameters pParams) throws BadRequestException {
                return new JolokiaSearchRequest(pStack.isEmpty() ? null : pStack.pop(), pParams);
            }

            @Override
            public JolokiaSearchRequest create(JSONObject requestMap, ProcessingParameters pParams) throws BadRequestException {
                // we accept empty mbean parameter - means "search for all"
                return new JolokiaSearchRequest(requestMap, pParams);
            }
        };
    }

}
