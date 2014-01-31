package org.jolokia.discovery;

import java.nio.charset.StandardCharsets;

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
    protected MessageType type;

    // Payload of the message
    protected AgentDetails agentDetails;

    final protected void setType(MessageType pType) {
        type = pType;
    }

    final protected void setAgentDetails(AgentDetails pAgentDetails) {
        agentDetails = pAgentDetails;
    }

    public boolean isQuery() {
        return type == MessageType.QUERY;
    }

    public byte[] getData() {
        StringBuilder respond = new StringBuilder();
        respond.append("type:").append(type.toString().toLowerCase()).append("\n");
        if (agentDetails != null) {
            respond.append(agentDetails.toMessagePayload());
        }
        byte[] ret = respond.toString().getBytes(StandardCharsets.UTF_8);
        if (ret.length > MAX_MSG_SIZE) {
            throw new IllegalArgumentException("Message to send is longer than maximum size of " + MAX_MSG_SIZE + " bytes.");
        }
        return ret;
    }

    public AgentDetails getAgentDetails() {
        return agentDetails;
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

    /**
     * Enum holding the possible values for the discovery request/response. Note that the
     * name of the enum is used literally in the message and must not be changed.
     */
    public enum Payload {
        // Type of request (see Message type)
        TYPE,
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
        VERSION
    }
}
