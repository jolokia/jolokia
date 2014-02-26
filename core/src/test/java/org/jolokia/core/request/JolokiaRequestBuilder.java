package org.jolokia.core.request;

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

import org.jolokia.core.config.*;
import org.jolokia.core.request.notification.NotificationCommandFactory;
import org.jolokia.core.request.notification.NotificationCommandType;
import org.jolokia.core.util.EscapeUtil;
import org.jolokia.core.util.RequestType;
import org.json.simple.JSONObject;

/**
 * Helper class for unit testing
 *
 * @author roland
 * @since Mar 6, 2010
 */
public class JolokiaRequestBuilder {

    private JSONObject request = new JSONObject();

    private Map<ConfigKey,String> procConfig = new HashMap<ConfigKey,String>();

    public JolokiaRequestBuilder(RequestType pType) throws MalformedObjectNameException {
        this(pType,(String) null);
    }

    public JolokiaRequestBuilder(RequestType pType, String pObjectName) throws MalformedObjectNameException {
        request.put("type",pType.getName());
        if (pObjectName != null) {
            request.put("mbean", pObjectName);
        }
    }

    public JolokiaRequestBuilder(RequestType pType, ObjectName pMBean) throws MalformedObjectNameException {
        this(pType,pMBean.getCanonicalName());
    }

    public <R extends JolokiaRequest> R build() throws MalformedObjectNameException {
        RequestType type = RequestType.getTypeByName((String) request.get("type"));
        ProcessingParameters params = new TestProcessingParameters(procConfig);
        switch (type) {
            case READ: return (R) new JolokiaReadRequest(request,params);
            case WRITE: return (R) new JolokiaWriteRequest(request,params);
            case EXEC: return (R) new JolokiaExecRequest(request,params);
            case VERSION: return (R) new JolokiaVersionRequest(request,params);
            case SEARCH: return (R) new JolokiaSearchRequest(request,params);
            case LIST: return (R) new JolokiaListRequest(request,params);
            case NOTIFICATION: return (R) new JolokiaNotificationRequest(NotificationCommandFactory.createCommand(request),
                                                                     params);
        }
        throw new IllegalArgumentException("Unknown type " + type);
    }

    public JolokiaRequestBuilder attribute(String pAttribute) {
        request.put("attribute",pAttribute);
        return this;
    }

    public JolokiaRequestBuilder attributes(List<String> pAttributeNames) {
        request.put("attribute", pAttributeNames);
        return this;
    }

    public JolokiaRequestBuilder attributes(String ... pAttributeNames) {
        request.put("attribute", Arrays.asList(pAttributeNames));
        return this;
    }

    public JolokiaRequestBuilder operation(String pOperation) {
        request.put("operation", pOperation);
        return this;
    }

    public JolokiaRequestBuilder value(Object pValue) {
        request.put("value", pValue);
        return this;
    }

    public JolokiaRequestBuilder pathParts(String... pExtraArgs) {
        request.put("path", EscapeUtil.combineToPath(Arrays.asList(pExtraArgs)));
        return this;
    }

    public JolokiaRequestBuilder path(String pPath) {
        request.put("path",pPath);
        return this;
    }

    public JolokiaRequestBuilder arguments(List<Object> pArguments) {
        request.put("arguments", pArguments);
        return this;
    }

    public JolokiaRequestBuilder arguments(Object ... pArguments) {
        request.put("arguments", Arrays.asList(pArguments));
        return this;
    }

    public JolokiaRequestBuilder command(NotificationCommandType pType) {
        request.put("command",pType.getType());
        return this;
    }

    public JolokiaRequestBuilder client(String client) {
        request.put("client",client);
        return this;
    }

    public JolokiaRequestBuilder handle(String handle) {
        request.put("handle",handle);
        return this;
    }

    public JolokiaRequestBuilder mode(String mode) {
        request.put("mode",mode);
        return this;
    }

    public JolokiaRequestBuilder handback(String handback) {
        request.put("handback",handback);
        return this;
    }

    public JolokiaRequestBuilder filter(String ...filter) {
        request.put("filter", Arrays.asList(filter));
        return this;
    }

    public JolokiaRequestBuilder option(ConfigKey pKey, String pValue) {
        assert pKey.isRequestConfig();
        procConfig.put(pKey,pValue);
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
