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
package org.jolokia.server.core.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.json.JSONStructure;
import org.jolokia.json.parser.JSONParser;
import org.jolokia.json.parser.ParseException;
import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.request.BaseRequestHandler;
import org.jolokia.server.core.request.EmptyResponseException;
import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.server.core.request.JolokiaRequestFactory;
import org.jolokia.server.core.request.ProcessingParameters;
import org.jolokia.server.core.service.api.JolokiaContext;

/**
 * <p>Request handler with no dependency on the servlet API, but designed for handling HTTP requests.
 * It can be used in several different web environments (like for the Sun JDK 11+
 * {@link com.sun.net.httpserver.HttpServer}) or Servlet container.</p>
 *
 * <p>Methods of this interface produce a JSON response to be sent to sender, but the sending should be done
 * by the caller.</p>
 *
 * @author roland
 * @since Mar 3, 2010
 */
public class HttpRequestHandler extends BaseRequestHandler {

    /**
     * Request handler for parsing HTTP request and dispatching to the appropriate
     * request handler (with help of the backend manager)
     *
     * @param context jolokia context
     */
    public HttpRequestHandler(JolokiaContext context) {
        super(context);
    }

    /**
     * Handle a GET request
     *
     * @param pUri          URI leading to this request
     * @param pPathInfo     path of the request
     * @param pParameterMap parameters of the GET request
     * @return the JSON response
     * @throws BadRequestException if there's a parsing error or parameter processing error (always sender's fault)
     * @throws EmptyResponseException if the connection should not be closed (only for notifications)
     */
    public JSONStructure handleGetRequest(String pUri, String pPathInfo, Map<String, String[]> pParameterMap)
            throws EmptyResponseException, BadRequestException {
        String pathInfo = extractPathInfo(pUri, pPathInfo);

        JolokiaRequest jmxReq =
            JolokiaRequestFactory.createGetRequest(pathInfo, getProcessingParameter(pParameterMap));

        if (jolokiaCtx.isDebug()) {
            jolokiaCtx.debug("URI: " + pUri);
            jolokiaCtx.debug("Path-Info: " + pathInfo);
            jolokiaCtx.debug("Request: " + jmxReq.toString());
        }
        return executeRequest(jmxReq);
    }

    /**
     * Handle the input stream as given by a POST request
     *
     * @param pUri          URI leading to this request
     * @param pInputStream  input stream of the post request
     * @param pEncoding     optional encoding for the stream. If null, the default encoding is used
     * @param pParameterMap additional processing parameters
     * @return the JSON object containing the json results for one or more {@link JolokiaRequest} contained
     * within the answer.
     * @throws IOException if reading from the input stream fails - so it doesn't have to be sender's fault
     * @throws BadRequestException if there's a parsing error or parameter processing error (always sender's fault)
     * @throws EmptyResponseException if the connection should not be closed (only for notifications)
     */
    public JSONStructure handlePostRequest(String pUri, InputStream pInputStream, String pEncoding, Map<String, String[]> pParameterMap)
            throws IOException, BadRequestException, EmptyResponseException {
        if (jolokiaCtx.isDebug()) {
            jolokiaCtx.debug("URI: " + pUri);
        }

        ProcessingParameters parameters = getProcessingParameter(pParameterMap);
        Object jsonRequest = extractJsonRequest(pInputStream, pEncoding);
        if (jsonRequest instanceof JSONArray) {
            List<JolokiaRequest> jolokiaRequests = JolokiaRequestFactory.createPostRequests((JSONArray) jsonRequest, parameters);

            JSONArray responseList = new JSONArray(jolokiaRequests.size());
            for (JolokiaRequest jmxReq : jolokiaRequests) {
                if (jolokiaCtx.isDebug()) {
                    jolokiaCtx.debug("Request: " + jmxReq.toString());
                }
                // Call handler and retrieve return value
                JSONObject resp = executeRequest(jmxReq);
                responseList.add(resp);
            }
            return responseList;
        } else if (jsonRequest instanceof JSONObject) {
            JolokiaRequest jmxReq = JolokiaRequestFactory.createPostRequest((JSONObject) jsonRequest, parameters);
            return executeRequest(jmxReq);
        } else {
            throw new BadRequestException("Invalid JSON Request. Expected Object or Array");
        }
    }

    /**
     * Handling an option request which is used for preflight checks before a CORS based browser request is
     * sent (for certain circumstances).
     * <p>
     * See the <a href="http://www.w3.org/TR/cors/">CORS specification</a>
     * (section 'preflight checks') for more details.
     *
     * @param pOrigin         the origin to check. If <code>null</code>, no headers are returned
     * @param pRequestHeaders extra headers to check against
     * @return headers to set
     */
    public Map<String, String> handleCorsPreflightRequest(String pOrigin, String pRequestHeaders) {
        Map<String, String> ret = new HashMap<>();
        if (jolokiaCtx.isOriginAllowed(pOrigin, false)) {
            // CORS is allowed, we set exactly the origin in the header, so there are no problems with authentication
            ret.put("Access-Control-Allow-Origin", pOrigin == null || "null".equals(pOrigin) ? "*" : pOrigin);
            if (pRequestHeaders != null) {
                ret.put("Access-Control-Allow-Headers", pRequestHeaders);
            }
            // Fix for CORS with authentication (#104)
            ret.put("Access-Control-Allow-Credentials", "true");
            // Allow for one year. Changes in access.xml are reflected directly in the CORS request itself
            ret.put("Access-Control-Max-Age", "" + 3600 * 24 * 365);
        }
        return ret;
    }

    /**
     * Extract JSON data from the incoming {@link InputStream}.
     *
     * @param pInputStream
     * @param pEncoding
     * @return
     * @throws IOException when there's a non-parser related issue with the incoming stream
     * @throws BadRequestException when the stream can be properly read, but JSON parsing fails
     */
    private Object extractJsonRequest(InputStream pInputStream, String pEncoding) throws IOException, BadRequestException {
        InputStreamReader reader;
        try {
            reader =
                pEncoding != null ?
                    new InputStreamReader(pInputStream, pEncoding) :
                    new InputStreamReader(pInputStream);
            JSONParser parser = new JSONParser();
            return parser.parse(reader);
        } catch (ParseException exp) {
            // JSON parsing error means we can't even know if it's bulk request or not, so HTTP 400
            throw new BadRequestException("Invalid JSON request", exp);
        }
    }

    /**
     * Check whether the given host and/or address is allowed to access this agent.
     *
     * @param pRequestScheme scheme used to make the request ('http' or 'https')
     * @param pHost          host to check
     * @param pAddress       address to check
     * @param pOrigin        (optional) origin header to check also.
     */
    public void checkAccess(String pRequestScheme, String pHost, String pAddress, String pOrigin) {
        if (!jolokiaCtx.isRemoteAccessAllowed(pHost != null ? new String[]{pHost, pAddress} : new String[]{pAddress})) {
            throw new SecurityException("No access from client " + pAddress + " allowed");
        }
        if (!jolokiaCtx.isOriginAllowed(pOrigin, true)) {
            throw new SecurityException("Origin " + pOrigin + " is not allowed to call this agent");
        }

        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Origin
        if (!jolokiaCtx.ignoreScheme() && "http".equals(pRequestScheme) && pOrigin != null && !"null".equals(pOrigin)) {
            try {
                String originScheme = new URL(pOrigin).getProtocol();
                // Requests with HTTPS origin should not be responded over HTTP,
                // as it compromises data confidentiality and integrity.
                if ("https".equals(originScheme)) {
                    throw new SecurityException("Secure origin " + pOrigin + " should not be processed over HTTP");
                }
            } catch (MalformedURLException e) {
                // Ignore it, should be safe as origin is not https anyway
            }
        }
    }

    /**
     * Check whether for the given host is a cross-browser request allowed. This check is delegated to the
     * backend manager which is responsible for the security configuration.
     * Also, some sanity checks are applied.
     *
     * @param pOrigin the origin URL to check against
     * @return the origin to put in the response header or null if none is to be set
     */
    public String extractCorsOrigin(String pOrigin) {
        if (pOrigin != null) {
            // Prevent HTTP response splitting attacks
            String origin = pOrigin.replaceAll("[\\n\\r]*", "");
            if (jolokiaCtx.isOriginAllowed(origin, false)) {
                return "null".equals(origin) ? "*" : origin;
            } else {
                return null;
            }
        }
        return null;
    }

    // Path info might need some special handling in case when the URL
    // contains two following slashes. These slashes get collapsed
    // when calling getPathInfo() but are still present in the URI.
    // This situation can happen, when slashes are escaped and the last char
    // of a path part is such an escaped slash
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
                return URLDecoder.decode(pathInfoEncoded, StandardCharsets.UTF_8);
            }
        }
        return pPathInfo;
    }

}
