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

import java.util.List;

import org.json.simple.JSONObject;

/**
 * Request object abstracting a request to a j4p agent.
 *
 * @author roland
 * @since Apr 24, 2010
 */
public abstract class J4pRequest {

    // request type
    private J4pType type;

    // "GET" or "POST"
    private String preferredHttpMethod;

    protected J4pRequest(J4pType pType) {
        type = pType;
    }

    /**
     * Get the type of the request
     *
     * @return request's type
     */
    public J4pType getType() {
        return type;
    }

    // ==================================================================================================
    // Methods used for building up HTTP Requests and setting up the reponse
    // These methods are package visible only since are used only internally

    // Get the parts to build up a GET url (without the type as the first part)
    abstract List<String> getRequestParts();

    // Get a JSON representation of this request
    JSONObject toJson() {
        JSONObject ret = new JSONObject();
        ret.put("type",type.name());
        return ret;
    }

    /**
     * Create a response from a given JSON response
     *
     * @param pResponse http response as obtained from the Http-Request
     * @return the create response
     */
    abstract <R extends J4pResponse<? extends J4pRequest>> R createResponse(JSONObject pResponse);

    public String getPreferredHttpMethod() {
        return preferredHttpMethod;
    }

    public void setPreferredHttpMethod(String pPreferredHttpMethod) {
        preferredHttpMethod = pPreferredHttpMethod;
    }
}
