package org.jolokia.discovery;

import java.net.DatagramPacket;
import java.net.InetAddress;

/**
 * Class representing an outgoing message
 *
 * @author roland
 * @since 27.01.14
 */
public final class DiscoveryOutgoingMessage extends AbstractDiscoveryMessage {

    private final InetAddress targetAddress;
    private final int targetPort;

    private DiscoveryOutgoingMessage(MessageType pType,
                                     InetAddress pTargetAddress,
                                     int pTargetPort,
                                     AgentDetails pAgentDetails) {
        this.targetAddress = pTargetAddress;
        this.targetPort = pTargetPort;
        setType(pType);
        setAgentDetails(pAgentDetails);
    }

    public InetAddress getTargetAddress() {
        return targetAddress;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public DatagramPacket createDatagramPacket(InetAddress address, int port) {
        byte[] out = getData();
        return new DatagramPacket(out,out.length,address,port);
    }

    public static class Builder {

        private AgentDetails agentDetails;
        private MessageType type;
        private InetAddress targetAddress;
        private int targetPort;
        private String agentId;

        public Builder(MessageType pType) {
            type = pType;
        }

        public Builder agentDetails(AgentDetails pAgentDetails) {
            this.agentDetails = pAgentDetails;
            return this;
        }

        public Builder agentId(String pAgentId) {
            this.agentId = pAgentId;
            return this;
        }

        public Builder respondTo(DiscoveryIncomingMessage pMsg) {
            if (pMsg != null) {
                targetAddress = pMsg.getSourceAddress();
                targetPort = pMsg.getSourcePort();
            }
            return this;
        }


        public DiscoveryOutgoingMessage build() {
            return new DiscoveryOutgoingMessage(
                    type,
                    targetAddress,
                    targetPort,
                    agentDetails != null ? agentDetails : createAgentDetails());
        }

        private AgentDetails createAgentDetails() {
            AgentDetails ret = new AgentDetails(agentId);
            ret.seal();
            return ret;
        }

    }
}
