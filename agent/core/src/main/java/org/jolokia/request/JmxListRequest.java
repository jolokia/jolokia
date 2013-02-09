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
import org.jolokia.util.RequestType;

/**
 * A JMX request for <code>list</code> operations, i.e. for listing JMX metadata
 *
 * @author roland
 * @since 15.03.11
 */
public class JmxListRequest extends JmxRequest {

    /**
     * Constructor for GET requests.
     *
     * @param pPathParts parts of a path to restrict on the return value
     * @param pParams processing parameters
     */
    JmxListRequest(List<String> pPathParts, ProcessingParameters pParams) {
        super(RequestType.LIST,pPathParts,pParams);
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     */
    JmxListRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams) {
        super(pRequestMap, pParams);
    }

    @Override
    public String toString() {
        StringBuffer ret = new StringBuffer("JmxListRequest[");
        String baseInfo = getInfo();
        if (baseInfo != null) {
            ret.append(baseInfo);
        }
        ret.append("]");
        return ret.toString();
    }

    // ====================================================================================

    /**
     * Create a new creator used for creating list requests
     *
     * @return creator
     */
    static RequestCreator<JmxListRequest> newCreator() {
        return new RequestCreator<JmxListRequest>() {
            /** {@inheritDoc} */
            public JmxListRequest create(Stack<String> pStack, ProcessingParameters pParams) throws MalformedObjectNameException {
                return new JmxListRequest(
                        prepareExtraArgs(pStack), // path
                        pParams);
            }

            /** {@inheritDoc} */
            public JmxListRequest create(Map<String, ?> requestMap, ProcessingParameters pParams)
                    throws MalformedObjectNameException {
                return new JmxListRequest(requestMap,pParams);
            }
        };
    }
}
