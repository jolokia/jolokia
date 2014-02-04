package org.jolokia.discovery;

import java.util.Map;

import org.jolokia.Version;
import org.json.simple.JSONObject;

import static org.jolokia.discovery.AbstractDiscoveryMessage.Payload;

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

    // How high is the probability that the URL is correct ? (0 .. 100)
    private int confidence;

    // Whether the agent is secure via. If null, not information about the security
    // mode could be collected.
    private Boolean secured;

    // Agent version
    private String version;

    public AgentDetails() {
        version = Version.getAgentVersion();
    }

    /**
     * Constructor used when the input has been parsed
     * @param pMsgData
     */
    public AgentDetails(Map<Payload, Object> pMsgData) {
        serverVendor = (String) pMsgData.get(Payload.SERVER_VENDOR);
        serverProduct = (String) pMsgData.get(Payload.SERVER_PRODUCT);
        serverVersion = (String) pMsgData.get(Payload.SERVER_VERSION);
        url = (String) pMsgData.get(Payload.URL);
        if (pMsgData.containsKey(Payload.CONFIDENCE)) {
            confidence = ((Long) pMsgData.get(Payload.CONFIDENCE)).intValue();
        }
        if (pMsgData.containsKey(Payload.SECURED)) {
            secured = (Boolean) pMsgData.get(Payload.SECURED);
        }
        version = (String) pMsgData.get(Payload.VERSION);
    }

    public void setServerInfo(String pVendor, String pProduct, String pVersion) {
        serverVendor = pVendor;
        serverProduct = pProduct;
        serverVersion = pVersion;
    }

    public void updateAgentParameters(String pUrl, int pConfidence, Boolean pSecured) {
        url = pUrl;
        confidence = pConfidence;
        secured = pSecured;
    }

    /**
     * Get the details as JSON Object
     *
     * @return
     */
    public JSONObject toJSONObject() {
        JSONObject resp = new JSONObject();
        add(resp,Payload.URL,url);
        if (confidence != 0) {
            add(resp, Payload.CONFIDENCE,confidence);
        }
        if (secured != null) {
            add(resp, Payload.SECURED, secured);
        }
        add(resp, Payload.SERVER_VENDOR, serverVendor);
        add(resp, Payload.SERVER_PRODUCT, serverProduct);
        add(resp, Payload.SERVER_VERSION, serverVersion);
        add(resp, Payload.VERSION, version);

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
