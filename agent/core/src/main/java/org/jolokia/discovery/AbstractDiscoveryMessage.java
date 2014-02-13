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

    // Id of this message
    private String id;

    // Payload of the message
    private AgentDetails agentDetails;

    protected final void setType(MessageType pType) {
        type = pType;
    }

    protected final void setId(String pId) {
        id = pId;
    }

    public String getId() {
        return id;
    }

    protected final void setAgentDetails(AgentDetails pAgentDetails) {
        agentDetails = pAgentDetails;
    }

    public boolean isQuery() {
        return type == MessageType.QUERY;
    }

    public byte[] getData() {
        JSONObject respond = new JSONObject();
        respond.put(Payload.TYPE.asKey(), type.toString().toLowerCase());
        respond.put(Payload.ID.asKey(),id);
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
            // Message ID
            ID,
            // Agent URL as the agent sees itself
            URL,
            // How accurate it the URL ? (100: Sure that URL is ok, 50: 50% sure). That's an heuristic value
            CONFIDENCE,
            // Whether the agent is secured and an authentication is required (0,1). If not given, this info is not known
            SECURED,
            // Vendor of the detected container
            SERVER_VENDOR,
            // The product in which the agent is running
            SERVER_PRODUCT,
            // Version of the server
            SERVER_VERSION,
            // Agent version
            VERSION;

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
               ",id=" + id +
               ", agentDetails=" + agentDetails +
               '}';
    }
}
