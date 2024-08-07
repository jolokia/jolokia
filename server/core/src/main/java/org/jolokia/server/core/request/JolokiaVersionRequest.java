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
import java.util.Map;

import org.jolokia.server.core.util.RequestType;

/**
 * A JMX request for a <code>version</code> request.
 *
 * @author roland
 * @since 15.03.11
 */
public class JolokiaVersionRequest extends JolokiaRequest {

    /**
     * A version request for GET requests
     *
     * @param pInitParams optional init parameters
     */
    JolokiaVersionRequest(ProcessingParameters pInitParams) {
        super(RequestType.VERSION,null,pInitParams,true);
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     */
    JolokiaVersionRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams) {
        super(pRequestMap, pParams,true);
    }

    @Override
    public String toString() {
        return "JmxVersionRequest[]";
    }

    // =================================================================

    /**
     * Creator for {@link JolokiaVersionRequest}s
     *
     * @return the creator implementation
     */
    static RequestCreator<JolokiaVersionRequest> newCreator() {
        return new RequestCreator<>() {
            /** {@inheritDoc} */
            public JolokiaVersionRequest create(Deque<String> pStack, ProcessingParameters pParams) {
                return new JolokiaVersionRequest(pParams);
            }

            /** {@inheritDoc} */
            public JolokiaVersionRequest create(Map<String, ?> requestMap, ProcessingParameters pParams) {
                return new JolokiaVersionRequest(requestMap, pParams);
            }
        };
    }
}
