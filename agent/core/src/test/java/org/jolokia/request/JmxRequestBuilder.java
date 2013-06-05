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

import java.util.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.config.*;
import org.jolokia.util.EscapeUtil;
import org.jolokia.util.RequestType;
import org.json.simple.JSONObject;

/**
 * Helper class for unit testing
 *
 * @author roland
 * @since Mar 6, 2010
 */
public class JmxRequestBuilder {

    private JSONObject request = new JSONObject();

    private Map<String,String> procConfig = new HashMap<String,String>();

    public JmxRequestBuilder(RequestType pType) throws MalformedObjectNameException {
        this(pType,(String) null);
    }

    public JmxRequestBuilder(RequestType pType, String pObjectName) throws MalformedObjectNameException {
        request.put("type",pType.getName());
        if (pObjectName != null) {
            request.put("mbean", pObjectName);
        }
    }

    public JmxRequestBuilder(RequestType pType, ObjectName pMBean) throws MalformedObjectNameException {
        this(pType,pMBean.getCanonicalName());
    }

    public <R extends JmxRequest> R build() throws MalformedObjectNameException {
        RequestType type = RequestType.getTypeByName((String) request.get("type"));
        ProcessingParameters params = new Configuration().getProcessingParameters(procConfig);
        switch (type) {
            case READ: return (R) new JmxReadRequest(request,params);
            case WRITE: return (R) new JmxWriteRequest(request,params);
            case EXEC: return (R) new JmxExecRequest(request,params);
            case VERSION: return (R) new JmxVersionRequest(request,params);
            case SEARCH: return (R) new JmxSearchRequest(request,params);
            case LIST: return (R) new JmxListRequest(request,params);
        }
        throw new IllegalArgumentException("Unknown type " + type);
    }

    public JmxRequestBuilder attribute(String pAttribute) {
        request.put("attribute",pAttribute);
        return this;
    }

    public JmxRequestBuilder attributes(List<String> pAttributeNames) {
        request.put("attribute", pAttributeNames);
        return this;
    }

    public JmxRequestBuilder attributes(String ... pAttributeNames) {
        request.put("attribute", Arrays.asList(pAttributeNames));
        return this;
    }

    public JmxRequestBuilder operation(String pOperation) {
        request.put("operation", pOperation);
        return this;
    }

    public JmxRequestBuilder value(Object pValue) {
        request.put("value", pValue);
        return this;
    }

    public JmxRequestBuilder pathParts(String... pExtraArgs) {
        request.put("path", EscapeUtil.combineToPath(Arrays.asList(pExtraArgs)));
        return this;
    }

    public JmxRequestBuilder path(String pPath) {
        request.put("path",pPath);
        return this;
    }

    public JmxRequestBuilder arguments(List<Object> pArguments) {
        request.put("arguments", pArguments);
        return this;
    }

    public JmxRequestBuilder arguments(Object ... pArguments) {
        request.put("arguments", Arrays.asList(pArguments));
        return this;
    }

    public JmxRequestBuilder option(ConfigKey pKey, String pValue) {
        assert pKey.isRequestConfig();
        procConfig.put(pKey.getKeyValue(),pValue);
        return this;
    }

    static Map<String,Object> createMap(Object ... args) {
        Map<String,Object> ret = new HashMap<String, Object>();
        for (int i = 0; i<args.length; i+=2) {
            ret.put((String) args[i],args[i+1]);
        }
        return ret;
    }

}
