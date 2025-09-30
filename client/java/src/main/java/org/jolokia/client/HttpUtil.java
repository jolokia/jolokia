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
package org.jolokia.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Map;

import org.jolokia.client.request.HttpMethod;
import org.jolokia.client.request.JolokiaRequest;
import org.jolokia.json.JSONObject;
import org.jolokia.json.JSONStructure;
import org.jolokia.json.parser.JSONParser;
import org.jolokia.json.parser.ParseException;

/**
 * Helper class to deal with requests and responses regardless of the implementation of HTTP Client used.
 */
public class HttpUtil {

    /**
     * Helper method to transform a map of parameters to a query string of a HTTP request
     *
     * @param pProcessingOptions
     * @return
     */
    public static String toQueryString(Map<JolokiaQueryParameter, String> pProcessingOptions) {
        if (pProcessingOptions != null && !pProcessingOptions.isEmpty()) {
            StringBuilder queryParams = new StringBuilder();
            for (Map.Entry<JolokiaQueryParameter, String> entry : pProcessingOptions.entrySet()) {
                queryParams.append(entry.getKey().getParam()).append("=").append(entry.getValue()).append("&");
            }
            return queryParams.substring(0, queryParams.length() - 1);
        } else {
            return null;
        }
    }

    /**
     * Parse the JSON data available in the passed {@link InputStream}. It is the responsibility of the caller
     * to close this stream.
     *
     * @param body
     * @param charset
     * @return
     */
    public static JSONStructure parseJsonResponse(InputStream body, Charset charset) throws ParseException, IOException {
        JSONParser parser = new JSONParser();
        return (JSONStructure) parser.parse(new InputStreamReader(body, charset));
    }

    /**
     * Get effective {@link JolokiaTargetConfig} according to the order of preference.
     *
     * @param pRequest
     * @param pTargetConfig
     * @return
     * @param <REQ>
     */
    public static <REQ extends JolokiaRequest> JolokiaTargetConfig determineTargetConfig(REQ pRequest, JolokiaTargetConfig pTargetConfig) {
        JolokiaTargetConfig targetConfig = pRequest.getTargetConfig();
        if (targetConfig == null) {
            targetConfig = pTargetConfig;
        }
        return targetConfig;
    }

    /**
     * Get effective {@link HttpMethod} to use depending on request and proxy configuration.
     *
     * @param pRequest
     * @param method
     * @param targetConfig
     * @return
     * @param <REQ>
     */
    public static <REQ extends JolokiaRequest> HttpMethod determineHttpMethod(REQ pRequest, HttpMethod method, JolokiaTargetConfig targetConfig) {
        if (method == null) {
            method = pRequest.getPreferredHttpMethod();
        }
        if (method == null) {
            method = targetConfig != null ? HttpMethod.POST : HttpMethod.GET;
        }
        return method;
    }

    /**
     * Get base path of the remote Jolokia Agent URL - should not contain operation type or any operation specific
     * URL segments.
     *
     * @param jolokiaAgentUrl
     * @return
     */
    public static String prepareBaseUrl(URI jolokiaAgentUrl) {
        String base = jolokiaAgentUrl.getPath();
        if (base == null) {
            return "/";
        }
        if (!base.endsWith("/")) {
            return base + "/";
        }
        return base;
    }

    /**
     * Construct full URL to use when connecting to remote Jolokia Agent.
     *
     * @param jolokiaAgentUrl
     * @param path
     * @param queryParams
     * @return
     * @throws URISyntaxException
     */
    public static URI prepareFullUrl(URI jolokiaAgentUrl, String path, String queryParams) {
        try {
            return new URI(jolokiaAgentUrl.getScheme(), jolokiaAgentUrl.getUserInfo(),
                jolokiaAgentUrl.getHost(), jolokiaAgentUrl.getPort(),
                path, queryParams, null);
        } catch (URISyntaxException e) {
            // should not happen, because we're using existing correct URI
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Build {@link JSONObject} to send with {@link HttpMethod#POST} request.
     *
     * @param pRequest
     * @param pTargetConfig
     * @return
     */
    public static JSONObject getJsonRequestContent(JolokiaRequest pRequest, JolokiaTargetConfig pTargetConfig) {
        JSONObject requestContent = pRequest.toJson();
        if (pTargetConfig != null) {
            requestContent.put("target", pTargetConfig.toJson());
        }
        return requestContent;
    }

}
