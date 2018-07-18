package org.jolokia.http;

import java.io.*;
import java.net.URLDecoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.*;

import org.jolokia.backend.BackendManager;
import org.jolokia.config.*;
import org.jolokia.request.JmxRequest;
import org.jolokia.request.JmxRequestFactory;
import org.jolokia.util.LogHandler;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

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
 * Request handler with no dependency on the servlet API so that it can be used in
 * several different environments (like for the Sun JDK 6 {@link com.sun.net.httpserver.HttpServer}.
 *
 * @author roland
 * @since Mar 3, 2010
 */
public class HttpRequestHandler {

    // handler for contacting the MBean server(s)
    private BackendManager backendManager;

    // Logging abstraction
    private LogHandler logHandler;

    // Global configuration
    private Configuration config;

    /**
     * Request handler for parsing HTTP request and dispatching to the appropriate
     * request handler (with help of the backend manager)
     *
     * @param pBackendManager backend manager to user
     * @param pLogHandler log handler to where to put out logging
     */
    public HttpRequestHandler(Configuration pConfig, BackendManager pBackendManager, LogHandler pLogHandler) {
        backendManager = pBackendManager;
        logHandler = pLogHandler;
        config = pConfig;
    }

    /**
     * Handle a GET request
     *
     * @param pUri URI leading to this request
     * @param pPathInfo path of the request
     * @param pParameterMap parameters of the GET request  @return the response
     */
    public JSONAware handleGetRequest(String pUri, String pPathInfo, Map<String, String[]> pParameterMap) {
        String pathInfo = extractPathInfo(pUri, pPathInfo);

        JmxRequest jmxReq =
                JmxRequestFactory.createGetRequest(pathInfo,getProcessingParameter(pParameterMap));

        if (backendManager.isDebug()) {
            logHandler.debug("URI: " + pUri);
            logHandler.debug("Path-Info: " + pathInfo);
            logHandler.debug("Request: " + jmxReq.toString());
        }
        return executeRequest(jmxReq);
    }

    private ProcessingParameters getProcessingParameter(Map<String, String[]> pParameterMap) {
        Map<String,String> ret = new HashMap<String, String>();
        if (pParameterMap != null) {
            for (Map.Entry<String,String[]> entry : pParameterMap.entrySet()) {
                String values[] = entry.getValue();
                if (values != null && values.length > 0) {
                        ret.put(entry.getKey(), values[0]);
                }
            }
        }
        return config.getProcessingParameters(ret);
    }

    /**
     * Handle the input stream as given by a POST request
     *
     *
     * @param pUri URI leading to this request
     * @param pInputStream input stream of the post request
     * @param pEncoding optional encoding for the stream. If null, the default encoding is used
     * @param pParameterMap additional processing parameters
     * @return the JSON object containing the json results for one or more {@link JmxRequest} contained
     *         within the answer.
     *
     * @throws IOException if reading from the input stream fails
     */
    public JSONAware handlePostRequest(String pUri, InputStream pInputStream, String pEncoding, Map<String, String[]>  pParameterMap)
            throws IOException {
        if (backendManager.isDebug()) {
            logHandler.debug("URI: " + pUri);
        }

        Object jsonRequest = extractJsonRequest(pInputStream,pEncoding);
        if (jsonRequest instanceof JSONArray) {
            List<JmxRequest> jmxRequests = JmxRequestFactory.createPostRequests((List) jsonRequest,getProcessingParameter(pParameterMap));

            JSONArray responseList = new JSONArray();
            for (JmxRequest jmxReq : jmxRequests) {
                if (backendManager.isDebug()) {
                    logHandler.debug("Request: " + jmxReq.toString());
                }
                // Call handler and retrieve return value
                JSONObject resp = executeRequest(jmxReq);
                responseList.add(resp);
            }
            return responseList;
        } else if (jsonRequest instanceof JSONObject) {
            JmxRequest jmxReq = JmxRequestFactory.createPostRequest((Map<String, ?>) jsonRequest,getProcessingParameter(pParameterMap));
            return executeRequest(jmxReq);
        } else {
            throw new IllegalArgumentException("Invalid JSON Request " + jsonRequest);
        }
    }

    /**
     * Handling an option request which is used for preflight checks before a CORS based browser request is
     * sent (for certain circumstances).
     *
     * See the <a href="http://www.w3.org/TR/cors/">CORS specification</a>
     * (section 'preflight checks') for more details.
     *
     * @param pOrigin the origin to check. If <code>null</code>, no headers are returned
     * @param pRequestHeaders extra headers to check against
     * @return headers to set
     */
    public Map<String, String> handleCorsPreflightRequest(String pOrigin, String pRequestHeaders) {
        Map<String,String> ret = new HashMap<String, String>();
        if (backendManager.isOriginAllowed(pOrigin,false)) {
            // CORS is allowed, we set exactly the origin in the header, so there are no problems with authentication
            ret.put("Access-Control-Allow-Origin",pOrigin == null || "null".equals(pOrigin) ? "*" : pOrigin);
            if (pRequestHeaders != null) {
                ret.put("Access-Control-Allow-Headers",pRequestHeaders);
            }
            // Fix for CORS with authentication (#104)
            ret.put("Access-Control-Allow-Credentials","true");
            // Allow for one year. Changes in access.xml are reflected directly in the CORS request itself
            ret.put("Access-Control-Max-Age","" + 3600 * 24 * 365);
        }
        return ret;
    }


    private Object extractJsonRequest(InputStream pInputStream, String pEncoding) throws IOException {
        InputStreamReader reader = null;
        try {
            reader =
                    pEncoding != null ?
                            new InputStreamReader(pInputStream, pEncoding) :
                            new InputStreamReader(pInputStream);
            JSONParser parser = new JSONParser();
            return parser.parse(reader);
        } catch (ParseException exp) {
            throw new IllegalArgumentException("Invalid JSON request " + reader,exp);
        }
    }

    /**
     * Execute a single {@link JmxRequest}. If a checked  exception occurs,
     * this gets translated into the appropriate JSON object which will get returned.
     * Note, that these exceptions gets *not* translated into an HTTP error, since they are
     * supposed <em>Jolokia</em> specific errors above the transport layer.
     *
     * @param pJmxReq the request to execute
     * @return the JSON representation of the answer.
     */
    private JSONObject executeRequest(JmxRequest pJmxReq) {
        // Call handler and retrieve return value
        try {
            return backendManager.handleRequest(pJmxReq);
        } catch (ReflectionException e) {
            return getErrorJSON(404,e, pJmxReq);
        } catch (InstanceNotFoundException e) {
            return getErrorJSON(404,e, pJmxReq);
        } catch (MBeanException e) {
            return getErrorJSON(500,e.getTargetException(), pJmxReq);
        } catch (AttributeNotFoundException e) {
            return getErrorJSON(404,e, pJmxReq);
        } catch (UnsupportedOperationException e) {
            return getErrorJSON(500,e, pJmxReq);
        } catch (IOException e) {
            return getErrorJSON(500,e, pJmxReq);
        } catch (IllegalArgumentException e) {
            return getErrorJSON(400,e, pJmxReq);
        } catch (SecurityException e) {
            // Wipe out stacktrace
            return getErrorJSON(403,new Exception(e.getMessage()), pJmxReq);
        } catch (RuntimeMBeanException e) {
            // Use wrapped exception
            return errorForUnwrappedException(e,pJmxReq);
        }
    }


    /**
     * Utility method for handling single runtime exceptions and errors. This method is called
     * in addition to and after {@link #executeRequest(JmxRequest)} to catch additional errors.
     * They are two different methods because of bulk requests, where each individual request can
     * lead to an error. So, each individual request is wrapped with the error handling of
     * {@link #executeRequest(JmxRequest)}
     * whereas the overall handling is wrapped with this method. It is hence more coarse grained,
     * leading typically to an status code of 500.
     *
     * Summary: This method should be used as last security belt is some exception should escape
     * from a single request processing in {@link #executeRequest(JmxRequest)}.
     *
     * @param pThrowable exception to handle
     * @return its JSON representation
     */
    public JSONObject handleThrowable(Throwable pThrowable) {
        if (pThrowable instanceof IllegalArgumentException) {
            return getErrorJSON(400,pThrowable, null);
        } else if (pThrowable instanceof SecurityException) {
            // Wipe out stacktrace
            return getErrorJSON(403,new Exception(pThrowable.getMessage()), null);
        } else {
            return getErrorJSON(500,pThrowable, null);
        }
    }


    /**
     * Get the JSON representation for a an exception
     *
     *
     * @param pErrorCode the HTTP error code to return
     * @param pExp the exception or error occured
     * @param pJmxReq request from where to get processing options
     * @return the json representation
     */
    public JSONObject getErrorJSON(int pErrorCode, Throwable pExp, JmxRequest pJmxReq) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("status",pErrorCode);
        jsonObject.put("error",getExceptionMessage(pExp));
        jsonObject.put("error_type", pExp.getClass().getName());
        addErrorInfo(jsonObject, pExp, pJmxReq);
        if (backendManager.isDebug()) {
            backendManager.error("Error " + pErrorCode,pExp);
        }
        if (pJmxReq != null) {
            jsonObject.put("request",pJmxReq.toJSON());
        }
        return jsonObject;
    }



    /**
     * Check whether the given host and/or address is allowed to access this agent.
     *
     * @param pHost host to check
     * @param pAddress address to check
     * @param pOrigin (optional) origin header to check also.
     */
    public void checkAccess(String pHost, String pAddress, String pOrigin) {
        if (!backendManager.isRemoteAccessAllowed(pHost, pAddress)) {
            throw new SecurityException("No access from client " + pAddress + " allowed");
        }
        if (!backendManager.isOriginAllowed(pOrigin,true)) {
            throw new SecurityException("Origin " + pOrigin + " is not allowed to call this agent");
        }
    }

    /**
     * Check whether for the given host is a cross-browser request allowed. This check is delegated to the
     * backendmanager which is responsible for the security configuration.
     * Also, some sanity checks are applied.
     *
     * @param pOrigin the origin URL to check against
     * @return the origin to put in the response header or null if none is to be set
     */
    public String extractCorsOrigin(String pOrigin) {
        if (pOrigin != null) {
            // Prevent HTTP response splitting attacks
            String origin  = pOrigin.replaceAll("[\\n\\r]*","");
            if (backendManager.isOriginAllowed(origin,false)) {
                return "null".equals(origin) ? "*" : origin;
            } else {
                return null;
            }
        }
        return null;
    }

    private void addErrorInfo(JSONObject pErrorResp, Throwable pExp, JmxRequest pJmxReq) {
        if (config.getAsBoolean(ConfigKey.ALLOW_ERROR_DETAILS)) {
            String includeStackTrace = pJmxReq != null ?
                    pJmxReq.getParameter(ConfigKey.INCLUDE_STACKTRACE) : "true";
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

    // Extract class and exception message for an error message
    private String getExceptionMessage(Throwable pException) {
        String message = pException.getLocalizedMessage();
        return pException.getClass().getName() + (message != null ? " : " + message : "");
    }

    // Unwrap an exception to get to the 'real' exception
    // and extract the error code accordingly
    private JSONObject errorForUnwrappedException(Exception e, JmxRequest pJmxReq) {
        Throwable cause = e.getCause();
        int code = cause instanceof IllegalArgumentException ? 400 : cause instanceof SecurityException ? 403 : 500;
        return getErrorJSON(code,cause, pJmxReq);
    }

    // Path info might need some special handling in case when the URL
    // contains two following slashes. These slashes get collapsed
    // when calling getPathInfo() but are still present in the URI.
    // This situation can happen, when slashes are escaped and the last char
    // of an path part is such an escaped slash
    // (e.g. "read/domain:type=name!//attribute")
    // In this case, we extract the path info on our own

    private static final Pattern PATH_PREFIX_PATTERN = Pattern.compile("^/?[^/]+/");

    private String extractPathInfo(String pUri, String pPathInfo) {
        if (pUri.contains("!//")) {
            // Special treatment for trailing slashes in paths
            Matcher matcher = PATH_PREFIX_PATTERN.matcher(pPathInfo);
            if (matcher.find()) {
                String prefix = matcher.group();
                String pathInfoEncoded = pUri.replaceFirst("^.*?" + prefix, prefix);
                try {
                    return URLDecoder.decode(pathInfoEncoded, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    // Should not happen at all ... so we silently fall through
                }
            }
        }
        return pPathInfo;
    }
}
