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
package org.jolokia.client.response;

import java.time.Instant;

import org.jolokia.client.JolokiaOperation;
import org.jolokia.client.request.JolokiaRequest;
import org.jolokia.json.JSONObject;

/**
 * <p>Abstract Representation of <em>Jolokia response</em> received from remote Jolokia agent. Each Jolokia response
 * is a result of sending some {@link JolokiaRequest} and the related request/response use the same
 * {@link JolokiaOperation}.</p>
 *
 * <p>The {@link org.jolokia.client.request.HttpMethod#POST} representation of each response (a response for
 * each supported {@link org.jolokia.client.request.JolokiaRequest} is:<pre>{@code
 * {
 *     "request": { ... },
 *     "value": { ... },
 *     "status": status-like-200-or-404-or-any-other,
 *     "timestamp": UNIX-time-in-seconds
 * }
 * }</pre></p>
 *
 * <p>Each Jolokia response contains a response body in the form of {@link JSONObject} available at "value" field.
 * If the response is a <em>bulk response</em> which arrived as {@link org.jolokia.json.JSONArray}, the array of Jolokia
 * responses is created instead - each with single {@link JSONObject}.</p>
 *
 * @author roland
 * @since Apr 24, 2010
 */
public abstract class JolokiaResponse<T extends JolokiaRequest> {

    /** JSON representation of the returned response, available at "value" field */
    private final JSONObject jsonResponse;
    /** Type of {@link JolokiaRequest} associated with this response */
    private final JolokiaOperation requestType;

    /** Request for this Response */
    private T request;

    /** Timestamp of this response generated at server side, available at "timestamp" response field. */
    private final Instant responseTimestamp;

    /**
     * Create a response based on it's original {@link JolokiaRequest} and the {@link JSONObject} of the
     * retrieved response body.
     *
     * @param pRequest
     * @param pJsonResponse
     */
    protected JolokiaResponse(T pRequest, JSONObject pJsonResponse) {
        request = pRequest;
        requestType = pRequest.getType();
        jsonResponse = pJsonResponse;
        Object ts = jsonResponse.get("timestamp");
        responseTimestamp = ts instanceof Number timestamp ? Instant.ofEpochSecond(timestamp.longValue()) : Instant.now();
    }

    /**
     * Get the request associated with this response
     *
     * @return the request
     */
    public T getRequest() {
        return request;
    }

    /**
     * For security or optimization reasons, we may want to clear the request from the response
     */
    public void clearRequest() {
        this.request = null;
    }

    /**
     * Get the request/response type
     *
     * @return type
     */
    public JolokiaOperation getType() {
        return requestType;
    }

    /**
     * Timestamp when the request was processed or retrieved.
     *
     * @return request date
     */
    public Instant getResponseTimestamp() {
        return this.responseTimestamp;
    }

    /**
     * According to Jolokia protocol definition, each response should contain {@code value} field representing
     * the actual response (other top-level fields contain metadata, return codes, etc.). This method returns
     * the {@code value} object casting it to generic type.
     *
     * @return json representation of answer
     */
    @SuppressWarnings("unchecked")
    public <V> V getValue() {
        return (V) jsonResponse.get("value");
    }

    /**
     * Type-safe variant of {@link #getValue()}
     *
     * @param clazz the expected class of the {@code value}
     * @return json representation of answer or null if wrong type is used
     */
    public <V> V getValue(Class<V> clazz) {
        Object value = jsonResponse.get("value");
        return clazz.isAssignableFrom(value.getClass()) ? clazz.cast(value) : null;
    }

    /**
     * Get entire response as {@link JSONObject}. The response JSON should contain {@code value} field.
     */
    public JSONObject asJSONObject() {
        return jsonResponse;
    }

}
