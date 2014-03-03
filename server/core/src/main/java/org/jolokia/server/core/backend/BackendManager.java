package org.jolokia.server.core.backend;

import java.io.IOException;
import java.util.Set;

import javax.management.AttributeNotFoundException;
import javax.management.JMException;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.request.NotChangedException;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.request.RequestInterceptor;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.json.simple.JSONObject;

import static org.jolokia.server.core.config.ConfigKey.*;

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


/**
 * Backendmanager for dispatching to various backends based on a given
 * {@link JolokiaRequest}
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class BackendManager {

    // Overall Jolokia context
    private final JolokiaContext jolokiaCtx;

    // Dispatcher for doing the requests
    private final RequestDispatcher requestDispatcher;

    // Hard limits for conversion
    private SerializeOptions.Builder convertOptionsBuilder;

    /**
     * Construct a new backend manager with the given configuration and with the default
     * request dispatcher
     *
     * @param pJolokiaCtx jolokia context for accessing internal services
     */
    public BackendManager(JolokiaContext pJolokiaCtx) {
        this(pJolokiaCtx, new RequestDispatcherImpl(pJolokiaCtx));
    }

    /**
     * Create a backend manager with a custom request dispatcher
     *
     * @param pJolokiaCtx context used as service locator
     * @param pRequestDispatcher the request dispatcher to use.
     */
    BackendManager(JolokiaContext pJolokiaCtx, RequestDispatcher pRequestDispatcher) {
        jolokiaCtx = pJolokiaCtx;
        requestDispatcher = pRequestDispatcher;
        init(pJolokiaCtx);
    }

    /**
     * Handle a single JMXRequest. The response status is set to 200 if the request
     * was successful
     *
     * @param pJmxReq request to perform
     * @return the already converted answer.
     * @throws JMException
     * @throws IOException
     */
    public JSONObject handleRequest(JolokiaRequest pJmxReq) throws JMException, IOException {
        boolean debug = jolokiaCtx.isDebug();

        long time = 0;
        if (debug) {
             time = System.currentTimeMillis();
        }
        JSONObject json;
        try {
            json = callRequestDispatcher(pJmxReq);
            json.put("status",200 /* success */);
        } catch (NotChangedException exp) {
            // A handled indicates that its value hasn't changed. We return an status with
            //"304 Not Modified" similar to the HTTP status code (http://en.wikipedia.org/wiki/HTTP_status)
            json = new JSONObject();
            json.put("request",pJmxReq.toJSON());
            json.put("status",304);
            json.put("timestamp",System.currentTimeMillis() / 1000);
        }

        // Call request logger
        intercept(pJmxReq, json);

        if (debug) {
            jolokiaCtx.debug("Execution time: " + (System.currentTimeMillis() - time) + " ms");
            jolokiaCtx.debug("Response: " + json);
        }

        return json;
    }

    // Provide interceptors a change for wrapping around the request
    private void intercept(JolokiaRequest pJmxReq, JSONObject pRetValue) {
        Set<RequestInterceptor> interceptors = jolokiaCtx.getServices(RequestInterceptor.class);
        for (RequestInterceptor interceptor : interceptors) {
            try {
                interceptor.intercept(pJmxReq, pRetValue);
            } catch (RuntimeException exp) {
                jolokiaCtx.error("Cannot call request logger " + interceptor + ": " + exp,exp);
            }
        }
    }

    /**
     * Convert a Throwable to a JSON object so that it can be included in an error response
     *
     * @param pExp throwable to convert
     * @param pJmxReq the request from where to take the serialization options
     * @return the exception.
     */
    public Object convertExceptionToJson(Throwable pExp, JolokiaRequest pJmxReq)  {
        SerializeOptions opts = getSerializeOptions(pJmxReq);
        try {
            return jolokiaCtx.getMandatoryService(Serializer.class).serialize(pExp, null, opts);
        } catch (AttributeNotFoundException e) {
            // Cannot happen, since we dont use a path
            return null;
        }
    }

    /**
     * Check whether remote access from the given client is allowed.
     *
     * @param pRemoteHost remote host to check against
     * @param pRemoteAddr alternative IP address
     * @return true if remote access is allowed
     */
    public boolean isRemoteAccessAllowed(String pRemoteHost, String pRemoteAddr) {
        return jolokiaCtx.isRemoteAccessAllowed(pRemoteHost, pRemoteAddr);
    }

    // ==========================================================================================================

    private void init(JolokiaContext pCtx) {
        // Init limits
        if (pCtx != null) {
            convertOptionsBuilder = new SerializeOptions.Builder(
                    getNullSaveIntLimit(pCtx.getConfig(MAX_DEPTH)),
                    getNullSaveIntLimit(pCtx.getConfig(MAX_COLLECTION_SIZE)),
                    getNullSaveIntLimit(pCtx.getConfig(MAX_OBJECTS))
            );
        } else {
            convertOptionsBuilder = new SerializeOptions.Builder();
        }
    }

    private int getNullSaveIntLimit(String pValue) {
        return pValue != null ? Integer.parseInt(pValue) : 0;
    }

    // call the an appropriate request dispatcher
    private JSONObject callRequestDispatcher(JolokiaRequest pJmxReq)
            throws JMException, IOException, NotChangedException {
        Object result = requestDispatcher.dispatch(pJmxReq);

        SerializeOptions opts = getSerializeOptions(pJmxReq);

        Object jsonResult =
                jolokiaCtx.getMandatoryService(Serializer.class).serialize(
                        result,
                        pJmxReq.useReturnValueWithPath() ? pJmxReq.getPathParts() : null,
                        opts);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("value",jsonResult);
        jsonObject.put("request",pJmxReq.toJSON());
        return jsonObject;
    }

    private SerializeOptions getSerializeOptions(JolokiaRequest pJmxReq) {
        return convertOptionsBuilder.
                    maxDepth(pJmxReq.getParameterAsInt(ConfigKey.MAX_DEPTH)).
                    maxCollectionSize(pJmxReq.getParameterAsInt(ConfigKey.MAX_COLLECTION_SIZE)).
                    maxObjects(pJmxReq.getParameterAsInt(ConfigKey.MAX_OBJECTS)).
                    faultHandler(pJmxReq.getValueFaultHandler()).
                    build();
    }
}
