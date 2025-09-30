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
package org.jolokia.client;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jolokia.client.exception.J4pBulkRemoteException;
import org.jolokia.client.exception.J4pConnectException;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.exception.J4pRemoteException;
import org.jolokia.client.request.HttpMethod;
import org.jolokia.client.request.JolokiaRequest;
import org.jolokia.client.response.JolokiaResponse;
import org.jolokia.client.response.J4pResponseExtractor;
import org.jolokia.client.response.ValidatingResponseExtractor;
import org.jolokia.client.spi.HttpClientSpi;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.json.JSONStructure;

/**
 * <p>The main, high level Jolokia Client which can be used to send {@link JolokiaRequest Jolokia requests} and receive
 * {@link JolokiaResponse Jolokia responses}.</p>
 *
 * <p>Jolokia client doesn't depend directly on any HTTP Client implementation, it delegates to particular
 * implementation using discoverable {@link HttpClientSpi HTTP Client SPI} with default implementation
 * based on JDK built-in HTTP Client.</p>
 *
 * @author roland
 * @since Apr 24, 2010
 */
public class JolokiaClient {

    /**
     * Discoverable {@link HttpClientSpi} used to perform actual HTTP requests, so this {@link JolokiaClient} is
     * implementation agnostic.
     */
    private final HttpClientSpi<?> httpClient;

    /**
     * Base URI of the remote Jolokia Agent.
     */
    private final URI jolokiaAgentUrl;

    /**
     * Proxy configuration when remote Jolokia Agent works in Proxy Mode (proxy connections to ultimate
     * JMX {@link javax.management.remote.JMXConnectorServer} or another Jolokia Agent).
     */
    private final JolokiaTargetConfig targetConfig;

    /** {@link J4pResponseExtractor} that builds actual {@link JolokiaResponse Jolokia responses} */
    private final J4pResponseExtractor responseExtractor;

    /**
     * Construct a new client for a given server url (as String)
     *
     * @param pJolokiaAgentUrl URL of the remote Jolokia Agent
     */
    public JolokiaClient(URI pJolokiaAgentUrl) {
        this(pJolokiaAgentUrl, null);
    }

    /**
     * Construct a new client for a given server url (as String) and existing {@link HttpClientSpi}
     *
     * @param pJolokiaAgentUrl URL of the remote Jolokia Agent
     * @param pHttpClient      {@link HttpClientSpi} for actual HTTP connection to the agent
     */
    public JolokiaClient(URI pJolokiaAgentUrl, HttpClientSpi<?> pHttpClient) {
        this(pJolokiaAgentUrl, pHttpClient, null);
    }

    /**
     * Construct a new client for a given server url (as String) and existing {@link HttpClientSpi}. Additionally
     * we can specify {@link JolokiaTargetConfig} when connecting to Jolokia Agent running in proxy mode.
     *
     * @param pJolokiaAgentUrl URL of the remote Jolokia Agent
     * @param pHttpClient      {@link HttpClientSpi} for actual HTTP connection to the agent
     * @param pTargetConfig    information for proxy mode
     */
    public JolokiaClient(URI pJolokiaAgentUrl, HttpClientSpi<?> pHttpClient, JolokiaTargetConfig pTargetConfig) {
        this(pJolokiaAgentUrl, pHttpClient, pTargetConfig, ValidatingResponseExtractor.DEFAULT);
    }


    /**
     * The constructor with all parameters to create a {@link JolokiaClient} for connections to remote Jolokia Agent using
     * selected {@link HttpClientSpi}, optional {@link JolokiaTargetConfig proxy configuration} and actual
     * {@link J4pResponseExtractor} for handling responses.
     *
     * @param pJolokiaAgentUrl the agent URL for how to contact the server.
     * @param pHttpClient      {@link HttpClientSpi} for actual HTTP connection to the agent
     * @param pTargetConfig    information for proxy mode
     * @param pExtractor       response extractor to use
     */
    public JolokiaClient(URI pJolokiaAgentUrl, HttpClientSpi<?> pHttpClient, JolokiaTargetConfig pTargetConfig, J4pResponseExtractor pExtractor) {
        jolokiaAgentUrl = pJolokiaAgentUrl;
        targetConfig = pTargetConfig;
        responseExtractor = pExtractor;

        // Using the default as defined in the client builder
        if (pHttpClient != null) {
            httpClient = pHttpClient;
        } else {
            JolokiaClientBuilder builder = new JolokiaClientBuilder().url(pJolokiaAgentUrl);
            if (targetConfig != null) {
                builder.target(targetConfig.url()).targetUser(targetConfig.user()).targetPassword(targetConfig.password());
            }
            httpClient = builder.createHttpClient();
        }
    }

    /**
     * Get base URL for Jolokia requests
     *
     * @return the Jolokia URL
     */
    public URI getUri() {
        return jolokiaAgentUrl;
    }

    /**
     * Expose the implementation used by {@link HttpClientSpi} to configure implementation-specific parameters.
     *
     * @return the http client used for HTTP communications
     */
    @SuppressWarnings("unchecked")
    public <T> T getHttpClient(Class<T> clientClass) {
        return ((HttpClientSpi<T>) httpClient).getClient(clientClass);
    }

    // methods for sending a single JolokiaRequest and retrieve related JolokiaResponse

    /**
     * Send a single {@link JolokiaRequest} and retrieve {@link JolokiaResponse}.
     * The HTTP Method used is determined automatically and HTTP connection is performed using {@link HttpClientSpi}.
     *
     * @param pRequest request to execute
     * @param <REQ>    request type
     * @param <RESP>   response type
     * @return the response as returned by the server
     * @throws J4pException if something's wrong (e.g. connection failed or read timeout)
     */
    public <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    RESP execute(REQ pRequest) throws J4pException {
        return execute(pRequest, null, null);
    }

    /**
     * Send a single {@link JolokiaRequest} with additional processing parameters and retrieve {@link JolokiaResponse}.
     * The HTTP Method used is determined automatically and HTTP connection is performed using {@link HttpClientSpi}.
     *
     * @param pRequest           request to execute
     * @param pProcessingOptions optional map of processing options
     * @param <REQ>              request type
     * @param <RESP>             response type
     * @return the response as returned by the server
     * @throws J4pException if something's wrong (e.g. connection failed or read timeout)
     */
    public <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    RESP execute(REQ pRequest, Map<JolokiaQueryParameter, String> pProcessingOptions) throws J4pException {
        return execute(pRequest, null, pProcessingOptions);
    }

    /**
     * Send a single {@link JolokiaRequest} and retrieve {@link JolokiaResponse}.
     * The HTTP Method used is passed with the request and should be valid according to the request.
     * HTTP connection is performed using {@link HttpClientSpi}.
     *
     * @param pRequest request to execute
     * @param pMethod  {@link HttpMethod} to use
     * @param <REQ>    request type
     * @param <RESP>   response type
     * @return response object
     * @throws J4pException if something's wrong (e.g. connection failed or read timeout)
     */
    public <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    RESP execute(REQ pRequest, HttpMethod pMethod) throws J4pException {
        return execute(pRequest, pMethod, null);
    }

    /**
     * Send a single {@link JolokiaRequest} with additional processing parameters and retrieve {@link JolokiaResponse}.
     * The HTTP Method used is passed with the request and should be valid according to the request.
     * HTTP connection is performed using {@link HttpClientSpi}.
     *
     * @param pRequest           request to execute
     * @param pMethod            {@link HttpMethod} to use
     * @param pProcessingOptions optional map of processing options
     * @param <REQ>              request type
     * @param <RESP>             response type
     * @return response object
     * @throws J4pException if something's wrong (e.g. connection failed or read timeout)
     */
    public <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    RESP execute(REQ pRequest, HttpMethod pMethod, Map<JolokiaQueryParameter, String> pProcessingOptions) throws J4pException {
        return execute(pRequest, pMethod, pProcessingOptions, responseExtractor);
    }

    /**
     * Send a single {@link JolokiaRequest} with additional processing parameters and retrieve {@link JolokiaResponse}.
     * The HTTP Method used is passed with the request and should be valid according to the request.
     * HTTP connection is performed using {@link HttpClientSpi}. The response is created with the help
     * of the passed {@link J4pResponseExtractor}.
     *
     * @param pRequest           request to execute
     * @param pMethod            method to use which should be either "GET" or "POST"
     * @param pProcessingOptions optional map of processing options
     * @param pResponseExtractor         extractor for actually creating the response
     * @param <REQ>              request type
     * @param <RESP>             response type
     * @return response object
     * @throws J4pException if something's wrong (e.g. connection failed or read timeout)
     */
    public <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    RESP execute(REQ pRequest, HttpMethod pMethod, Map<JolokiaQueryParameter, String> pProcessingOptions, J4pResponseExtractor pResponseExtractor)
            throws J4pException {
        try {
            JSONStructure jsonResponse = httpClient.execute(pRequest, pMethod, pProcessingOptions, targetConfig);
            if (!(jsonResponse instanceof JSONObject)) {
                String msg = jsonResponse == null ? "an empty response" : "a " + jsonResponse.getClass().getName();
                throw new J4pException("Invalid JSON response for a single request (expected a Map but got " + msg + ")");
            }

            return extractResponse((JSONObject) jsonResponse, pRequest, pProcessingOptions, pResponseExtractor);
        } catch (IOException e) {
            throw mapException(e);
        }
    }

    // methods for sending multiple JolokiaRequests and retrieve related JolokiaResponses

    /**
     * Execute multiple requests at once. All given {@link JolokiaRequest requests} will be sent using
     * a single HTTP request where it gets dispatched on the agent side.
     * The {@link JolokiaResponse results} are returned in the same order as the provided requests.
     *
     * @param pRequests requests to execute
     * @param <REQ>     request type
     * @param <RESP>    response type
     * @return list of responses, one response for each request
     * @throws J4pException when a communication error occurs
     */
    public <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    List<RESP> execute(List<REQ> pRequests) throws J4pException {
        return execute(pRequests, null);
    }

    /**
     * Execute multiple requests at once. All given {@link JolokiaRequest requests} will be sent using
     * a single HTTP request (with additional {@link JolokiaQueryParameter parameters}) where it gets dispatched
     * on the agent side. The {@link JolokiaResponse results} are returned in the same order as the provided requests.
     *
     * @param pRequests          requests to send
     * @param pProcessingOptions processing options to use
     * @param <REQ>              request type
     * @param <RESP>             response type
     * @return list of responses, one response for each request
     * @throws J4pException when a communication error occurs
     */
    public <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    List<RESP> execute(List<REQ> pRequests, Map<JolokiaQueryParameter, String> pProcessingOptions) throws J4pException {
        return execute(pRequests, pProcessingOptions, responseExtractor);
    }

    /**
     * Execute multiple requests at once. All given {@link JolokiaRequest requests} will be sent using
     * a single HTTP request where it gets dispatched on the agent side.
     * The {@link JolokiaResponse results} are returned in the same order as the provided requests.
     * The response is created with the help of the passed {@link J4pResponseExtractor}.
     *
     * @param pRequests requests to execute
     * @param <REQ>     request type
     * @param <RESP>    response type
     * @return list of responses, one response for each request
     * @throws J4pException when a communication error occurs
     */
    @SafeVarargs
    public final <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    List<RESP> execute(REQ... pRequests) throws J4pException {
        return this.execute(Arrays.asList(pRequests));
    }

    /**
     * Execute multiple requests at once. All given {@link JolokiaRequest requests} will be sent using
     * a single HTTP request (with additional {@link JolokiaQueryParameter parameters}) where it gets dispatched
     * on the agent side. The {@link JolokiaResponse results} are returned in the same order as the provided requests.
     * The response is created with the help of the passed {@link J4pResponseExtractor}.
     *
     * @param pRequests          requests to execute
     * @param pProcessingOptions processing options to use
     * @param pResponseExtractor use this for custom extraction handling
     * @param <REQ>              request type
     * @param <RESP>             response type
     * @return list of responses, one response for each request
     * @throws J4pException when a communication error occurs
     */
    public <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    List<RESP> execute(List<REQ> pRequests, Map<JolokiaQueryParameter, String> pProcessingOptions, J4pResponseExtractor pResponseExtractor)
            throws J4pException {
        try {
            JSONStructure jsonResponse = httpClient.execute(pRequests, pProcessingOptions, targetConfig);
            if (!(jsonResponse instanceof JSONArray)) {
                if (jsonResponse instanceof JSONObject errorObject) {
                    // bulk response may end with single JSONObject, let's check if it's a "proper error"
                    if (!errorObject.containsKey("status") || ((Number) errorObject.get("status")).intValue() != 200) {
                        throw new J4pRemoteException(null, errorObject);
                    }
                }
                throw new J4pException("Invalid JSON response for a bulk request (expected an array but got a " + jsonResponse.getClass().getName() + ")");
            }

            return extractResponses((JSONArray) jsonResponse, pRequests, pProcessingOptions, pResponseExtractor);
        } catch (IOException e) {
            throw mapException(e);
        }
    }

    // private helper methods

    /**
     * Combine initial {@link JolokiaRequest} and received {@link JSONObject} response and return proper
     * {@link JolokiaResponse}
     *
     * @param jsonResponse
     * @param pRequest
     * @param pProcessingOptions
     * @param pResponseExtractor
     * @return
     * @param <REQ>
     * @param <RESP>
     * @throws J4pException
     */
    private <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    RESP extractResponse(JSONObject jsonResponse, REQ pRequest, Map<JolokiaQueryParameter, String> pProcessingOptions, J4pResponseExtractor pResponseExtractor)
            throws J4pException {
        boolean excludeRequest = pProcessingOptions != null && "false".equals(pProcessingOptions.get(JolokiaQueryParameter.INCLUDE_REQUEST));
        return pResponseExtractor.extract(pRequest, jsonResponse, !excludeRequest);
    }

    // Extract J4pResponses from a returned bulk JSON answer

    /**
     * Combine a list of initial {@link JolokiaRequest requests} and received {@link JSONObject} bulk response
     * and return list of proper {@link JolokiaResponse} responses. Because any individual request may end up with
     * a Jolokia error, we either return a {@link List} of {@link JolokiaResponse Jolokia responses} or throw
     * a {@link J4pException} where we can find successful responses and exception in correct order.
     *
     * @param pJsonResponse
     * @param pRequests
     * @param pProcessingOptions
     * @param pResponseExtractor
     * @return
     * @param <REQ>
     * @param <RESP>
     * @throws J4pException
     */
    private <RESP extends JolokiaResponse<REQ>, REQ extends JolokiaRequest>
    List<RESP> extractResponses(JSONArray pJsonResponse, List<REQ> pRequests, Map<JolokiaQueryParameter, String> pProcessingOptions,
                                J4pResponseExtractor pResponseExtractor)
            throws J4pException {
        // the trick is that any initial request may result in proper JolokiaResponse or an error object
        List<RESP> ret = new ArrayList<>(pJsonResponse.size());
        J4pRemoteException[] remoteExceptions = new J4pRemoteException[pJsonResponse.size()];

        boolean excludeRequest = pProcessingOptions != null
            && "false".equals(pProcessingOptions.get(JolokiaQueryParameter.INCLUDE_REQUEST));

        boolean exceptionFound = false;
        for (int i = 0; i < pRequests.size(); i++) {
            REQ request = pRequests.get(i);
            Object jsonResp = pJsonResponse.get(i);
            if (!(jsonResp instanceof JSONObject)) {
                throw new J4pException("Response for request Nr " + i + " is invalid (expected a Map but got "
                    + jsonResp.getClass().getName() + ")");
            }
            try {
                ret.add(i, pResponseExtractor.extract(request, (JSONObject) jsonResp, !excludeRequest));
            } catch (J4pRemoteException exp) {
                remoteExceptions[i] = exp;
                exceptionFound = true;
                // add a null response to not mess up with the order
                ret.add(i, null);
            }
        }

        if (exceptionFound) {
            // we've collected some responses, but will "return" them in the bulk exception thrown
            List<Object> responsesAndErrors = new ArrayList<>();
            for (int i = 0; i < pRequests.size(); i++) {
                J4pRemoteException exp = remoteExceptions[i];
                if (exp != null) {
                    responsesAndErrors.add(exp);
                } else {
                    responsesAndErrors.add(ret.get(i));
                }
            }
            throw new J4pBulkRemoteException(responsesAndErrors);
        }

        // here we have all successful responses
        return ret;
    }

    /**
     * Translate {@link IOException} to {@link J4pException}
     *
     * @param ex
     * @return
     */
    private J4pException mapException(IOException ex) {
        if (ex instanceof ConnectException cex) {
            String msg = "Cannot connect to " + jolokiaAgentUrl + (ex.getMessage() != null ? ": " + ex.getMessage() : "");
            return new J4pConnectException(msg, cex);
        } else {
            return new J4pException("IO-Error during connection to " + jolokiaAgentUrl + ": " + ex.getMessage(), ex);
        }
    }

}
