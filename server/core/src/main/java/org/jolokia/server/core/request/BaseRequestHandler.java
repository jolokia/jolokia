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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import javax.management.RuntimeMBeanException;

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
     * Utility method for handling single runtime exceptions and errors. This method is called
     * in addition to and after {@link #executeRequest(JolokiaRequest)} to catch additional errors.
     * They are two different methods because of bulk requests, where each individual request can
     * lead to an error. So, each individual request is wrapped with the error handling of
     * {@link #executeRequest(JolokiaRequest)}
     * whereas the overall handling is wrapped with this method. It is hence more coarse grained,
     * leading typically to a status code of 500.
     * <p>
     * Summary: This method should be used as last security belt is some exception should escape
     * from a single request processing in {@link #executeRequest(JolokiaRequest)}.
     *
     * @param pThrowable exception to handle
     * @return its JSON representation
     */
    public JSONObject handleThrowable(Throwable pThrowable) {
        if (pThrowable instanceof IllegalArgumentException) {
            return getErrorJSON(400, pThrowable, null);
        } else if (pThrowable instanceof SecurityException) {
            // Wipe out stacktrace
            return getErrorJSON(403, new Exception(pThrowable.getMessage()), null);
        } else {
            return getErrorJSON(500, pThrowable, null);
        }
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
     * Execute a single {@link JolokiaRequest}. If a checked  exception occurs,
     * this gets translated into the appropriate JSON object which will get returned.
     * Note, that these exceptions gets *not* translated into an HTTP error, since they are
     * supposed <em>Jolokia</em> specific errors above the transport layer.
     *
     * @param pJmxReq the request to execute
     * @return the JSON representation of the answer.
     */
    protected JSONObject executeRequest(JolokiaRequest pJmxReq) throws EmptyResponseException {
        // Call handler and retrieve return value
        try {
            return backendManager.handleRequest(pJmxReq);
        } catch (ReflectionException | InstanceNotFoundException | AttributeNotFoundException e) {
            return getErrorJSON(404, e, pJmxReq);
        } catch (MBeanException e) {
            return getErrorJSON(500, e.getTargetException(), pJmxReq);
        } catch (UnsupportedOperationException | JMException | IOException e) {
            return getErrorJSON(500, e, pJmxReq);
        } catch (IllegalArgumentException e) {
            return getErrorJSON(400, e, pJmxReq);
        } catch (SecurityException e) {
            // Wipe out stacktrace
            return getErrorJSON(403, new Exception(e.getMessage()), pJmxReq);
        } catch (RuntimeMBeanException e) {
            // Use wrapped exception
            return errorForUnwrappedException(e, pJmxReq);
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
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("status", pErrorCode);
        jsonObject.put("error", getExceptionMessage(pExp));
        jsonObject.put("error_type", pExp.getClass().getName());
        addErrorInfo(jsonObject, pExp, pJmxReq);
        if (jolokiaCtx.isDebug()) {
            jolokiaCtx.error("Error " + pErrorCode, pExp);
        }
        if (pJmxReq != null) {
            String includeRequestLocal = pJmxReq.getParameter(ConfigKey.INCLUDE_REQUEST);
            if ((includeRequestGlobal && !"false".equals(includeRequestLocal))
                || (!includeRequestGlobal && "true".equals(includeRequestLocal))) {
                jsonObject.put("request", pJmxReq.toJSON());
            }
        }
        return jsonObject;
    }

    protected void addErrorInfo(JSONObject pErrorResp, Throwable pExp, JolokiaRequest pJmxReq) {
        if (Boolean.parseBoolean(jolokiaCtx.getConfig(ConfigKey.ALLOW_ERROR_DETAILS))) {
            String includeStackTrace = pJmxReq != null ?
                pJmxReq.getParameter(ConfigKey.INCLUDE_STACKTRACE) : "false";
            if (includeStackTrace.equalsIgnoreCase("true") ||
                (includeStackTrace.equalsIgnoreCase("runtime") && pExp instanceof RuntimeException)) {
                StringWriter writer = new StringWriter();
                pExp.printStackTrace(new PrintWriter(writer));
                pErrorResp.put("stacktrace", writer.toString());
            }
            if (pJmxReq != null && pJmxReq.getParameterAsBool(ConfigKey.SERIALIZE_EXCEPTION)) {
                pErrorResp.put("error_value", backendManager.convertExceptionToJson(pExp, pJmxReq));
            }
        }
    }

    // Unwrap an exception to get to the 'real' exception
    // and extract the error code accordingly
    protected JSONObject errorForUnwrappedException(Exception e, JolokiaRequest pJmxReq) {
        Throwable cause = e.getCause();
        int code = cause instanceof IllegalArgumentException ? 400 : cause instanceof SecurityException ? 403 : 500;
        return getErrorJSON(code, cause, pJmxReq);
    }

    // Extract class and exception message for an error message
    protected String getExceptionMessage(Throwable pException) {
        String message = pException.getLocalizedMessage();
        return pException.getClass().getName() + (message != null ? " : " + message : "");
    }

}
