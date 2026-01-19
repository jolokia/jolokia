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
package org.jolokia.server.core.backend;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.management.AttributeNotFoundException;
import javax.management.JMException;
import javax.management.JMRuntimeException;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.*;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.request.RequestInterceptor;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.core.service.serializer.SerializeOptions;
import org.jolokia.json.JSONObject;

import static org.jolokia.server.core.config.ConfigKey.*;

/**
 * Backend manager is responsible for two clearly distinguished steps and combining them:<ul>
 *     <li>Calling {@link RequestDispatcher} which then selects proper {@link org.jolokia.server.core.service.request.RequestHandler}
 *     based on the type of {@link JolokiaRequest}</li>
 *     <li>Serializing the result using {@link Serializer}.</li>
 * </ul>
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
    private final SerializeOptions.Builder convertOptionsBuilder;

    private final boolean includeRequestGlobal;

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

        if (pJolokiaCtx != null) {
            // limits
            convertOptionsBuilder = new SerializeOptions.Builder(
                getOrDefault(pJolokiaCtx.getConfig(MAX_DEPTH), 0),
                getOrDefault(pJolokiaCtx.getConfig(MAX_COLLECTION_SIZE), 0),
                getOrDefault(pJolokiaCtx.getConfig(MAX_OBJECTS), 0)
            );
            // whether to include the incoming request in the response
            includeRequestGlobal = getOrDefault(pJolokiaCtx.getConfig(INCLUDE_REQUEST), true);
        } else {
            convertOptionsBuilder = new SerializeOptions.Builder();
            includeRequestGlobal = true;
        }
    }

    private int getOrDefault(String pValue, int defaultValue) {
        return pValue != null ? Integer.parseInt(pValue) : defaultValue;
    }

    private boolean getOrDefault(String pValue, boolean defaultValue) {
        return pValue != null ? Boolean.parseBoolean(pValue) : defaultValue;
    }

    /**
     * Handle a single {@link JolokiaRequest}. The (Jolokia, not HTTP) response status is set to 200 if the request
     * was successful and 304 for {@link NotChangedException}. All errors are handled by the caller
     * which should then create an Error JSON response and set proper (Jolokia, not HTTP) status.
     *
     * @param pJmxReq request to perform
     * @return the already converted (serialized to JSON) answer.
     * @throws IOException when there's an error invoking {@link javax.management.MBeanServerConnection} for some commands (which may be remote)
     * @throws JMException JMX checked exception, because most requests are handled by dealing with MBeans
     * @throws JMRuntimeException JMX unchecked exception, because most requests are handled by dealing with MBeans
     * @throws BadRequestException because some commands do more user input parsing in addition to what was checked when the {@link JolokiaRequest} was created
     * @throws EmptyResponseException if the response should not be closed (expecting further async/stream data)
     */
    public JSONObject handleRequest(JolokiaRequest pJmxReq)
            throws IOException, JMException, JMRuntimeException, BadRequestException, EmptyResponseException {
        boolean debug = jolokiaCtx.isDebug();

        long time = 0;
        if (debug) {
             time = System.currentTimeMillis();
        }

        JSONObject json;
        try {
            json = callRequestDispatcher(pJmxReq);
            json.put("status", 200 /* success */);
        } catch (NotChangedException exp) {
            // A handled indicates that its value hasn't changed. We return a status with
            // "304 Not Modified" similar to the HTTP status code (http://en.wikipedia.org/wiki/HTTP_status)
            json = new JSONObject();
            json.put("status", 304 /* no change */);
        }

        // we can choose not to have a "request" field in the response object
        addRequestToResponseIfNeeded(json, pJmxReq);

        intercept(pJmxReq, json);

        if (!json.containsKey("timestamp")) {
            // normally org.jolokia.service.history.HistoryStore.updateAndAdd adds it, but we need this
            // field also when the history service is disabled/unavailable
            json.put("timestamp", System.currentTimeMillis() / 1000);
        }

        if (debug) {
            jolokiaCtx.debug("Execution time: " + (System.currentTimeMillis() - time) + " ms");
            jolokiaCtx.debug("Response: " + json.toJSONString());
        }

        return json;
    }

    /**
     * Call the {@link RequestDispatcher} and serialize the result into a {@link JSONObject} for the response, where
     * the actual result is a {@code value} field of the response {@link JSONObject}.
     *
     * @param pJmxReq
     * @return
     * @throws IOException when there's an error invoking {@link javax.management.MBeanServerConnection} for some commands (which may be remote)
     * @throws JMException JMX checked exception, because most requests are handled by dealing with MBeans
     * @throws JMRuntimeException JMX unchecked exception, because most requests are handled by dealing with MBeans
     * @throws NotChangedException when (according to request parameters) user wants HTTP 304 if nothing has changed
     * @throws BadRequestException because some commands do more user input parsing in addition to what was checked when the {@link JolokiaRequest} was created
     * @throws EmptyResponseException if the response should not be closed (expecting further async/stream data)
     */
    private JSONObject callRequestDispatcher(JolokiaRequest pJmxReq)
            throws IOException, JMException, JMRuntimeException, NotChangedException, BadRequestException, EmptyResponseException {

        // this is where the magic happens. JolokiaRequest is turned into a result Object which depends
        // on the actual RequestHandler - for JMX, it may be for example a result of
        // javax.management.MBeanServerConnection.invoke() call - so actually _any_ type of object.
        // but some request handlers may already return a JSON to be returned without any conversion
        Object result = requestDispatcher.dispatch(pJmxReq);

        // the rest of the magic - serialization of _any_ object into a JSON object/value (even null)

        Serializer serializer = jolokiaCtx.getMandatoryService(Serializer.class);
        List<String> pathParts = pJmxReq.useReturnValueWithPath() ? pJmxReq.getPathParts() : null;
        SerializeOptions options = getSerializeOptions(pJmxReq);

        Object jsonResult = serializer.serialize(result, pathParts, options);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("value", jsonResult);

        return jsonObject;
    }

    /**
     * Find all available services of {@link RequestInterceptor} class and pass already prepared response object
     * to {@link RequestInterceptor#intercept(JolokiaRequest, JSONObject)}. Interceptors are used only
     * after the result is ready. So they don't work like Servlet filters for example.
     *
     * @param pJmxReq
     * @param pRetValue
     */
    private void intercept(JolokiaRequest pJmxReq, JSONObject pRetValue) {
        Set<RequestInterceptor> interceptors = jolokiaCtx.getServices(RequestInterceptor.class);
        for (RequestInterceptor interceptor : interceptors) {
            try {
                interceptor.intercept(pJmxReq, pRetValue);
            } catch (RuntimeException exp) {
                jolokiaCtx.error("Cannot call request interceptor " + interceptor + ": " + exp.getMessage(), exp);
            }
        }
    }

    /**
     * Convert a {@link Throwable} into a {@link JSONObject} so that it can be included in an error response - should
     * be called only if {@code serializeException} parameter is set.
     *
     * @param pExp throwable to convert
     * @param pJmxReq the request from where to take the serialization options
     * @return the exception.
     */
    public Object convertExceptionToJson(Throwable pExp, JolokiaRequest pJmxReq)  {
        SerializeOptions options = getSerializeOptions(pJmxReq);
        try {
            Serializer serializer = jolokiaCtx.getMandatoryService(Serializer.class);
            return serializer.serialize(pExp, null, options);
        } catch (AttributeNotFoundException e) {
            // Cannot happen, since we don't use a path
            return null;
        }
    }

    public void addRequestToResponseIfNeeded(JSONObject json, JolokiaRequest pJmxReq) {
        if (pJmxReq == null) {
            return;
        }

        // g:true (default), r:null - true
        // g:true (default), r:true (default) - true
        // g:true (default), r:false - false
        // g:false, r:null - false
        // g:false, r:true (default) - true
        // g:false, r:false - false
        String includeRequestLocal = pJmxReq.getParameter(INCLUDE_REQUEST);
        if ((includeRequestGlobal && !"false".equals(includeRequestLocal))
            || (!includeRequestGlobal && "true".equals(includeRequestLocal))) {
            json.put("request", pJmxReq.toJSON());
        }
    }

    /**
     * {@link SerializeOptions serialization config} is prepared for each request.
     * @param pJmxReq
     * @return
     */
    private SerializeOptions getSerializeOptions(JolokiaRequest pJmxReq) {
        return convertOptionsBuilder
            .maxDepth(pJmxReq.getParameterAsInt(ConfigKey.MAX_DEPTH))
            .maxCollectionSize(pJmxReq.getParameterAsInt(ConfigKey.MAX_COLLECTION_SIZE))
            .maxObjects(pJmxReq.getParameterAsInt(ConfigKey.MAX_OBJECTS))
            .serializeLong(pJmxReq.getParameter(ConfigKey.SERIALIZE_LONG))
            .faultHandler(pJmxReq.getValueFaultHandler())
            .useAttributeFilter(pJmxReq.getPathParts() != null)
            .build();
    }

}
