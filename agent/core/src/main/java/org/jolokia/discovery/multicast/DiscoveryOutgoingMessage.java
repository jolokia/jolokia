package org.jolokia.discovery.multicast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.jolokia.Version;
import org.jolokia.detector.ServerHandle;

import static org.jolokia.discovery.multicast.AbstractDiscoveryMessage.Payload.*;

/**
 * Class representing an outgoing message
 *
 * @author roland
 * @since 27.01.14
 */
public class DiscoveryOutgoingMessage extends AbstractDiscoveryMessage {

    private final InetAddress targetAddress;
    private final int targetPort;

    private DiscoveryOutgoingMessage(MessageType pType,
                                     InetAddress pTargetAddress,
                                     int pTargetPort,
                                     Map<Payload, String> pPayload) {
        this.targetAddress = pTargetAddress;
        this.targetPort = pTargetPort;
        setType(pType);
        setPayload(pPayload);
    }

    public InetAddress getTargetAddress() {
        return targetAddress;
    }

    public int getTargetPort() {
        return targetPort;
    }

    public DatagramPacket getDatagramPacket(InetAddress address, int port) {
        byte[] out = getData();
        return new DatagramPacket(out,out.length,address,port);
    }

    static public class Builder {

        private ServerHandle handle;
        private MessageType type;
        private InetAddress targetAddress;
        private int targetPort;

        public Builder(MessageType pType) {
            type = pType;
        }

        public Builder handle(ServerHandle pHandle) {
            this.handle  = pHandle;
            return this;
        }

        public Builder respondTo(DiscoveryIncomingMessage pMsg) {
            if (pMsg != null) {
                targetAddress = pMsg.getSourceAddress();
                targetPort = pMsg.getSourcePort();
            }
            return this;
        }

        public DiscoveryOutgoingMessage build() throws IOException {
            Map<Payload,String> payload = new HashMap<Payload, String>();
            if (handle != null) {
                payload.put(URL,handle.getAgentUrl().toString());
                payload.put(SERVER_VENDOR,handle.getVendor());
                payload.put(SERVER_VERSION,handle.getVersion());
                payload.put(VERSION, Version.getAgentVersion());
            }
            return new DiscoveryOutgoingMessage(
                    type,
                    targetAddress,
                    targetPort,
                    payload);
        }
    }
}
