package org.jolokia.discovery;


import java.io.UnsupportedEncodingException;

import org.json.simple.JSONObject;

/**
 * A Jolokia discover message which can be either a request
 * or a response.
 *
 * @author roland
 * @since 27.01.14
 */
abstract class AbstractDiscoveryMessage {

    // Maximum size supported for an UDP discovery message
    public static final int MAX_MSG_SIZE = 8972;

    // Type of the message
    private MessageType type;

    // Payload of the message
    private AgentDetails agentDetails;

    protected final void setType(MessageType pType) {
        type = pType;
    }

    protected final void setAgentDetails(AgentDetails pAgentDetails) {
        agentDetails = pAgentDetails;
    }

    public boolean isQuery() {
        return type == MessageType.QUERY;
    }

    public boolean isResponse() {
        return type == MessageType.RESPONSE;
    }

    public byte[] getData() {
        JSONObject respond = new JSONObject();
        respond.put(Payload.TYPE.asKey(), type.toString().toLowerCase());
        if (agentDetails != null) {
            respond.putAll(agentDetails.toJSONObject());
        }
        byte[] ret = getBytes(respond.toJSONString());
        if (ret.length > MAX_MSG_SIZE) {
            throw new IllegalArgumentException("Message to send is larger (" + ret.length + " bytes) than maximum size of " + MAX_MSG_SIZE + " bytes.");
        }
        return ret;
    }

    public AgentDetails getAgentDetails() {
        return agentDetails;
    }

    protected byte[] getBytes(String pRespond) {
        try {
            return pRespond.getBytes("UTF8");
        } catch (UnsupportedEncodingException e) {
            return pRespond.getBytes();
        }
    }

    /**
     * Enum holding the possible values for the discovery request/response. Note that the
     * name of the enum is used literally in the message and must not be changed.
     */
    public enum Payload {
            // Type of request (see Message type)
            TYPE,
            // Agent URL as the agent sees itself
            URL,
            // Whether the agent is secured and an authentication is required (0,1). If not given, this info is not known
            SECURED,
            // Vendor of the detected container
            SERVER_VENDOR,
            // The product in which the agent is running
            SERVER_PRODUCT,
            // Version of the server
            SERVER_VERSION,
            // Version of the agent
            AGENT_VERSION,
            // The agent id
            AGENT_ID,
            // Description of the agent (if any)
            AGENT_DESCRIPTION;

            String asKey() {
                return this.name().toLowerCase();
            }

            public static Payload fromKey(String pKey) {
                return Payload.valueOf(pKey.toUpperCase());
            }
    }

    /**
     * Type of message. The constant names are used as type value for the payload
     */
    enum MessageType {
        // Discovery query
        QUERY,
        // Response to a discovery query
        RESPONSE
    }

    @Override
    public String toString() {
        return "{" +
               "type=" + type +
               ", agentDetails=" + agentDetails +
               '}';
    }
}
