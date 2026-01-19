/*
 * Copyright 2009-2026 Roland Huss
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

import java.util.*;

import javax.management.ObjectName;

import org.jolokia.core.util.EscapeUtil;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.json.JSONObject;

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
    private List<?> arguments;

    /** Flag set after calling {@link #splitArgumentsAndPath} to not call it anymore */
    private boolean pathCreated;

    /**
     * Constructor for creating a {@link JolokiaExecRequest} resulting from an HTTP GET request
     *
     * @param pObjectName name of MBean to execute the operation upon. Must not be null.
     * @param pOperation name of the operation to execute. Must not be null.
     * @param pArguments arguments to to used for executing the request. Can be null
     * @param pParams optional params used for processing the request.
     * @throws BadRequestException if the object name is not in proper format
     */
    JolokiaExecRequest(String pObjectName, String pOperation, List<?> pArguments,
                       ProcessingParameters pParams) throws BadRequestException {
        super(RequestType.EXEC, pObjectName, null /* path is not supported for exec requests */, pParams, true);
        operation = pOperation;
        arguments = pArguments;
    }

    /**
     * Constructor for creating a {@link JolokiaExecRequest} resulting from an HTTP POST request
     *
     * @param pRequestMap request in object format
     * @param pParams optional processing parameters
     * @throws BadRequestException if the object name is not in proper format
     */
    JolokiaExecRequest(Map<String, ?> pRequestMap, ProcessingParameters pParams) throws BadRequestException {
        super(pRequestMap, pParams, true);
        operation = (String) pRequestMap.get("operation");
        arguments = (List<?>) pRequestMap.get("arguments");
    }

    /**
     * When performing pattern exec requests, we need to do a copy of existing request with specific (from search)
     * ObjectName.
     *
     * @param name
     * @return
     */
    public JolokiaExecRequest withChangedObjectName(ObjectName name) {
        try {
            return new JolokiaExecRequest(name.getCanonicalName(), operation, arguments, this.processingConfig);
        } catch (BadRequestException e) {
            // should not really happen, because we're dealing with existing ObjectName
            // so this is "special" IllegalArgumentException
            throw new IllegalArgumentException(e.getMessage(), e);
        }
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
    public JSONObject toJSON() {
        JSONObject ret = super.toJSON();
        if (arguments != null && !arguments.isEmpty()) {
            ret.put("arguments", arguments);
        }
        ret.put("operation", operation);
        return ret;
    }

    /**
     * For exec requests, <em>after</em> we decide on the number of arguments, we may want to change the
     * request, so it contains valid path and arguments.
     *
     * @param nrParams new argument count
     * @param pathParts path parts created from extra arguments
     */
    public void splitArgumentsAndPath(int nrParams, List<String> pathParts) {
        if (pathCreated) {
            return;
        }
        this.arguments = this.arguments.subList(0, nrParams);
        this.setPathParts(pathParts);
        this.pathCreated = true;
    }

    // =================================================================================

    /**
     * Creator for {@link JolokiaExecRequest}s
     *
     * @return the creator implementation
     */
    static RequestCreator<JolokiaExecRequest> newCreator() {
        return new RequestCreator<>() {
            @Override
            public JolokiaExecRequest create(Deque<String> pStack, ProcessingParameters pParams) throws BadRequestException {
                if (pStack == null || pStack.size() < 2) {
                    throw new BadRequestException("Exec GET requests require at least two path elements");
                }
                return new JolokiaExecRequest(
                        pStack.pop(), // Object name
                        pStack.pop(), // Operation name
                        convertSpecialStringTags(prepareExtraArgs(pStack)), // arguments - consume entire path
                        pParams);
            }

            @Override
            public JolokiaExecRequest create(JSONObject requestMap, ProcessingParameters pParams)
                    throws BadRequestException {
                if (requestMap == null) {
                    throw new BadRequestException("Can't create Exec POST request");
                }
                if (!requestMap.containsKey("mbean")) {
                    throw new BadRequestException("Exec POST requests require an ObjectName to invoke");
                }
                if (!requestMap.containsKey("operation")) {
                    throw new BadRequestException("Exec POST requests require an operation name to invoke");
                }
                return new JolokiaExecRequest(requestMap, pParams);
            }
        };
    }

    // Convert string tags if required
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
        StringBuilder ret = new StringBuilder("JolokiaExecRequest[");
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
