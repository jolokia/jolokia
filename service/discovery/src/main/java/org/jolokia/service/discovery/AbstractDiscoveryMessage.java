package org.jolokia.service.discovery;


import java.io.UnsupportedEncodingException;

import org.jolokia.server.core.service.AgentDetails;
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

    // Key for specifying the type of a message
    protected static final String MESSAGE_TYPE = "type";

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
        respond.put(MESSAGE_TYPE, type.toString().toLowerCase());
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
