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

import java.util.Set;

import org.jolokia.client.request.JolokiaVersionRequest;
import org.jolokia.json.JSONObject;

/**
 * Response for {@link JolokiaVersionRequest}
 *
 * @author roland
 * @since Apr 24, 2010
 */
public final class JolokiaVersionResponse extends JolokiaResponse<JolokiaVersionRequest> {

    /** Version of Jolokia agent and the library as available in Maven Central */
    private final String agentVersion;

    /** <a href="https://jolokia.org/reference/html/manual/jolokia_protocol.html#versions">Jolokia protocol version</a> */
    private final String protocolVersion;

    private JSONObject details;
    private final String jolokiaId;
    private JSONObject config;
    private JSONObject info;

    /**
     * Create a response to {@link org.jolokia.client.JolokiaOperation#VERSION} based on defined structure.
     *
     * @param pRequest
     * @param pResponse
     */
    public JolokiaVersionResponse(JolokiaVersionRequest pRequest, JSONObject pResponse) {
        super(pRequest, pResponse);
        JSONObject value = getValue();
        agentVersion = (String) value.get("agent");
        protocolVersion = (String) value.get("protocol");
        jolokiaId = (String) value.get("id");

        details = (JSONObject) value.get("details");
        if (details == null) {
            details = new JSONObject();
        }

        config = (JSONObject) value.get("config");
        if (config == null) {
            config = new JSONObject();
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
     * Jolokia protocol version used by the remote Jolokia agent
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
     * could not detect it.
     */
    public String getProduct() {
        return (String) details.get("server_product");
    }

    /**
     * Get the vendor of the remote application server or <code>null</code> if the
     * Jolokia agent could not detect the server
     *
     * @return vendor name or <code>null</code>
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
        return info.keySet();
    }

    /**
     * Get extra information for a given provider
     *
     * @param pProvider provider for which information is requested
     * @return extra information for the provider, which might be null
     */
    public JSONObject getExtraInfo(String pProvider) {
        return (JSONObject) info.get(pProvider);
    }

    /**
     * Return {@code config} information from version response.
     * @return
     */
    public JSONObject getConfig() {
        return config;
    }

}
