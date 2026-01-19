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
package org.jolokia.server.core.request;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.JMRuntimeException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import javax.management.RuntimeErrorException;
import javax.management.RuntimeMBeanException;
import javax.management.RuntimeOperationsException;

import org.jolokia.core.util.ErrorUtil;
import org.jolokia.json.JSONObject;
import org.jolokia.server.core.backend.BackendManager;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.MimeTypeUtil;

/**
 * Base request handlers to be used by specific handlers that rely on specific technology (web, in-vm, ...)
 */
public abstract class BaseRequestHandler {

    // handler for contacting the MBean server(s)
    protected final BackendManager backendManager;

    // Overall context
    protected final JolokiaContext jolokiaCtx;

    protected final boolean includeRequestGlobal;

    public BaseRequestHandler(JolokiaContext context) {
        jolokiaCtx = context;
        backendManager = new BackendManager(context);
        includeRequestGlobal = jolokiaCtx.getConfig(ConfigKey.INCLUDE_REQUEST) == null
            || Boolean.parseBoolean(jolokiaCtx.getConfig(ConfigKey.INCLUDE_REQUEST));
    }

    /**
     * Get processing parameters from a string-string map
     *
     * @param pParameterMap params to extra. A parameter {@link ConfigKey#PATH_QUERY_PARAM} is used as extra path info
     * @return the processing parameters
     */
    public ProcessingParameters getProcessingParameter(Map<String, String[]> pParameterMap) throws BadRequestException {
        Map<ConfigKey, String> config = new HashMap<>();
        if (pParameterMap != null) {
            extractRequestParameters(config, pParameterMap);
            validateRequestParameters(config);
            extractDefaultRequestParameters(config);
        }
        return new ProcessingParameters(config);
    }

    /**
     * Extract configuration parameters from the given HTTP request parameters
     */
    protected void extractRequestParameters(Map<ConfigKey, String> pConfig, Map<String, String[]> pParameterMap) {
        for (Map.Entry<String, String[]> entry : pParameterMap.entrySet()) {
            String[] values = entry.getValue();
            if (values != null && values.length > 0) {
                ConfigKey cKey = ConfigKey.getRequestConfigKey(entry.getKey());
                if (cKey != null) {
                    Object value = values[0];
                    pConfig.put(cKey, value != null ? value.toString() : null);
                }
            }
        }
    }

    /**
     * Validation of parameters. Should be called for provided parameter values. Not necessary for built-in/default
     * values.
     *
     * @param config
     */
    protected void validateRequestParameters(Map<ConfigKey, String> config) throws BadRequestException {
        // parameters that may be passed with HTTP request:
        //  + callback
        //  + canonicalNaming
        //  + ifModifiedSince
        //  + ignoreErrors (validated in org.jolokia.server.core.request.JolokiaRequest.initParameters())
        //  + includeRequest
        //  + includeStackTrace
        //  + listCache
        //  + listKeys
        //  + maxCollectionSize
        //  + maxDepth
        //  + maxObjects
        //  + mimeType
        //  + p
        //  + serializeException
        //  + serializeLong
        for (Map.Entry<ConfigKey, String> e : config.entrySet()) {
            ConfigKey key = e.getKey();
            String value = e.getValue();
            Class<?> type = key.getType();

            if (type == null) {
                continue;
            }

            if (type == Boolean.class) {
                String v = value.trim().toLowerCase();
                if (!(ConfigKey.enabledValues.contains(v) || ConfigKey.disabledValues.contains(v))) {
                    throw new BadRequestException("Invalid value of " + key.getKeyValue() + " parameter");
                }
            } else if (type == Integer.class) {
                String v = value.trim();
                try {
                    Integer.parseInt(v);
                } catch (NumberFormatException ex) {
                    throw new BadRequestException("Invalid value of " + key.getKeyValue() + " parameter");
                }
            } else if (type == String.class) {
                // validate selected keys
                if (key == ConfigKey.INCLUDE_STACKTRACE) {
                    String v = value.trim().toLowerCase();
                    if (!(ConfigKey.enabledValues.contains(v) || ConfigKey.disabledValues.contains(v)
                        || v.equals("runtime"))) {
                        throw new BadRequestException("Invalid value of " + ConfigKey.INCLUDE_STACKTRACE.getKeyValue() + " parameter");
                    }
                } else if (key == ConfigKey.SERIALIZE_LONG) {
                    String v = value.trim().toLowerCase();
                    if (!("number".equals(v) || "string".equals(v))) {
                        throw new BadRequestException("Invalid value of " + ConfigKey.SERIALIZE_LONG.getKeyValue() + " parameter");
                    }
                } else if (key == ConfigKey.MIME_TYPE) {
                    String v = value.trim().toLowerCase();
                    boolean ok = false;
                    for (String accepted : MimeTypeUtil.ACCEPTED_MIME_TYPES) {
                        if (accepted.equals(v)) {
                            ok = true;
                            break;
                        }
                    }
                    if (!ok) {
                        throw new BadRequestException("Invalid value of " + ConfigKey.MIME_TYPE.getKeyValue() + " parameter");
                    }
                }
            }
        }
    }

    // Add from the global configuration all request relevant parameters which have not
    // already been set in the given map
    protected void extractDefaultRequestParameters(Map<ConfigKey, String> pConfig) {
        Set<ConfigKey> globalRequestConfigKeys = jolokiaCtx.getConfigKeys();
        for (ConfigKey key : globalRequestConfigKeys) {
            if (key.isRequestConfig() && !pConfig.containsKey(key)) {
                pConfig.put(key, jolokiaCtx.getConfig(key));
            }
        }
    }

    /**
     * <p>Execute a single {@link JolokiaRequest} using {@link BackendManager} and handle all possible errors
     * occurred when doing Jolokia work (mostly - accessing MBeans). The result (whether successful or
     * an error one) is returned as {@link JSONObject} - serialization is already performed by the
     * {@link BackendManager} after invoking the {@link org.jolokia.server.core.backend.RequestDispatcher}.</p>
     *
     * <p>Still no HTTP response handling here, but the error JSON messages may contain {@code status}
     * codes matching the HTTP errors. Note - this error JSON message will be sent to the HTTP client
     * with HTTP 200 status code since they are supposed <em>Jolokia</em> specific errors above the
     * transport layer.</p>
     *
     * <p>This method already handles a lot of exceptions, but when the actual agent (JVM, Servlet) catches any
     * other exception before/after calling this method, there's special {@link #handleThrowable(Throwable)}
     * method to prepare more serious error message.</p>
     *
     * <p>The return value is always {@link JSONObject}, because bulk requests are handled by multiple calls
     * to this method. Full bulk response is a {@link org.jolokia.json.JSONArray}, which contains
     * {@link JSONObject objects} which could be successful or error Jolokia responses.</p>
     *
     * @param pJmxReq the request to execute
     * @return the JSON representation of the answer (successful or error JSON object).
     * @throws BadRequestException propagated to be handled by the caller - when there's another caller-related error
     * not discovered when constructing {@link JolokiaRequest}
     * @throws EmptyResponseException propagated when the Jolokia request should <em>not</em> end with closed connection
     * (so we can send more data asynchronously)
     */
    protected JSONObject executeRequest(JolokiaRequest pJmxReq) throws BadRequestException, EmptyResponseException {
        try {
            // Call handler and retrieve return value, we go through:
            // -1a. org.jolokia.server.core.http.AgentServlet.doHandle
            // -1b. org.jolokia.jvmagent.handler.JolokiaHttpHandler.doHandle
            // 0. org.jolokia.server.core.http.HttpRequestHandler
            // 1. org.jolokia.server.core.backend.BackendManager.handleRequest
            // 2. org.jolokia.server.core.backend.RequestDispatcherImpl.dispatch
            //     - JSON serialization is performed on the object returned by the dispatcher, so there's no
            //       (de)serialization further down the stack
            // 3. org.jolokia.server.core.service.request.RequestHandler.handleRequest()
            //     - org.jolokia.service.jmx.LocalRequestHandler (main)
            //     - org.jolokia.service.jsr160.Jsr160RequestHandler (if there's "target" in the request)
            //     - org.jolokia.support.spring.backend.SpringRequestHandler
            //        - handles READ and LIST on the application context
            //     - org.jolokia.server.core.service.impl.VersionRequestHandler
            //     - org.jolokia.server.core.service.impl.ConfigRequestHandler
            // 4a. org.jolokia.service.jmx.api.CommandHandler.handleAllServerRequest (local and jsr160)
            //     - READ for a patter or if more (all) attributes are fetched
            //     - EXEC for a pattern
            //     - LIST
            //     - NOTIFICATION
            //     - SEARCH
            // 4b. org.jolokia.service.jmx.api.CommandHandler.handleSingleServerRequest (local and jsr160)
            //     - other commands
            //
            // And even further, org.jolokia.server.core.util.jmx.MBeanServerAccess is obtained from JolokiaContext
            // by jmx.LocalRequestHandler (jsr160.LocalRequestHandler uses own SingleMBeanServerAccess) and
            // is used by org.jolokia.service.jmx.api.CommandHandler
            try {
                return backendManager.handleRequest(pJmxReq);
            } catch (ReflectionException | RuntimeOperationsException | RuntimeMBeanException | RuntimeErrorException | MBeanException e) {
                // All JMX exceptions that wrap some target/real exception - according to JMX practice
                Throwable cause = e.getCause();
                if (cause == null) {
                    // strange, but let's handle it like that
                    throw e;
                }
                // throw, so we handle in a single catch chain
                throw cause;
            }
        } catch (InstanceNotFoundException | AttributeNotFoundException e) {
            // Jolokia special - these are 404 to represent "everything seems fine, but the target MBean
            // or its attribute is not available"
            return getErrorJSON(404, e, pJmxReq);
        } catch (SecurityException e) {
            // Should rather not be thrown unless under a SecurityManager or in some JAAS setups,
            // but we mark it as 403 and wipe out the stacktrace
            return getErrorJSON(403, new Exception(e.getMessage()), pJmxReq);
        } catch (JMException | JMRuntimeException e) {
            // all remaining JMX exceptions not handled earlier
            return getErrorJSON(500, e, pJmxReq);
        } catch (BadRequestException | EmptyResponseException e) {
            // BadRequestException usually thrown when parsing ObjectNames from URI paths
            // rethrow to be handled by the caller
            throw e;
        } catch (Throwable e) {
            // Catch-All - this includes exceptions like IllegalStateException, when for example we
            // can't find a request handler to process the JolokiaRequest. Or UnsupportedOperationException
            // when there's no command handler found.
            // This is definitely a server error.
            return getErrorJSON(500, e, pJmxReq);
        }
    }

    /**
     * <p>Utility method for handling single runtime exceptions and errors. This method is called
     * in addition to and after {@link #executeRequest(JolokiaRequest)} to catch additional errors.
     * They are two different methods because of bulk requests, where each individual request can
     * lead to an error. So, each individual request is wrapped with the error handling of
     * {@link #executeRequest(JolokiaRequest)}
     * whereas the overall handling is wrapped with this method. It is hence more coarse grained,
     * leading typically to a status code of 500. We also don't have the incoming {@link JolokiaRequest}
     * object because it may have not been constructed.</p>
     *
     * <p> Summary: This method should be used as last security belt if some exception should escape
     * from a single request processing in {@link #executeRequest(JolokiaRequest)}.</p>
     *
     * @param throwable exception to handle
     * @return its JSON representation
     */
    public JSONObject handleThrowable(Throwable throwable) {
        // no 400 error here, because we parsed all input data in org.jolokia.server.core.http.HttpRequestHandler
        if (throwable instanceof SecurityException) {
            // wipe out stacktrace
            return getErrorJSON(403, new Exception(throwable.getMessage()), null);
        } else {
            // catch-all
            return getErrorJSON(500, throwable, null);
        }
    }

    /**
     * Get the JSON representation for an exception.
     *
     * @param pErrorCode the HTTP error code to return
     * @param pExp       the exception or error occurred
     * @param pJmxReq    request from where to get processing options
     * @return the json representation
     */
    protected JSONObject getErrorJSON(int pErrorCode, Throwable pExp, JolokiaRequest pJmxReq) {
        if (jolokiaCtx.isDebug()) {
            jolokiaCtx.error("Error " + pErrorCode, pExp);
        }

        JSONObject jsonObject = new JSONObject();

        ErrorUtil.addBasicErrorResponseInformation(jsonObject, pExp);

        // Jolokia status - not HTTP status
        jsonObject.put("status", pErrorCode);

        // More error details according to configuration
        if (Boolean.parseBoolean(jolokiaCtx.getConfig(ConfigKey.ALLOW_ERROR_DETAILS))) {
            String includeStackTrace = pJmxReq != null ? pJmxReq.getParameter(ConfigKey.INCLUDE_STACKTRACE) : "false";
            if ("true".equalsIgnoreCase(includeStackTrace) || ("runtime".equalsIgnoreCase(includeStackTrace) && pExp instanceof RuntimeException)) {
                StringWriter writer = new StringWriter();
                pExp.printStackTrace(new PrintWriter(writer));
                jsonObject.put("stacktrace", writer.toString());
            }
            if (pJmxReq != null && pJmxReq.getParameterAsBool(ConfigKey.SERIALIZE_EXCEPTION)) {
                jsonObject.put("error_value", backendManager.convertExceptionToJson(pExp, pJmxReq));
            }
        }

        // Incoming request according to configuration
        backendManager.addRequestToResponseIfNeeded(jsonObject, pJmxReq);

        return jsonObject;
    }

}
