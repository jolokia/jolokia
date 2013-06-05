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

import java.util.Map;

import org.json.simple.JSONObject;

/**
 * Response for J4pVersion request
 *
 * @author roland
 * @since Apr 24, 2010
 */
public final class J4pVersionResponse extends J4pResponse<J4pVersionRequest> {

    private String agentVersion;

    private String protocolVersion;

    private JSONObject info;

    J4pVersionResponse(J4pVersionRequest pRequest, JSONObject pResponse) {
        super(pRequest,pResponse);
        JSONObject value = (JSONObject) getValue();
        agentVersion = (String) value.get("agent");
        protocolVersion = (String) value.get("protocol");
        info = (JSONObject) value.get("info");
        if (info == null) {
            info = new JSONObject();
        }
    }

    /**
     * The version of the Jolokia agent
     *
     * @return version
     */
    public String getAgentVersion() {
        return agentVersion;
    }

    /**
     * Jolokia protocol version by the remote Jolokia agent
     *
     * @return protocol version (as string)
     */
    public String getProtocolVersion() {
        return protocolVersion;
    }

    /**
     * Product detected by the Jolokia agent or <code>null</code> if it could
     * not be detected
     *
     * @return remote application server type or <code>null</code> if the agent
     *         could not detect it.
     */
    public String getProduct() {
        return (String) info.get("product");
    }

    /**
     * Get the vendor of the remote application server or <code>null</code> if the
     * Jolokia agent could not detect the server
     *
     * @return venor name or <code>null</code>
     */
    public String getVendor() {
        return (String) info.get("vendor");
    }

    /**
     * Any extra information specific to a certain application server type or <code>null</code> if no
     * extra information is provided
     *
     * @return map with string keys and value containing extra information on the server or <code>null</code>
     */
    public Map getExtraInfo() {
        return (Map) info.get("extraInfo");
    }
}

