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
import java.util.Set;

import org.json.simple.JSONObject;

/**
 * Response for J4pVersion request
 *
 * @author roland
 * @since Apr 24, 2010
 */
public final class J4pVersionResponse extends J4pResponse<J4pVersionRequest> {

    private final String jolokiaId;

    private JSONObject info;

    private final String agentVersion;

    private final String protocolVersion;

    private JSONObject details;

    J4pVersionResponse(J4pVersionRequest pRequest, JSONObject pResponse) {
        super(pRequest,pResponse);
        JSONObject value = getValue();
        agentVersion = (String) value.get("agent");
        protocolVersion = (String) value.get("protocol");
        details = (JSONObject) value.get("details");
        jolokiaId = (String) value.get("id");
        if (details == null) {
            details = new JSONObject();
        }
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
        return (String) details.get("server_product");
    }

    /**
     * Get the vendor of the remote application server or <code>null</code> if the
     * Jolokia agent could not detect the server
     *
     * @return venor name or <code>null</code>
     */
    public String getVendor() {
        return (String) details.get("server_vendor");
    }

    /**
     * Get the jolokia id of the server
     *
     * @return jolokia id
     */
    public String getJolokiaId() {
        return jolokiaId;
    }

    /**
     * Get all supported providers
     *
     * @return set of supported providers
     */
    public Set<String> getProviders() {
        //noinspection unchecked
        return info.keySet();
    }

    /**
     * Get extra information for a given provider
     *
     * @param pProvider provider for which information is requested
     * @return extra information for the provider, which might be null
     */
    public Map<?, ?> getExtraInfo(String pProvider) {
        return (Map<?, ?>) info.get(pProvider);
    }
}

