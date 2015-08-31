package org.jolokia.discovery;

import java.util.Map;

import org.jolokia.Version;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.util.NetworkUtil;
import org.json.simple.JSONObject;

import static org.jolokia.discovery.AbstractDiscoveryMessage.Payload;
import static org.jolokia.discovery.AbstractDiscoveryMessage.Payload.*;

/**
 * Agent details describing this agent. This information during
 * discovery of the agent. This object is mutable so that since
 * the information is possible collected peu-a-peu through various
 * channels.
 *
 * @author roland
 * @since 31.01.14
 */
public class AgentDetails {

    // Detected server handle
    private String serverVendor, serverProduct, serverVersion;

    // The URL on which an agent is listening
    private String url;

    // Whether the agent is secure via. If null, not information about the security
    // mode could be collected.
    private Boolean secured;

    // Agent version
    private String agentVersion;

    // Agent id
    private String agentId;

    // Description for the agent
    private String agentDescription;

    // Whether initialization is done and no further update is allowed
    private boolean sealed;

    public AgentDetails(String pAgentId) {
        agentVersion = Version.getAgentVersion();
        agentId = pAgentId;
        if (agentId == null) {
            throw new IllegalArgumentException("No agent id given");
        }
        sealed = false;
    }

    public AgentDetails(Configuration pConfig) {
        this(NetworkUtil.replaceExpression(pConfig.get(ConfigKey.AGENT_ID)));
        agentDescription = pConfig.get(ConfigKey.AGENT_DESCRIPTION);
    }

    /**
     * Constructor used when the input has been parsed
     *
     * @param pMsgData data send via multicast
     */
    public AgentDetails(Map<Payload, Object> pMsgData) {
        serverVendor = (String) pMsgData.get(SERVER_VENDOR);
        serverProduct = (String) pMsgData.get(SERVER_PRODUCT);
        serverVersion = (String) pMsgData.get(SERVER_VERSION);
        url = (String) pMsgData.get(URL);
        if (pMsgData.containsKey(SECURED)) {
            secured = (Boolean) pMsgData.get(SECURED);
        }
        agentVersion = (String) pMsgData.get(AGENT_VERSION);
        agentDescription = (String) pMsgData.get(AGENT_DESCRIPTION);
        agentId = (String) pMsgData.get(AGENT_ID);
        if (agentId == null) {
            throw new IllegalArgumentException("No agent id given");
        }
        sealed = true;
    }

    /**
     * Single method for updating the server information when the server has been detected
     *
     * @param pVendor vendor of the deteted container
     * @param pProduct name of the contained
     * @param pVersion server version (not Jolokia's version!)
     */
    public void setServerInfo(String pVendor, String pProduct, String pVersion) {
        checkSeal();
        serverVendor = pVendor;
        serverProduct = pProduct;
        serverVersion = pVersion;
    }

    public void setUrl(String pUrl) {
        checkSeal();
        url = pUrl;
    }

    public void setSecured(Boolean pSecured) {
        checkSeal();
        secured = pSecured;
    }

    private void checkSeal() {
        if (sealed) {
            throw new IllegalStateException("Cannot update agent details because it is already initialized and sealed");
        }
    }

    /**
     * Check if either url or security information is missing.
     * @return true if url or security information is missing and the initialization has not already be done
     */
    public boolean isInitRequired() {
        return !sealed && (isUrlMissing() || isSecuredMissing());
    }

    public boolean isUrlMissing() {
        return url == null;
    }

    public boolean isSecuredMissing() {
        return secured == null;
    }

    /**
     * Seal this details so that no further updates are possible
     */
    public void seal() {
        sealed = true;
    }

    /**
     * Get the ID specific for these agent
     * @return unique id identifying this agent or null if no ID was given.
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Get the details as JSON Object
     *
     * @return
     */
    public JSONObject toJSONObject() {
        JSONObject resp = new JSONObject();
        add(resp, URL,url);
        if (secured != null) {
            add(resp, SECURED, secured);
        }
        add(resp, SERVER_VENDOR, serverVendor);
        add(resp, SERVER_PRODUCT, serverProduct);
        add(resp, SERVER_VERSION, serverVersion);
        add(resp, AGENT_VERSION, agentVersion);
        add(resp, AGENT_ID, agentId);
        add(resp, AGENT_DESCRIPTION,agentDescription);
        return resp;
    }

    @Override
    public String toString() {
        return "AgentDetails{" + toJSONObject().toJSONString() + "}";
    }

    // =======================================================================================

    private void add(JSONObject pResp, Payload pKey, Object pValue) {
        if (pValue != null) {
            pResp.put(pKey.toString().toLowerCase(),pValue);
        }
    }
}
