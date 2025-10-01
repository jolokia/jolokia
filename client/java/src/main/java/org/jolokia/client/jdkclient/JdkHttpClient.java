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
package org.jolokia.client.jdkclient;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jolokia.client.HttpUtil;
import org.jolokia.client.EscapeUtil;
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
import org.jolokia.client.spi.HttpHeader;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.json.JSONStructure;
import org.jolokia.json.parser.ParseException;

public class JdkHttpClient implements HttpClientSpi<HttpClient> {

    private final HttpClient client;
    private final JolokiaClientBuilder.Configuration config;
    private final URI jolokiaAgentUrl;

    public JdkHttpClient(HttpClient client, JolokiaClientBuilder.Configuration configuration) {
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
            throws IOException, JolokiaException {
        // JDK HTTP request from Jolokia request
        HttpRequest httpRequest = prepareRequest(pRequest, method, parameters, targetConfig);

        return execute(httpRequest, pRequest, pRequest.getType().getValue());
    }

    @Override
    public <REQ extends JolokiaRequest, RES extends JolokiaResponse<REQ>>
    JSONStructure execute(List<REQ> pRequests, Map<JolokiaQueryParameter, String> parameters, JolokiaTargetConfig targetConfig)
            throws IOException, JolokiaException {
        // JDK HTTP request from bulk Jolokia request
        HttpRequest httpRequest = prepareRequests(pRequests, parameters, targetConfig);

        return execute(httpRequest, null, "bulk");
    }

    @Override
    public void close() {
        // noop
    }

    // methods that help to convert between Jolokia requests/responses and HTTP requests/responses for JDK HTTP Client

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
    HttpRequest prepareRequest(REQ pRequest, HttpMethod method, Map<JolokiaQueryParameter, String> parameters, JolokiaTargetConfig pTargetConfig) {
        JolokiaTargetConfig targetConfig = HttpUtil.determineTargetConfig(pRequest, pTargetConfig);
        HttpMethod selectedMethod = HttpUtil.determineHttpMethod(pRequest, method, targetConfig);
        String queryParams = HttpUtil.toQueryString(parameters);

        Charset charset = config.contentCharset() == null ? StandardCharsets.UTF_8 : config.contentCharset();

        HttpRequest.Builder builder = null;
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

                builder = HttpRequest.newBuilder().uri(uri).GET();
            }
        }

        if (builder == null) {
            // POST - we need to pass the body to the request
            JSONObject requestContent = HttpUtil.getJsonRequestContent(pRequest, targetConfig);
            HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofString(requestContent.toJSONString(), charset);

            builder = HttpRequest.newBuilder()
                .uri(HttpUtil.prepareFullUrl(jolokiaAgentUrl, jolokiaAgentUrl.getPath(), queryParams))
                .header("Content-Type", "application/json")
                .POST(publisher);
            // for POST only
            builder.expectContinue(config.expectContinue());
        }

        return configureRequest(builder);
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
    HttpRequest prepareRequests(List<REQ> pRequests, Map<JolokiaQueryParameter, String> parameters, JolokiaTargetConfig pTargetConfig) {
        String queryParams = HttpUtil.toQueryString(parameters);
        Charset charset = config.contentCharset() == null ? StandardCharsets.UTF_8 : config.contentCharset();

        JSONArray bulkRequest = new JSONArray(pRequests.size());
        for (REQ request : pRequests) {
            // each request (could be multiple types with REQ = ?) is added to the request JSON array
            JSONObject requestContent = HttpUtil.getJsonRequestContent(request, HttpUtil.determineTargetConfig(request, pTargetConfig));
            bulkRequest.add(requestContent);
        }

        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofString(bulkRequest.toJSONString(), charset);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(HttpUtil.prepareFullUrl(jolokiaAgentUrl, jolokiaAgentUrl.getPath(), queryParams))
            .header("Content-Type", "application/json")
            .POST(publisher);

        // for POST only
        builder.expectContinue(config.expectContinue());

        return configureRequest(builder);
    }

    /**
     * Complete configuration of {@link HttpRequest.Builder} by setting common configuration options.
     *
     * @param builder
     * @return
     */
    private HttpRequest configureRequest(HttpRequest.Builder builder) {
        if (config.connectionConfig().socketTimeout() > 0) {
            builder.timeout(Duration.ofMillis(config.connectionConfig().socketTimeout()));
        }

        // emulate preemptive authentication here
        if (config.user() != null && !config.user().isEmpty()) {
            String credentials = config.user() + ":" + (config.password() == null ? "" : config.password());
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + encoded);
        }

        Collection<HttpHeader> customHeaders = config.defaultHttpHeaders();
        if (customHeaders != null) {
            for (HttpHeader h : customHeaders) {
                builder.setHeader(h.name(), h.value());
            }
        }

        return builder
            .header("Content-Type", "application/json")
            .build();
    }

    /**
     * Private method that sends already prepared {@link HttpRequest HTTP requests}.
     *
     * @param httpRequest
     * @param jolokiaRequest request being sent over HTTP - could be null for bulk requests
     * @param requestType    for logging purpose
     * @return
     */
    private JSONStructure execute(HttpRequest httpRequest, JolokiaRequest jolokiaRequest, String requestType) throws IOException, JolokiaException {
        // Response to be handled/returned as InputStream
        HttpResponse.BodyHandler<InputStream> responseHandler = HttpResponse.BodyHandlers.ofInputStream();
        try {
            HttpResponse<InputStream> response = client.send(httpRequest, responseHandler);
            int errorCode = response.statusCode();

            // just parse without interpretation
            try (InputStream body = response.body()) {
                if (errorCode != 200) {
                    // no need to parse, because Jolokia JSON responses for errors are sent with HTTP 200 code
                    throw new JolokiaRemoteException(jolokiaRequest, "HTTP error " + errorCode + " sending " + requestType + " Jolokia request",
                        null, errorCode, null, null);
                }

                Optional<String> encoding = response.headers().firstValue("Content-Encoding");
                if (encoding.isEmpty()) {
                    encoding = Optional.of((config.contentCharset() == null ? StandardCharsets.ISO_8859_1 : config.contentCharset()).name());
                }
                return HttpUtil.parseJsonResponse(body, Charset.forName(encoding.get()));
            } catch (ParseException e) {
                // JSON parsing error - convert to Jolokia exception
                String errorType = e.getClass().getName();
                String message = "Error parsing " + requestType + " response: " + e.getMessage();
                throw new JolokiaRemoteException(jolokiaRequest, message, errorType, errorCode, null, null);
            }
        } catch (ConnectException e) {
            String msg = "Cannot connect to " + jolokiaAgentUrl + ": " + e.getMessage();
            throw new JolokiaConnectException(msg, e);
        } catch (HttpConnectTimeoutException e) {
            String msg = "Connection timeout when sending " + requestType + " request to " + jolokiaAgentUrl + ": " + e.getMessage();
            throw new JolokiaTimeoutException(msg, e);
        } catch (HttpTimeoutException e) {
            String msg = "Timeout when processing " + requestType + " request to " + jolokiaAgentUrl + ": " + e.getMessage();
            throw new JolokiaTimeoutException(msg, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JolokiaException("Interrupted while sending " + requestType + " Jolokia request", e);
        }
    }

}
