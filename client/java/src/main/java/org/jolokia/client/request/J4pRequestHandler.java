package org.jolokia.client.request;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
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
    private String j4pServerUrl;

    // Escape patterns
    private static final Pattern SLASH_PATTERN = Pattern.compile("/+");
    private static final Pattern ESCAPED_SLASH_PATTERN = Pattern.compile("%2F");

    public J4pRequestHandler(String pJ4pServerUrl) {
        j4pServerUrl = pJ4pServerUrl;
    }

    /**
     * Get the HttpRequest for executing the given single request
     *
     * @param pRequest request to convert
     * @param pPreferredMethod HTTP method preferred
     * @return the request used with HttpClient to obtain the result.
     */
    public HttpUriRequest getHttpRequest(J4pRequest pRequest,String pPreferredMethod) throws UnsupportedEncodingException {
        String method = pPreferredMethod;
        if (method == null) {
            method = pRequest.getPreferredHttpMethod();
        }
        if (method == null) {
            method = HttpGet.METHOD_NAME;
        }
        if (method.equals(HttpGet.METHOD_NAME)) {
            List<String> parts = pRequest.getRequestParts();
            // If parts == null the request decides, that POST *must* be used
            if (parts != null) {
                StringBuilder requestPath = new StringBuilder();
                requestPath.append(pRequest.getType().getValue());
                for (String p : parts) {
                    requestPath.append("/");
                    requestPath.append(escape(p));
                }
                // TODO: Option handling, special escaping
                return new HttpGet(j4pServerUrl + "/" + requestPath.toString());
            }
        }


        // We are using a post method as fallback
        // TODO: Option handling
        JSONObject requestContent = pRequest.toJson();
        HttpPost postReq = new HttpPost(j4pServerUrl);
        postReq.setEntity(new StringEntity(requestContent.toJSONString(),"utf-8"));
        return postReq;
    }

    /**
     * Get an HTTP Request for requesting multips requests at once
     *
     * @param pRequests requests to put into a HTTP request
     * @return HTTP request to send to the server
     */
    public <T extends J4pRequest> HttpUriRequest getHttpRequest(List<T> pRequests) throws UnsupportedEncodingException {
        JSONArray bulkRequest = new JSONArray();
        HttpPost postReq = new HttpPost(j4pServerUrl);
        for (T request : pRequests) {
            JSONObject requestContent = request.toJson();
            bulkRequest.add(requestContent);
        }
        postReq.setEntity(new StringEntity(bulkRequest.toJSONString(),"utf-8"));
        return postReq;
    }


    /**
     * Extract the complete JSON response out of a HTTP response
     *
     * @param pRequest the original J4p request
     * @param pHttpResponse the resulting http response
     * @param <T> J4p Request
     * @return JSON content of the answer
     * @throws J4pException when parsing of the answer fails
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
                entity.consumeContent();
            }
        }
    }

    /**
     * Extract a {@link J4pResponse} out of a JSON object
     *
     * @param pRequest request which lead to the response
     * @param pJsonResponse JSON response
     * @param <T> request type.
     * @param <R> response type
     * @return the J4p response
     */
    public <R extends J4pResponse<T>,T extends J4pRequest> R extractResponse(T pRequest,JSONObject pJsonResponse) {
        return pRequest.<R>createResponse(pJsonResponse);
    }

    // Escape a part for usage as part of URI path
    private String escape(String pPart) throws UnsupportedEncodingException {
        Matcher matcher = SLASH_PATTERN.matcher(pPart);
        int index = 0;
        StringBuilder ret = new StringBuilder();
        while (matcher.find()) {
            String part = pPart.subSequence(index, matcher.start()).toString();
            ret.append(part).append("/");
            ret.append(escapeSlash(pPart, matcher));
            ret.append("/");
            index = matcher.end();
        }
        if (index != pPart.length()) {
            ret.append(pPart.substring(index,pPart.length()));
        }
        return uriEscape(ret);
    }

    private String escapeSlash(String pPart, Matcher pMatcher) {
        StringBuilder ret = new StringBuilder();
        String separator = pPart.substring(pMatcher.start(), pMatcher.end());
        int len = separator.length();
        for (int i = 0;i<len;i++) {
            if (i == 0 && pMatcher.start() == 0) {
                ret.append("^");
            } else if (i == len - 1 && pMatcher.end() == pPart.length()) {
                ret.append("+");
            } else {
                ret.append("-");
            }
        }
        return ret.toString();
    }

    private String uriEscape(StringBuilder pRet) throws UnsupportedEncodingException {
        // URI Escape unsafe chars
        String encodedRet = URLEncoder.encode(pRet.toString(),"utf-8");
        // Translate all "/" back...
        return ESCAPED_SLASH_PATTERN.matcher(encodedRet).replaceAll("/");
    }

}
