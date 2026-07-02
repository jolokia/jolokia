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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.jolokia.server.core.restrictor.policy.CorsChecker;
import org.jolokia.server.core.restrictor.policy.HttpMethodChecker;
import org.jolokia.server.core.restrictor.policy.PolicyRestrictor;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.util.HttpMethod;

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

    // restrictor which we'll use (traditionally) to control CORS responses
    private final Restrictor restrictor;
    // whether authentication is enabled (for CORS purposes)
    private final boolean authenticationEnabled;

    /**
     * Request handler for parsing HTTP request and dispatching to the appropriate
     * request handler (with help of the backend manager)
     *
     * @param context     jolokia context
     * @param pRestrictor
     */
    public HttpRequestHandler(JolokiaContext context, Restrictor pRestrictor, boolean pAuthenticationEnabled) {
        super(context);
        this.restrictor = pRestrictor;
        this.authenticationEnabled = pAuthenticationEnabled;
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
     * Handling an {@code OPTIONS} request which is used for preflight checks before a CORS based browser request is
     * sent (for certain circumstances). {@link JolokiaContext#isOriginAllowed} is already called, so no need
     * to call it again. Just check if it's non null - without {@code Origin} header we should not send any CORS
     * response headers.
     * <p>
     * See the <a href="http://www.w3.org/TR/cors/">CORS specification</a>
     * (section 'preflight checks') for more details.
     *
     * @param pOrigin             the origin to check. If <code>null</code>, no headers are returned
     * @param pCorsRequestHeaders incoming {@code Access-Control-Request-Headers} (not yet validated/sanitized)
     * @return headers to set in a response for proper CORS preflight request
     */
    public Map<String, String> handleCorsPreflightRequest(String pOrigin, String pCorsRequestHeaders)
            throws BadRequestException {

        validateHeader(pCorsRequestHeaders);

        if (pOrigin == null || pOrigin.trim().isEmpty() || "null".equals(pOrigin.trim())) {
            return Collections.emptyMap();
        }

        // these are the CORS preflight response headers:
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS#the_http_response_headers
        // + Access-Control-Allow-Credentials
        //   we'll send true only if Jolokia is configured with authentication
        // + Access-Control-Allow-Headers
        //   the practice is to reflect incoming Access-Control-Request-Headers and let the browser control
        //   the response with Access-Control-Allow-Origin
        // + Access-Control-Allow-Methods
        //   we'll return whatever is present in jolokia-access.xml
        // + Access-Control-Allow-Origin
        //   this is tricky in Jolokia, because org.jolokia.server.core.restrictor.policy.CorsChecker uses
        //   regexp patterns and we can only return '*' or actual origin
        // + Access-Control-Max-Age
        //   no need to configure - we'll stick to the default from Chrome (2 hours. Firefox has 1 day)

        Map<String, String> ret = new HashMap<>();
        if (pCorsRequestHeaders != null) {
            // in theory if Authorization is not requested, when we return http 401, browser will send another
            // preflight request announcing the Authorization header
            ret.put("Access-Control-Allow-Headers", pCorsRequestHeaders);
        }
        ret.put("Access-Control-Max-Age", Integer.toString(2 * 60 * 60));
        ret.put("Access-Control-Allow-Credentials", Boolean.toString(this.authenticationEnabled));

        if (restrictor instanceof PolicyRestrictor pr) {
            HttpMethodChecker httpMethodConfig = pr.getHttpMethodChecker();
            Set<String> methods = new HashSet<>();
            if (httpMethodConfig == null) {
                methods.add("GET");
                methods.add("POST");
            } else {
                if (httpMethodConfig.check(HttpMethod.GET)) {
                    methods.add("GET");
                }
                if (httpMethodConfig.check(HttpMethod.POST)) {
                    methods.add("POST");
                }
            }
            if (!methods.isEmpty()) {
                ret.put("Access-Control-Allow-Methods", String.join(", ", methods));
            }
        } else {
            ret.put("Access-Control-Allow-Methods", "GET, POST");
        }

        // Passing false for "only if strict checking" means that we always check actually configured origins.
        // This is a call for actual CORS handling from JavaScript code calling fetch() in a modern browser
        if (restrictor.isOriginAllowed(pOrigin, false)) {
            // we allow a give origin also when there are no <cors>/<allow-origin> elements configured
            // for the policy restrictor (or the configured restrictor simply allows the access)
            ret.put("Access-Control-Allow-Origin", pOrigin);
        }

        return ret;
    }

    /**
     * This method prepares CORS response headers for non-preflight requests
     *
     * @param pOrigin
     * @return non null Map of headers values if there's a need to set CORS response headers
     */
    public Map<String, String> prepareCorsResponseHeaders(String pOrigin) {
        if (pOrigin == null || pOrigin.trim().isEmpty() || "null".equals(pOrigin.trim())) {
            // no incoming Origin - no CORS response headers
            return Collections.emptyMap();
        }

        Map<String, String> ret = new HashMap<>();
        ret.put("Access-Control-Allow-Credentials", Boolean.toString(this.authenticationEnabled));
        if (restrictor.isOriginAllowed(pOrigin, false)) {
            // we allow a give origin also when there are no <cors>/<allow-origin> elements configured
            // for the policy restrictor (or the configured restrictor simply allows the access)
            ret.put("Access-Control-Allow-Origin", pOrigin);
        }

        return ret;
    }

    /**
     * Do some security validation on the incoming header before we process it
     * @param incomingHeader
     */
    private void validateHeader(String incomingHeader) throws BadRequestException {
        if (incomingHeader == null || incomingHeader.trim().isEmpty()) {
            return;
        }
        if (incomingHeader.contains("\r") || incomingHeader.contains("\n")) {
            throw new BadRequestException("Illegal HTTP header value");
        }
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
     * <p>Check whether the given host and/or address is allowed to access this agent. Additional access check
     * is performed against {@code Origin} or {@code Referer} headers which can't be controlled using JavaScript
     * in the browser (when calling {@code fetch()} API).</p>
     *
     * <p>{@code Origin} or {@code Referer} is <strong>not</strong> a protection at all when using other
     * clients like {@code curl}. Additionally {@code Origin} header is not used for handling preflight CORS
     * requests here.</p>
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
        // passing true for "only if strict checking" means that the access can be granted if there's
        // no Origin (or Referer) header included. This is used for handling requests not related to "real" CORS protocol
        // for protecting JavaScript running in a browser
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
     * <p>Check whether for the given host is a cross-browser request allowed. This check is delegated to the
     * backend manager which is responsible for the security configuration.
     * Also, some sanity checks are applied.</p>
     *
     * <p>This method calls {@link JolokiaContext#isOriginAllowed} so should not be called in normal flow. We
     * keep it for compatibility reasons (it's a public method).</p>
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
                String pathInfoEncoded = pUri.replaceFirst("^.*?" + Pattern.quote(prefix), prefix);
                return URLDecoder.decode(pathInfoEncoded, StandardCharsets.UTF_8);
            }
        }
        return pPathInfo;
    }

}
