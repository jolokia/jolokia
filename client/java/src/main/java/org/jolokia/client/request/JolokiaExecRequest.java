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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.client.EscapeUtil;
import org.jolokia.client.JolokiaOperation;
import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.client.response.JolokiaExecResponse;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;

/**
 * <p>A execute request for executing a JMX operation. Besides {@link ObjectName} we need operation name and
 * variable list of arguments for {@link javax.management.MBeanServerConnection#invoke}. Each argument is
 * generic object, subject to serialization mechanisms of Jolokia. Return value of an MBean operation is
 * returned in the response.</p>
 *
 * <p>Path to the return value is supported, but user must be aware that this may lead to ambiguity when dealing
 * with overloaded methods. As a rule, method with the most number of parameters is called, and the remaining
 * path arguments are used as a "path" into the return value.</p>
 *
 * <p>JSON form of a "exec" {@link HttpMethod#POST} is:<pre>{@code
 * {
 *     "type": "exec",
 *     "mbean": "object-name",
 *     "operation": "operation-name",
 *     "arguments": [ "arg1", "arg2", ..., "argN" ],
 *     "path": "path/to/response",
 *     "target": {
 *         "url": "target remote JMX URI",
 *         "user": "remote JMX connector username",
 *         "password": "remote JMX connector password"
 *     }
 * }
 * }</pre>
 * </p>
 *
 * <p>For {@link HttpMethod#GET}, the URI is: {@code /mbean/operation/arg1/arg2/.../argN/path/to/return/value}</p>
 *
 * @author roland
 * @since May 18, 2010
 */
public class JolokiaExecRequest extends JolokiaMBeanRequest {

    /** Operation name to invoke - for overloaded operations, entire signature may be provided */
    private final String operation;

    /** List of arguments to pass to the operation invocation */
    private final List<Object> arguments;

    /**
     * Path to retrieve an inner value of the response (which is an operation's return value). This path is already
     * escaped and uses {@code /} separator for nested path.
     */
    private String path;

    /**
     * New client request for executing a JMX operation
     *
     * @param pMBeanName name of the MBean to execute the request on
     * @param pOperation operation to execute
     * @param pArgs      any arguments to pass (which must match the JMX operation's declared signature)
     */
    public JolokiaExecRequest(ObjectName pMBeanName, String pOperation, Object... pArgs) {
        this(null, pMBeanName, pOperation, pArgs);
    }

    /**
     * New client request for executing a JMX operation
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pMBeanName    name of the MBean to execute the request on
     * @param pOperation    operation to execute
     * @param pArgs         any arguments to pass (which must match the JMX operation's declared signature)
     */
    public JolokiaExecRequest(JolokiaTargetConfig pTargetConfig, ObjectName pMBeanName, String pOperation, Object... pArgs) {
        super(JolokiaOperation.EXEC, pMBeanName, pTargetConfig);
        operation = pOperation;
        if (pArgs == null) {
            // That's the case when a single, null argument is given (which is the only
            // case that pArgs can be null)
            // TODO: check null argument vs no arguments
            arguments = new ArrayList<>();
            arguments.add(null);
        } else {
            arguments = Arrays.asList(pArgs);
        }
    }

    /**
     * New client request for executing a JMX operation
     *
     * @param pMBeanName name of the MBean to execute the request on
     * @param pOperation operation to execute
     * @param pArgs      any arguments to pass (which must match the JMX operation's declared signature)
     * @throws MalformedObjectNameException if the given name is not an {@link ObjectName}
     */
    public JolokiaExecRequest(String pMBeanName, String pOperation, Object... pArgs)
        throws MalformedObjectNameException {
        this(null, pMBeanName, pOperation, pArgs);
    }

    /**
     * New client request for executing a JMX operation
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pMBeanName    name of the MBean to execute the request on
     * @param pOperation    operation to execute
     * @param pArgs         any arguments to pass (which must match the JMX operation's declared signature)
     * @throws MalformedObjectNameException if the given name is not an {@link ObjectName}
     */
    public JolokiaExecRequest(JolokiaTargetConfig pTargetConfig, String pMBeanName, String pOperation, Object... pArgs)
        throws MalformedObjectNameException {
        this(pTargetConfig, new ObjectName(pMBeanName), pOperation, pArgs);
    }

    /**
     * Name of the operation to execute
     *
     * @return operation name
     */
    public String getOperation() {
        return operation;
    }

    /**
     * List of arguments used for executing
     *
     * @return list of arguments or empty list if no argument are used
     */
    public List<Object> getArguments() {
        return arguments;
    }

    @Override
    @SuppressWarnings("unchecked")
    public JolokiaExecResponse createResponse(JSONObject pResponse) {
        return new JolokiaExecResponse(this, pResponse);
    }

    @Override
    public List<String> getRequestParts() {
        List<String> ret = super.getRequestParts();
        ret.add(operation);
        if (!arguments.isEmpty()) {
            for (Object argument : arguments) {
                ret.add(serializeArgumentToRequestPart(argument));
            }
        }
        // path elements directly after arguments. It's up to the server-side to correctly split arguments
        // and path. See https://github.com/jolokia/jolokia/issues/151
        ret.addAll(EscapeUtil.splitPath(path));
        return ret;
    }

    @Override
    public JSONObject toJson() {
        JSONObject ret = super.toJson();
        ret.put("operation", operation);
        if (!arguments.isEmpty()) {
            JSONArray args = new JSONArray(arguments.size());
            for (Object arg : arguments) {
                args.add(serializeArgumentToJson(arg));
            }
            ret.put("arguments", args);
        }
        if (path != null) {
            ret.put("path", path);
        }
        return ret;
    }

    /**
     * Get the path for extracting parts of the return value.
     *
     * @return path used for extracting
     */
    public String getPath() {
        return path;
    }

    /**
     * Set the path for diving into the return value. Can't be specified with constructor, because operation
     * arguments are passed as varargs.
     *
     * @param pPath path to set
     */
    public void setPath(String pPath) {
        path = pPath;
    }

}
