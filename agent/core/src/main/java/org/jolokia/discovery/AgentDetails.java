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

    public void setUrl(String pUrl) {
        url = pUrl;
    }

    public void setConfidence(int pConfidence) {
        confidence = pConfidence;
    }

    public void setSecured(Boolean pSecured) {
        secured = pSecured;
    }

    public void setVersion(String  pVersion) {
        version = pVersion;
    }

    public String toMessagePayload() {
        StringBuffer resp = new StringBuffer();
        append(resp, Payload.URL,url);
        if (confidence != 0) {
            append(resp, Payload.CONFIDENCE,"" + confidence);
        }
        if (secured != null) {
            append(resp, Payload.SECURED,secured.toString());
        }
        append(resp,Payload.SERVER_VENDOR,serverVendor);
        append(resp,Payload.SERVER_PRODUCT,serverProduct);
        append(resp,Payload.SERVER_VERSION,serverVendor);
        append(resp,Payload.VERSION, version);
        return resp.toString();
    }

    private void append(StringBuffer buf, Payload pPayload, String pVal) {
        if (pVal != null) {
            buf.append(pPayload.toString().toLowerCase()).append(":").append(pVal).append("\n");
        }
    }

    public JSONObject asJson() {
        JSONObject resp = new JSONObject();
        add(resp,Payload.URL,url);
        if (confidence != 0) {
            add(resp, Payload.CONFIDENCE, "" + confidence);
        }
        if (secured != null) {
            add(resp, Payload.SECURED, secured.toString());
        }
        add(resp, Payload.SERVER_VENDOR, serverVendor);
        add(resp, Payload.SERVER_PRODUCT, serverProduct);
        add(resp, Payload.SERVER_VERSION, serverVendor);
        add(resp, Payload.VERSION, version);

        return resp;
    }

    private void add(JSONObject pResp, Payload pKey, String pValue) {
        if (pValue != null) {
            pResp.put(pKey.toString().toLowerCase(),pValue);
        }
    }
}
