/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.server.core.vm;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.jolokia.json.JSONObject;
import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.request.BaseRequestHandler;
import org.jolokia.server.core.request.EmptyResponseException;
import org.jolokia.server.core.request.JolokiaExecRequest;
import org.jolokia.server.core.request.JolokiaListRequest;
import org.jolokia.server.core.request.JolokiaReadRequest;
import org.jolokia.server.core.request.JolokiaRequestFactory;
import org.jolokia.server.core.request.JolokiaWriteRequest;
import org.jolokia.server.core.request.ProcessingParameters;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.RequestType;

/**
 * Request handler that's designed to be used internally within a JVM using direct methods
 * that map to Jolokia protocol operations ({@link RequestType#READ}, {@link RequestType#WRITE},
 * {@link RequestType#LIST}, {@link RequestType#SEARCH}, {@link RequestType#EXEC}).
 */
public class InVmRequestHandler extends BaseRequestHandler {

    public InVmRequestHandler(JolokiaContext context) {
        super(context);
    }

    public JSONObject handleList(String mbean) throws EmptyResponseException, BadRequestException {
        JolokiaListRequest list = JolokiaRequestFactory.createGetRequest(
            RequestType.LIST.getName() + "/" + toPath(mbean),
            new ProcessingParameters(new HashMap<>()));
        return executeRequest(list);
    }

    private String toPath(String mbean) {
        if (mbean == null || mbean.isEmpty()) {
            return "";
        }
        return mbean.replaceAll("!", "!!").replaceAll("/", "!/").replaceFirst(":", "/");
    }

    public JSONObject handleRead(String mbean, String attribute) throws EmptyResponseException, BadRequestException {
        JolokiaReadRequest read = JolokiaRequestFactory.createGetRequest(
            RequestType.READ.getName() + "/" + mbean + "/" + attribute,
            new ProcessingParameters(new HashMap<>()));
        return executeRequest(read);
    }

    public JSONObject handleWrite(String mbean, String attribute, Object value) throws EmptyResponseException, BadRequestException {
        JolokiaWriteRequest write = JolokiaRequestFactory.createGetRequest(
            // TODO: handle object value
            RequestType.WRITE.getName() + "/" + mbean + "/" + attribute + "/" + value,
            new ProcessingParameters(new HashMap<>()));
        return executeRequest(write);
    }

    public JSONObject handleExec(String mbean, String operation, Object... args) throws EmptyResponseException, BadRequestException {
        String path = RequestType.EXEC.getName() + "/" + mbean + "/" + operation;
        if (args != null && args.length > 0) {
            path += "/" + Arrays.stream(args).map(Object::toString).collect(Collectors.joining("/"));
        }
        JolokiaExecRequest exec = JolokiaRequestFactory.createGetRequest(
            path,
            new ProcessingParameters(new HashMap<>()));
        return executeRequest(exec);
    }

}
