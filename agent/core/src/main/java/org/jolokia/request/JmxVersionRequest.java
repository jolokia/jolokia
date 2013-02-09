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
 * A JMX request for a <code>version</code> request.
 *
 * @author roland
 * @since 15.03.11
 */
public class JmxVersionRequest extends JmxRequest {

    /**
     * A version request for GET requests
     *
     * @param pInitParams optional init parameters
     */
    JmxVersionRequest(ProcessingParameters pInitParams) {
        super(RequestType.VERSION,null,pInitParams);
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     */
    JmxVersionRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams) {
        super(pRequestMap, pParams);
    }

    @Override
    public String toString() {
        return "JmxVersionRequest[]";
    }

    // =================================================================

    /**
     * Creator for {@link JmxVersionRequest}s
     *
     * @return the creator implementation
     */
    static RequestCreator<JmxVersionRequest> newCreator() {
        return new RequestCreator<JmxVersionRequest>() {
            /** {@inheritDoc} */
            public JmxVersionRequest create(Stack<String> pStack, ProcessingParameters pParams) throws MalformedObjectNameException {
                return new JmxVersionRequest(pParams);
            }

            /** {@inheritDoc} */
            public JmxVersionRequest create(Map<String, ?> requestMap, ProcessingParameters pParams)
                    throws MalformedObjectNameException {
                return new JmxVersionRequest(requestMap,pParams);
            }
        };
    }
}
