package org.jolokia.client.request;

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

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Class doing the hard work of conversion between HTTP request/responses and
 * J4p request/responses.
 *
 * @author roland
 * @since Apr 25, 2010
 */
public class J4pRequestHandler {

    // j4p agent URL for the agent server
    private URI j4pServerUrl;

    // Optional default target configuration
    private J4pTargetConfig defaultTargetConfig;

    /**
     * Constructor
     *
     * @param pJ4pServerUrl URL to remote agent
     * @param pTargetConfig optional default target configuration for proxy requests
     */
    public J4pRequestHandler(String pJ4pServerUrl, J4pTargetConfig pTargetConfig) {
        try {
            j4pServerUrl = new URI(pJ4pServerUrl);
            defaultTargetConfig = pTargetConfig;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL " + pJ4pServerUrl,e);
        }
    }

    /**
     * Get the HttpRequest for executing the given single request
     *
     * @param pRequest request to convert
     * @param pPreferredMethod HTTP method preferred
     * @param pProcessingOptions optional map of processiong options
     * @return the request used with HttpClient to obtain the result.
     */
    public HttpUriRequest getHttpRequest(J4pRequest pRequest, String pPreferredMethod,
                                         Map<J4pQueryParameter, String> pProcessingOptions) throws UnsupportedEncodingException, URISyntaxException {
        String method = pPreferredMethod;
        if (method == null) {
            method = pRequest.getPreferredHttpMethod();
        }
        if (method == null) {
            method = doUseProxy(pRequest) ? HttpPost.METHOD_NAME : HttpGet.METHOD_NAME;
        }
        String queryParams = prepareQueryParameters(pProcessingOptions);

        // GET request
        if (method.equals(HttpGet.METHOD_NAME)) {
            if (doUseProxy(pRequest)) {
                throw new IllegalArgumentException("Proxy mode can only be used with POST requests");
            }
            List<String> parts = pRequest.getRequestParts();
            // If parts == null the request decides, that POST *must* be used
            if (parts != null) {
                String base = prepareBaseUrl(j4pServerUrl);
                StringBuilder requestPath = new StringBuilder(base);
                requestPath.append(pRequest.getType().getValue());
                for (String p : parts) {
                    requestPath.append("/");
                    requestPath.append(escape(p));
                }
                return new HttpGet(createRequestURI(requestPath.toString(),queryParams));
            }
        }

        // We are using a post method as fallback
        JSONObject requestContent = getJsonRequestContent(pRequest);
        HttpPost postReq = new HttpPost(createRequestURI(j4pServerUrl.getPath(),queryParams));
        postReq.setEntity(new StringEntity(requestContent.toJSONString(),"utf-8"));
        return postReq;
    }

    private boolean doUseProxy(J4pRequest pRequest) {
        return defaultTargetConfig != null || pRequest.getTargetConfig() != null;
    }

    private String prepareBaseUrl(URI pUri) {
        String base = pUri.getPath();
        if (base == null) {
            return "/";
        } else if (!base.endsWith("/")) {
            return base + "/";
        } else {
            return base;
        }
    }


    /**
     * Get an HTTP Request for requesting multiples requests at once
     *
     * @param pRequests requests to put into a HTTP request
     * @return HTTP request to send to the server
     */
    public <T extends J4pRequest> HttpUriRequest getHttpRequest(List<T> pRequests,Map<J4pQueryParameter,String> pProcessingOptions)
            throws UnsupportedEncodingException, URISyntaxException {
        JSONArray bulkRequest = new JSONArray();
        String queryParams = prepareQueryParameters(pProcessingOptions);
        HttpPost postReq = new HttpPost(createRequestURI(j4pServerUrl.getPath(),queryParams));
        for (T request : pRequests) {
            JSONObject requestContent = getJsonRequestContent(request);
            bulkRequest.add(requestContent);
        }
        postReq.setEntity(new StringEntity(bulkRequest.toJSONString(),"utf-8"));
        return postReq;
    }


    /**
     * Extract the complete JSON response out of a HTTP response
     *
     * @param pHttpResponse the resulting http response
     * @return JSON content of the answer
     */
    @SuppressWarnings("PMD.PreserveStackTrace")
    public JSONAware extractJsonResponse(HttpResponse pHttpResponse) throws IOException, ParseException {
        HttpEntity entity = pHttpResponse.getEntity();
        try {
            JSONParser parser = new JSONParser();
            Header contentEncoding = entity.getContentEncoding();
            if (contentEncoding != null) {
                return (JSONAware) parser.parse(new InputStreamReader(entity.getContent(), Charset.forName(contentEncoding.getValue())));
            } else {
                return (JSONAware) parser.parse(new InputStreamReader(entity.getContent()));
            }
        } finally {
            if (entity != null) {
                EntityUtils.consume(entity);
            }
        }
    }

    /**
     * Get the J4p Server URL
     * @return the URL to the Jolokia agent on the server side
     */
    public URI getJ4pServerUrl() {
        return j4pServerUrl;
    }

    // =============================================================================================================

    private JSONObject getJsonRequestContent(J4pRequest pRequest) {
        JSONObject requestContent = pRequest.toJson();
        if (defaultTargetConfig != null && pRequest.getTargetConfig() == null) {
            requestContent.put("target", defaultTargetConfig.toJson());
        }
        return requestContent;
    }

    // Escape a part for usage as part of URI path: / -> \/, \ -> \\
    private static final String ESCAPE = "!";
    private static final Pattern ESCAPE_PATTERN = Pattern.compile(ESCAPE);
    private static final Pattern SLASH_PATTERN = Pattern.compile("/");
    private String escape(String pPart) {
        String ret = ESCAPE_PATTERN.matcher(pPart).replaceAll(ESCAPE + ESCAPE);
        return SLASH_PATTERN.matcher(ret).replaceAll(ESCAPE + "/");
    }

    // Create the request URI to use
    private URI createRequestURI(String path,String queryParams) throws URISyntaxException {
        return new URI(j4pServerUrl.getScheme(),
                                  j4pServerUrl.getUserInfo(),
                                  j4pServerUrl.getHost(),
                                  j4pServerUrl.getPort(),
                                  path,
                                  queryParams,null);
    }

    // prepare query parameters
    private String prepareQueryParameters(Map<J4pQueryParameter, String> pProcessingOptions) {
        if (pProcessingOptions != null && pProcessingOptions.size() > 0) {
            StringBuilder queryParams = new StringBuilder();
            for (Map.Entry<J4pQueryParameter,String> entry : pProcessingOptions.entrySet()) {
                queryParams.append(entry.getKey().getParam()).append("=").append(entry.getValue()).append("&");
            }
            return queryParams.substring(0,queryParams.length() - 1);
        } else {
            return null;
        }
    }
}
