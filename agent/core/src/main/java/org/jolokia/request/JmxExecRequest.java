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

import java.util.List;
import java.util.Map;

import javax.management.MalformedObjectNameException;

import org.jolokia.util.RequestType;
import org.json.simple.JSONObject;

/**
 * A JMX request for <code>exec</code> operations, i.e. for executing JMX operations
 * on MBeans.
 *
 * @author roland
 * @since 15.03.11
 */
public class JmxExecRequest extends JmxObjectNameRequest {

    // Name of operation to execute
    private String operation;

    // List of arguments for the operation to execute. Can be either already of the
    // proper type or, if not, in a string representation.
    private List arguments;

    /**
     * Constructor for creating a JmxRequest resulting from an HTTP GET request
     *
     * @param pObjectName name of MBean to execute the operation upon. Must not be null.
     * @param pOperation name of the operation to execute. Must not be null.
     * @param pArguments arguments to to used for executing the request. Can be null
     * @param pParams optional params used for processing the request.
     * @throws MalformedObjectNameException if the object name is not in proper format
     */
    public JmxExecRequest(String pObjectName,String pOperation,List pArguments,
                   Map<String, String> pParams) throws MalformedObjectNameException {
        super(RequestType.EXEC, pObjectName, null /* path is not supported for exec requests */, pParams);
        operation = pOperation;
        arguments = pArguments;
    }

    /**
     * Constructor for creating a JmxRequest resulting from an HTTP POST request
     *
     * @param pRequestMap request in object format
     * @param pParams optional processing parameters
     * @throws MalformedObjectNameException if the object name is not in proper format
     */
    public JmxExecRequest(Map<String, ?> pRequestMap, Map<String, String> pParams) throws MalformedObjectNameException {
        super(pRequestMap, pParams);
        arguments = (List) pRequestMap.get("arguments");
        operation = (String) pRequestMap.get("operation");
    }

    /**
     * Get the operation name
     *
     * @return the operation name for this exec request
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Get arguments or null if no arguments are given
     *
     * @return arguments
     */
    public List getArguments() {
        return arguments;
    }

    /**
     * Return this request in a proper JSON representation
     * @return this object in a JSON representation
     */
    public JSONObject toJSON() {
        JSONObject ret = super.toJSON();
        if (arguments != null && arguments.size() > 0) {
            ret.put("arguments", arguments);
        }
        ret.put("operation", operation);
        return ret;
    }

    @Override
    public String toString() {
        StringBuffer ret = new StringBuffer("JmxExecRequest[");
        ret.append("operation=").append(getOperation());
        if (arguments != null && arguments.size() > 0) {
            ret.append(", arguments=").append(getArguments());
        }
        String baseInfo = getInfo();
        if (baseInfo != null) {
            ret.append(", ").append(baseInfo);
        }
        ret.append("]");
        return ret.toString();
    }
}
