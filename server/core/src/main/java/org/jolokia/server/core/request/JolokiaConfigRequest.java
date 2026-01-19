/*
 * Copyright 2009-2025 Roland Huss
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
 * A JMX request for a {@link RequestType#CONFIG} request. Doesn't require any parameters.
 */
public class JolokiaConfigRequest extends JolokiaRequest {

    /**
     * A version request for GET requests
     *
     * @param pInitParams optional init parameters
     */
    JolokiaConfigRequest(ProcessingParameters pInitParams) throws BadRequestException {
        super(RequestType.CONFIG, null, pInitParams, true);
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams     processing parameters
     */
    JolokiaConfigRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams) throws BadRequestException {
        super(pRequestMap, pParams, true);
    }

    @Override
    public String toString() {
        return "JmxConfigRequest[]";
    }

    /**
     * Creator for {@link JolokiaConfigRequest}s
     *
     * @return the creator implementation
     */
    static RequestCreator<JolokiaConfigRequest> newCreator() {
        return new RequestCreator<>() {
            @Override
            public JolokiaConfigRequest create(Deque<String> pStack, ProcessingParameters pParams) throws BadRequestException {
                if (!pStack.isEmpty()) {
                    throw new BadRequestException("Illegal path arguments for configuration endpoint");
                }
                return new JolokiaConfigRequest(pParams);
            }

            @Override
            public JolokiaConfigRequest create(JSONObject requestMap, ProcessingParameters pParams) throws BadRequestException {
                return new JolokiaConfigRequest(requestMap, pParams);
            }
        };
    }

}
