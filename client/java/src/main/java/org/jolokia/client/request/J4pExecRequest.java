package org.jolokia.client.request;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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

    public J4pExecRequest(ObjectName pMBeanName,String pOperation,Object ... pArgs) {
        super(J4pType.EXEC, pMBeanName);
        operation = pOperation;
        arguments = Arrays.asList(pArgs);
    }

    public J4pExecRequest(String pMBeanName, String pOperation,Object ... pArgs)
            throws MalformedObjectNameException {
        this(new ObjectName(pMBeanName),pOperation,pArgs);
    }

    public String getOperation() {
        return operation;
    }

    public List<Object> getArguments() {
        return arguments;
    }

    @Override
    J4pExecResponse createResponse(JSONObject pResponse) {
        return new J4pExecResponse(this,pResponse);
    }

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
