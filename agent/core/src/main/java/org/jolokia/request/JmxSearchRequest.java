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

import javax.management.MalformedObjectNameException;

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
    JmxSearchRequest(String pObjectName, Map<String, String> pParams) throws MalformedObjectNameException {
        super(RequestType.SEARCH, pObjectName, null, pParams);
    }

    /**
     * Constructor for POST requests
     *
     * @param pRequestMap object representation of the request
     * @param pParams processing parameters
     * @throws MalformedObjectNameException if the name is not a proper object name
     */
    JmxSearchRequest(Map<String, ?> pRequestMap, Map<String, String> pParams) throws MalformedObjectNameException {
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
}
