package org.jolokia.client.request;

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
import javax.management.ObjectName;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * A execute request for executing a JMX operation
 *
 * @author roland
 * @since May 18, 2010
 */
public class J4pExecRequest extends AbtractJ4pMBeanRequest {

    // Operation to execute
    private String operation;

    // Operation arguments
    private List<Object> arguments;

    /**
     * New client request for executing a JMX operation
     *
     * @param pMBeanName name of the MBean to execute the request on
     * @param pOperation operation to execute
     * @param pArgs any arguments to pass (which must match the JMX operation's declared signature)
     */
    public J4pExecRequest(ObjectName pMBeanName,String pOperation,Object ... pArgs) {
        this(null,pMBeanName,pOperation,pArgs);
    }

    /**
     * New client request for executing a JMX operation
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pMBeanName name of the MBean to execute the request on
     * @param pOperation operation to execute
     * @param pArgs any arguments to pass (which must match the JMX operation's declared signature)
     */
    public J4pExecRequest(J4pTargetConfig pTargetConfig,ObjectName pMBeanName,String pOperation,Object ... pArgs) {
        super(J4pType.EXEC, pMBeanName, pTargetConfig);
        operation = pOperation;
        if (pArgs == null) {
            // That's the case when a single, null argument is given (which is the only
            // case that pArgs can be null)
            arguments = new ArrayList<Object>();
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
     * @param pArgs any arguments to pass (which must match the JMX operation's declared signature)
     *
     * @throws MalformedObjectNameException if the given name is not an {@link ObjectName}
     */
    public J4pExecRequest(String pMBeanName, String pOperation,Object ... pArgs)
            throws MalformedObjectNameException {
        this(null,pMBeanName,pOperation,pArgs);
    }

    /**
     * New client request for executing a JMX operation
     *
     * @param pTargetConfig proxy target configuration or <code>null</code> if no proxy should be used
     * @param pMBeanName name of the MBean to execute the request on
     * @param pOperation operation to execute
     * @param pArgs any arguments to pass (which must match the JMX operation's declared signature)
     *
     * @throws MalformedObjectNameException if the given name is not an {@link ObjectName}
     */
    public J4pExecRequest(J4pTargetConfig pTargetConfig,String pMBeanName, String pOperation,Object ... pArgs)
            throws MalformedObjectNameException {
        this(pTargetConfig,new ObjectName(pMBeanName),pOperation,pArgs);
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

    /** {@inheritDoc} */
    @Override
    J4pExecResponse createResponse(JSONObject pResponse) {
        return new J4pExecResponse(this,pResponse);
    }

    /** {@inheritDoc} */
    @Override
    List<String> getRequestParts() {
        List<String> ret = super.getRequestParts();
        ret.add(operation);
        if (arguments.size() > 0) {
            for (int i = 0; i < arguments.size(); i++) {
                ret.add(serializeArgumentToRequestPart(arguments.get(i)));
            }
        }
        return ret;
    }

    /** {@inheritDoc} */
    @Override
    JSONObject toJson() {
        JSONObject ret = super.toJson();
        ret.put("operation",operation);
        if (arguments.size() > 0) {
            JSONArray args = new JSONArray();
            for (Object arg : arguments) {
                args.add(serializeArgumentToJson(arg));
            }
            ret.put("arguments",args);
        }
        return ret;
    }
}
