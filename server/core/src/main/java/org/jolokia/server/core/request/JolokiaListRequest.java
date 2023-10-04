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

import org.jolokia.server.core.util.RequestType;

/**
 * A JMX request for <code>list</code> operations, i.e. for listing JMX metadata
 *
 * @author roland
 * @since 15.03.11
 */
public class JolokiaListRequest extends JolokiaRequest {

    /**
     * Constructor for GET requests.
     *
     * @param pPathParts parts of a path to restrict on the return value
     * @param pParams processing parameters
     */
    JolokiaListRequest(List<String> pPathParts, ProcessingParameters pParams) {
        super(RequestType.LIST,pPathParts,pParams,false);
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     */
    JolokiaListRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams) {
        super(pRequestMap, pParams,false);
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("JmxListRequest[");
        String baseInfo = getInfo();
        if (baseInfo != null) {
            ret.append(baseInfo);
        }
        ret.append("]");
        return ret.toString();
    }

    /**
     * Path handling is done directly within this handler to avoid
     * excessive memory consumption by building up the whole list
     * into memory only for extracting a part from it. So we return false here.
     *
     * @return always false
     */
    @Override
    public boolean useReturnValueWithPath() {
        return false;
    }

    // ====================================================================================

    /**
     * Create a new creator used for creating list requests
     *
     * @return creator
     */
    static RequestCreator<JolokiaListRequest> newCreator() {
        return new RequestCreator<>() {
            /** {@inheritDoc} */
            public JolokiaListRequest create(Stack<String> pStack, ProcessingParameters pParams) {
                return new JolokiaListRequest(
                        prepareExtraArgs(pStack), // path
                        pParams);
            }

            /** {@inheritDoc} */
            public JolokiaListRequest create(Map<String, ?> requestMap, ProcessingParameters pParams) {
                return new JolokiaListRequest(requestMap, pParams);
            }
        };
    }
}
