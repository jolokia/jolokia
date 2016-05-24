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

import org.jolokia.config.ConfigKey;
import org.jolokia.config.ProcessingParameters;
import org.jolokia.util.RequestType;

import javax.management.MalformedObjectNameException;
import java.util.Map;
import java.util.Stack;

/**
 * A "hot threads"-request.
 *
 */
public class JmxHotThreadsRequest extends JmxRequest {

    public int interval;
    public int numberOfThreads;

    /**
     * A version request for GET requests
     *
     * @param pInitParams optional init parameters
     */
    JmxHotThreadsRequest(ProcessingParameters pInitParams) {
        super(RequestType.HOT_THREADS,null,pInitParams);
        setRequestParams(pInitParams);
    }

    private void setRequestParams(ProcessingParameters pInitParams) {
        interval = Integer.parseInt(pInitParams.get(ConfigKey.HOT_THREADS_INTERVAL));
        numberOfThreads = Integer.parseInt(pInitParams.get(ConfigKey.HOT_THREADS_COUNT));
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     */
    JmxHotThreadsRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams) {
        super(pRequestMap, pParams);
        setRequestParams(pParams);
    }

    @Override
    public String toString() {
        return "JmxHotThreadsRequest[]";
    }

    // =================================================================

    /**
     * Creator for {@link JmxHotThreadsRequest}s
     *
     * @return the creator implementation
     */
    static RequestCreator<JmxHotThreadsRequest> newCreator() {
        return new RequestCreator<JmxHotThreadsRequest>() {
            /** {@inheritDoc} */
            public JmxHotThreadsRequest create(Stack<String> pStack, ProcessingParameters pParams) throws MalformedObjectNameException {
                return new JmxHotThreadsRequest(pParams);
            }

            /** {@inheritDoc} */
            public JmxHotThreadsRequest create(Map<String, ?> requestMap, ProcessingParameters pParams)
                    throws MalformedObjectNameException {
                return new JmxHotThreadsRequest(requestMap,pParams);
            }
        };
    }
}
