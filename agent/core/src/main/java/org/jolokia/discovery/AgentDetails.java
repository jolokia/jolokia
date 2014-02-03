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
    public AgentDetails(Map<Payload, String> pMsgData) {
        serverVendor = pMsgData.get(Payload.SERVER_VENDOR);
        serverProduct = pMsgData.get(Payload.SERVER_PRODUCT);
        serverVersion = pMsgData.get(Payload.SERVER_VERSION);
        url = pMsgData.get(Payload.URL);
        if (pMsgData.containsKey(Payload.CONFIDENCE)) {
            confidence = Integer.parseInt(pMsgData.get(Payload.CONFIDENCE));
        }
        if (pMsgData.containsKey(Payload.SECURED)) {
            secured = Boolean.parseBoolean(pMsgData.get(Payload.SECURED));
        }
        version = pMsgData.get(Payload.VERSION);
    }

    public void setServerInfo(String pVendor, String pProduct, String pVersion) {
        serverVendor = pVendor;
        serverProduct = pProduct;
        serverVersion = pVersion;
    }

    public void setConnectionParameters(String pUrl, int pConfidence, Boolean pSecured) {
        url = pUrl;
        confidence = pConfidence;
        secured = pSecured;
    }

    public String toMessagePayload() {
        Map<String,Object> payload = toJSONObject();
        StringBuffer resp = new StringBuffer();
        for (Map.Entry<String,Object> entry : payload.entrySet()) {
            resp.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
        }
        return resp.toString();
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

    // =======================================================================================

    private void add(JSONObject pResp, Payload pKey, Object pValue) {
        if (pValue != null) {
            pResp.put(pKey.toString().toLowerCase(),pValue);
        }
    }
}
