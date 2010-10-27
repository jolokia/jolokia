/*
 * Copyright 2009-2010 Roland Huss
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

package org.jolokia.proxy;

import org.jolokia.JmxRequest;
import org.jolokia.backend.RequestDispatcher;
import org.jolokia.config.Restrictor;
import org.jolokia.converter.StringToObjectConverter;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.handler.JsonRequestHandler;
import org.jolokia.handler.RequestHandlerManager;
import org.json.simple.JSONObject;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.naming.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Dispatcher for Jolokia requests. This is useful if the agent is used in
 * a JavaScript library to cicumvent the 'same origin' policy.
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class SimpleRequestDispatcher implements RequestDispatcher {

    private RequestHandlerManager requestHandlerManager;

    public SimpleRequestDispatcher(ObjectToJsonConverter objectToJsonConverter,
                                   StringToObjectConverter stringToObjectConverter,
                                   Restrictor restrictor) {
        // We only push the request object directly to the target
    }

    /**
     * Call a remote connector based on the connection information contained in
     * the request.
     *
     *
     * @param pJmxReq the request to dispatch
     * @return result object
     * @throws javax.management.InstanceNotFoundException
     * @throws javax.management.AttributeNotFoundException
     * @throws javax.management.ReflectionException
     * @throws javax.management.MBeanException
     * @throws java.io.IOException
     */
    public JSONObject dispatchRequest(JmxRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {

        URL targetUrl = new URL(pJmxReq.getTargetConfigUrl());
        URLConnection connection = targetUrl.openConnection();
        if (connection instanceof HttpURLConnection) {
            HttpURLConnection httpConnection = (HttpURLConnection) connection;
            httpConnection.setDoOutput(true);
            httpConnection.setRequestMethod("POST");

            // Write request as JSON to the target server
            OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());

            out.close();

            // Read HttpResponse
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuffer buf = new StringBuffer();
            String line;
            while ((line = in.readLine()) != null) {
                buf.append(line);
            }
            in.close();

            // Create JSON response

            // TODO: The dispatcher itself should decided, whether an Object is returned or the JSON answer directly.
            return null;
        } else {
            throw new IllegalArgumentException(targetUrl + " is not a HTTP(S) Url");
        }
    }

    public boolean canHandle(JmxRequest pJmxRequest) {
        String targetUrl = pJmxRequest.getTargetConfigUrl();
        return targetUrl != null && targetUrl.startsWith("http");
    }

    public boolean useReturnValueWithPath(JmxRequest pJmxRequest) {
        JsonRequestHandler handler = requestHandlerManager.getRequestHandler(pJmxRequest.getType());
        return handler.useReturnValueWithPath();
    }
}
