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

import org.jolokia.server.core.util.EscapeUtil;
import org.jolokia.server.core.util.RequestType;
import org.json.simple.JSONObject;

/**
 * A JMX request for <code>exec</code> operations, i.e. for executing JMX operations
 * on MBeans.
 *
 * @author roland
 * @since 15.03.11
 */
public class JolokiaExecRequest extends JolokiaObjectNameRequest {

    // Name of operation to execute
    private final String operation;

    // List of arguments for the operation to execute. Can be either already of the
    // proper type or, if not, in a string representation.
    private final List<?> arguments;

    /**
     * Constructor for creating a JmxRequest resulting from an HTTP GET request
     *
     * @param pObjectName name of MBean to execute the operation upon. Must not be null.
     * @param pOperation name of the operation to execute. Must not be null.
     * @param pArguments arguments to to used for executing the request. Can be null
     * @param pParams optional params used for processing the request.
     * @throws MalformedObjectNameException if the object name is not in proper format
     */
    JolokiaExecRequest(String pObjectName, String pOperation, List<?> pArguments,
                       ProcessingParameters pParams) throws MalformedObjectNameException {
        super(RequestType.EXEC, pObjectName, null /* path is not supported for exec requests */, pParams, true);
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
    JolokiaExecRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams) throws MalformedObjectNameException {
        super(pRequestMap, pParams, true);
        arguments = (List<?>) pRequestMap.get("arguments");
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
    public List<?> getArguments() {
        return arguments;
    }

    /**
     * Return this request in a proper JSON representation
     * @return this object in a JSON representation
     */
    @SuppressWarnings("unchecked")
    public JSONObject toJSON() {
        JSONObject ret = super.toJSON();
        if (arguments != null && !arguments.isEmpty()) {
            ret.put("arguments", arguments);
        }
        ret.put("operation", operation);
        return ret;
    }

    // =================================================================================

    /**
     * Creator for {@link JolokiaExecRequest}s
     *
     * @return the creator implementation
     */
    static RequestCreator<JolokiaExecRequest> newCreator() {
        return new RequestCreator<>() {
            /** {@inheritDoc} */
            public JolokiaExecRequest create(Stack<String> pStack, ProcessingParameters pParams) throws MalformedObjectNameException {
                return new JolokiaExecRequest(
                        pStack.pop(), // Object name
                        pStack.pop(), // Operation name
                        convertSpecialStringTags(prepareExtraArgs(pStack)), // arguments
                        pParams);
            }

            /** {@inheritDoc} */
            public JolokiaExecRequest create(Map<String, ?> requestMap, ProcessingParameters pParams)
                    throws MalformedObjectNameException {
                return new JolokiaExecRequest(requestMap, pParams);
            }
        };
    }

    // Conver string tags if required
    private static List<String> convertSpecialStringTags(List<String> extraArgs) {
        if (extraArgs == null) {
            return null;
        }
        List<String> args = new ArrayList<>();
        for (String arg : extraArgs) {
            args.add(EscapeUtil.convertSpecialStringTags(arg));
        }
        return args;
    }


    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("JmxExecRequest[");
        ret.append("operation=").append(getOperation());
        if (arguments != null && !arguments.isEmpty()) {
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
