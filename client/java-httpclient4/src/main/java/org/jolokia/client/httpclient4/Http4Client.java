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
package org.jolokia.client.httpclient4;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.jolokia.client.EscapeUtil;
import org.jolokia.client.HttpUtil;
import org.jolokia.client.JolokiaClientBuilder;
import org.jolokia.client.JolokiaQueryParameter;
import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.client.exception.JolokiaConnectException;
import org.jolokia.client.exception.JolokiaException;
import org.jolokia.client.exception.JolokiaRemoteException;
import org.jolokia.client.exception.JolokiaTimeoutException;
import org.jolokia.client.request.HttpMethod;
import org.jolokia.client.request.JolokiaRequest;
import org.jolokia.client.response.JolokiaResponse;
import org.jolokia.client.spi.HttpClientSpi;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.json.JSONStructure;
import org.jolokia.json.parser.ParseException;

/**
 * {@link HttpClientSpi} implementation based on Apache HttpClient 4
 */
public class Http4Client implements HttpClientSpi<HttpClient> {

    private final HttpClient client;
    private final JolokiaClientBuilder.Configuration config;
    private final URI jolokiaAgentUrl;

    public Http4Client(HttpClient client, JolokiaClientBuilder.Configuration configuration) {
        this.client = client;
        this.config = configuration;
        this.jolokiaAgentUrl = this.config.url();
    }

    @Override
    public HttpClient getClient(Class<HttpClient> clientClass) {
        if (clientClass.isAssignableFrom(client.getClass())) {
            return clientClass.cast(client);
        }
        return null;
    }

    @Override
    public <REQ extends JolokiaRequest, RES extends JolokiaResponse<REQ>>
    JSONStructure execute(REQ pRequest, HttpMethod method, Map<JolokiaQueryParameter, String> parameters, JolokiaTargetConfig targetConfig)
            throws JolokiaException {
        HttpUriRequest httpRequest = prepareRequest(pRequest, method, parameters, targetConfig);

        return execute(httpRequest, pRequest, pRequest.getType().getValue());
    }

    @Override
    public <REQ extends JolokiaRequest, RES extends JolokiaResponse<REQ>>
    JSONStructure execute(List<REQ> pRequests, Map<JolokiaQueryParameter, String> parameters, JolokiaTargetConfig targetConfig)
            throws JolokiaException {
        HttpUriRequest httpRequest = prepareRequests(pRequests, parameters, targetConfig);

        return execute(httpRequest, null, "bulk");
    }

    @Override
    public void close() throws IOException {
        if (client instanceof CloseableHttpClient closable) {
            closable.close();
        }
    }

    // methods that help to convert between Jolokia requests/responses and HTTP requests/responses for Apache Http4 Client

    /**
     * Prepare an {@link HttpRequest} to send a {@link JolokiaRequest} over HTTP. {@link JolokiaTargetConfig} is used
     * from {@link JolokiaRequest#getTargetConfig()}} or from the argument (in that order)
     *
     * @param pRequest
     * @param method
     * @param parameters
     * @param pTargetConfig
     * @return
     * @param <REQ>
     */
    private <REQ extends JolokiaRequest>
    HttpUriRequest prepareRequest(REQ pRequest, HttpMethod method, Map<JolokiaQueryParameter, String> parameters, JolokiaTargetConfig pTargetConfig) {
        JolokiaTargetConfig targetConfig = HttpUtil.determineTargetConfig(pRequest, pTargetConfig);
        HttpMethod selectedMethod = HttpUtil.determineHttpMethod(pRequest, method, targetConfig);
        String queryParams = HttpUtil.toQueryString(parameters);

        Charset charset = config.contentCharset() == null ? StandardCharsets.UTF_8 : config.contentCharset();

        HttpUriRequest request = null;
        // GET request
        if (selectedMethod.equals(HttpMethod.GET)) {
            if (targetConfig != null) {
                throw new IllegalArgumentException("Proxy requests should be sent using POST method");
            }
            List<String> parts = pRequest.getRequestParts();
            // If parts == null then the request tells use there's nothing to encode as GET URL, so we must use POST
            // parts do NOT include operation type (list, read, write, exec, search, version, ...)
            if (parts != null) {
                String base = HttpUtil.prepareBaseUrl(jolokiaAgentUrl);
                StringBuilder requestPath = new StringBuilder(base);
                // operation type ALWAYS comes first
                requestPath.append(pRequest.getType().getValue());
                // and then operation specific "parts" (like mbean name, operation name, attribute name, ...
                for (String p : parts) {
                    requestPath.append("/");
                    requestPath.append(EscapeUtil.escape(p));
                }
                URI uri = HttpUtil.prepareFullUrl(jolokiaAgentUrl, requestPath.toString(), queryParams);

                request = new HttpGet(uri);
            }
        }

        if (request == null) {
            HttpPost postRequest = new HttpPost(HttpUtil.prepareFullUrl(jolokiaAgentUrl, jolokiaAgentUrl.getPath(), queryParams));
            JSONObject requestContent = HttpUtil.getJsonRequestContent(pRequest, targetConfig);
            postRequest.setEntity(new StringEntity(requestContent.toJSONString(), StandardCharsets.UTF_8));
            postRequest.addHeader(HTTP.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            request = postRequest;
        }

        return request;
    }

    /**
     * Prepare an {@link HttpRequest} to send multiple {@link JolokiaRequest Jolokia requests} over HTTP.
     * {@link JolokiaTargetConfig} is used from {@link JolokiaRequest#getTargetConfig()}} (independently for each
     * request) or from the argument (in that order)
     *
     * @param pRequests
     * @param parameters
     * @param pTargetConfig
     * @return
     * @param <REQ>
     */
    private <REQ extends JolokiaRequest>
    HttpUriRequest prepareRequests(List<REQ> pRequests, Map<JolokiaQueryParameter, String> parameters, JolokiaTargetConfig pTargetConfig) {
        String queryParams = HttpUtil.toQueryString(parameters);
        Charset charset = config.contentCharset() == null ? StandardCharsets.UTF_8 : config.contentCharset();

        JSONArray bulkRequest = new JSONArray(pRequests.size());
        for (REQ request : pRequests) {
            // each request (could be multiple types with REQ = ?) is added to the request JSON array
            JSONObject requestContent = HttpUtil.getJsonRequestContent(request, HttpUtil.determineTargetConfig(request, pTargetConfig));
            bulkRequest.add(requestContent);
        }

        HttpPost request = new HttpPost(HttpUtil.prepareFullUrl(jolokiaAgentUrl, jolokiaAgentUrl.getPath(), queryParams));
        // POST - we need to pass the body to the request
        request.setEntity(new StringEntity(bulkRequest.toJSONString(), StandardCharsets.UTF_8));
        request.addHeader(HTTP.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());

        return request;
    }

    /**
     * Private method that sends already prepared {@link HttpRequest HTTP requests}.
     *
     * @param httpRequest
     * @param jolokiaRequest request being sent over HTTP - could be null for bulk requests
     * @param requestType    for logging purpose
     * @return
     */
    private JSONStructure execute(HttpUriRequest httpRequest, JolokiaRequest jolokiaRequest, String requestType) throws JolokiaException {
        try {
            HttpResponse response = client.execute(httpRequest);
            StatusLine statusLine = response.getStatusLine();
            int errorCode = statusLine.getStatusCode();

            if (errorCode != 200) {
                // no need to parse, because Jolokia JSON responses for errors are sent with HTTP 200 code
                throw new JolokiaException("HTTP error " + errorCode + " sending " + requestType + " Jolokia request");
            }

            HttpEntity entity = response.getEntity();
            if (entity != null) {
                try {
                    Charset encoding;
                    Header contentEncoding = entity.getContentEncoding();
                    if (contentEncoding == null) {
                        encoding = config.contentCharset() == null ? StandardCharsets.ISO_8859_1 : config.contentCharset();
                    } else {
                        encoding = Charset.forName(contentEncoding.getValue());
                    }
                    return HttpUtil.parseJsonResponse(entity.getContent(), encoding);
                } catch (ParseException e) {
                    // JSON parsing error - convert to Jolokia exception
                    String errorType = e.getClass().getName();
                    String message = "Error parsing " + requestType + " response: " + e.getMessage();
                    throw new JolokiaException(message);
                } finally {
                    EntityUtils.consume(entity);
                }
            } else {
                // no data at all
                throw new JolokiaException("No data received from the remote Jolokia Agent for " + requestType);
            }
        } catch (ConnectException e) {
            String msg = "Cannot connect to " + jolokiaAgentUrl + ": " + e.getMessage();
            throw new JolokiaConnectException(msg, e);
        } catch (ConnectTimeoutException e) {
            String msg = "Connection timeout when sending " + requestType + " request to " + jolokiaAgentUrl + ": " + e.getMessage();
            throw new JolokiaTimeoutException(msg, e);
        } catch (IOException e) {
            String msg = "I/O exception when processing " + requestType + " request to " + jolokiaAgentUrl + ": " + e.getMessage();
            throw new JolokiaException(msg, e);
        }
    }

}
