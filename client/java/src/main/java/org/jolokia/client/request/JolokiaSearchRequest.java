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
package org.jolokia.client.request;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.client.JolokiaOperation;
import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.client.response.JolokiaSearchResponse;
import org.jolokia.json.JSONObject;

/**
 * <p>Request for a MBean search where the only argument is {@link ObjectName} pattern. No path is supported
 * to retrieve data from the response and the response is always a list of Strings representing found
 * MBean names.</p>
 *
 * <p>JSON form of a "search" {@link HttpMethod#POST} is:<pre>{@code
 * {
 *     "type": "read",
 *     "mbean": "object-name-or-pattern",
 *     "target": {
 *         "url": "target remote JMX URI",
 *         "user": "remote JMX connector username",
 *         "password": "remote JMX connector password"
 *     }
 * }
 * }</pre>
 * </p>
 *
 * @author roland
 * @since 26.03.11
 */
public class JolokiaSearchRequest extends JolokiaMBeanRequest {

    /**
     * Create request with a {@link ObjectName}, which can be a pattern
     *
     * @param pMBeanPattern pattern to use for a search
     */
    public JolokiaSearchRequest(ObjectName pMBeanPattern) {
        this(null, pMBeanPattern);
    }

    /**
     * Create request with a {@link ObjectName}, which can be a pattern
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pMBeanPattern pattern to use for a search
     */
    public JolokiaSearchRequest(JolokiaTargetConfig pTargetConfig, ObjectName pMBeanPattern) {
        super(JolokiaOperation.SEARCH, pMBeanPattern, pTargetConfig);
    }

    /**
     * Create a search request
     *
     * @param pMBeanPattern MBean pattern as string
     * @throws MalformedObjectNameException if the provided pattern is not a valid {@link ObjectName}
     */
    public JolokiaSearchRequest(String pMBeanPattern) throws MalformedObjectNameException {
        this(null, pMBeanPattern);
    }

    /**
     * Create a search request
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pMBeanPattern MBean pattern as string
     * @throws MalformedObjectNameException if the provided pattern is not a valid {@link ObjectName}
     */
    public JolokiaSearchRequest(JolokiaTargetConfig pTargetConfig, String pMBeanPattern) throws MalformedObjectNameException {
        this(pTargetConfig, new ObjectName(pMBeanPattern));
    }

    @Override
    @SuppressWarnings("unchecked")
    public JolokiaSearchResponse createResponse(JSONObject pResponse) {
        return new JolokiaSearchResponse(this, pResponse);
    }

}
