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

import java.util.Date;

import org.jolokia.json.JSONObject;

/**
 * Representation of a j4p Response as sent by the
 * j4p agent.
 *
 * @author roland
 * @since Apr 24, 2010
 */
public abstract class J4pResponse<T extends J4pRequest> {

    // JSON representation of the returned response
    private final JSONObject jsonResponse;

    // request which lead to this response
    private T request;
    private final J4pType type;

    // timestamp of this response
    private final Date requestDate;

    protected J4pResponse(T pRequest, JSONObject pJsonResponse) {
        request = pRequest;
        type = pRequest.getType();
        jsonResponse = pJsonResponse;
        Number timestamp = (Number) jsonResponse.get("timestamp");
        requestDate = timestamp != null ? new Date(timestamp.longValue() * 1000) : new Date();
    }

    /**
     * Get the request associated with this response
     * @return the request
     */
    public T getRequest() {
        return request;
    }

    /**
     * For security reasons, we may want to clear the request from the response
     */
    public void clearRequest() {
        this.request = null;
    }

    /**
     * Get the request/response type
     *
     * @return type
     */
    public J4pType getType() {
        return type;
    }

    /**
     * Date when the request was processed
     *
     * @return request date
     */
    public Date getRequestDate() {
        return (Date) requestDate.clone();
    }

    /**
     * Get the value of this response
     *
     * @return json representation of answer
     */
    public <V> V getValue() {
        //noinspection unchecked
        return (V) jsonResponse.get("value");
    }

    /**
     * Get response as JSON Object
     */
    public JSONObject asJSONObject() {
        return jsonResponse;
    }

}
