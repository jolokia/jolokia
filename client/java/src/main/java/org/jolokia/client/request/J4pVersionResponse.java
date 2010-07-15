package org.jolokia.client.request;

import org.json.simple.JSONObject;

/**
 * @author roland
 * @since Apr 24, 2010
 */
public final class J4pVersionResponse extends J4pResponse<J4pVersionRequest> {

    private String agentVersion;

    private String protocolVersion;

    J4pVersionResponse(J4pVersionRequest pRequest, JSONObject pResponse) {
        super(pRequest,pResponse);
        JSONObject value = (JSONObject) getValue();
        agentVersion = (String) value.get("agent");
        protocolVersion = (String) value.get("protocol");
    }

    public String getAgentVersion() {
        return agentVersion;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }
}
