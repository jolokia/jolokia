/*
 * Copyright 2011 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.request;

import java.util.Map;

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
    public JmxVersionRequest(Map<String, String> pInitParams) {
        super(RequestType.VERSION,null,pInitParams);
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     */
    public JmxVersionRequest(Map<String, ?> pRequestMap, Map<String, String> pParams) {
        super(pRequestMap, pParams);
    }

    @Override
    public String toString() {
        return "JmxVersionRequest[]";
    }
}
