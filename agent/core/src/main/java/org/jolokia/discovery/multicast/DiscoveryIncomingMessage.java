package org.jolokia.discovery.multicast;

import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author roland
 * @since 27.01.14
 */
public class DiscoveryIncomingMessage extends AbstractDiscoveryMessage {

    // UDP packet received (if incoming)
    private DatagramPacket packet;

    /**
     * Parse a message from a datagram packet.
     *
     * @param packet packet received
     * @throws IOException if reading/parsing failed.
     */
    public DiscoveryIncomingMessage(DatagramPacket pPacket) throws IOException {
        packet = pPacket;
        Map<Payload, String> msgData = parseData(packet.getData(), packet.getLength());
        String typeS = msgData.remove(Payload.TYPE);
        if (typeS == null) {
            throw new IOException("No type given in request");
        }
        setType(AbstractDiscoveryMessage.MessageType.valueOf(typeS.toUpperCase()));
        setPayload(msgData);
    }

    private Map<Payload, String> parseData(byte[] pData, int pLength) throws IOException {
        ByteArrayInputStream is = new ByteArrayInputStream(pData,0,pLength);
        LineNumberReader reader = new LineNumberReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        String line;
        Map<Payload,String> ret = new HashMap<Payload, String>();
        while ( (line = reader.readLine()) != null) {
            String parts[] = line.split(":",2);
            if (parts.length != 2) {
                throw new IOException("Cannot parse line " + line + " since it doesn't contains a ':' separator");
            }
            ret.put(Payload.valueOf(parts[0].toUpperCase()),parts[1]);
        }
        return ret;
    }

    public InetAddress getSourceAddress() {
        return packet.getAddress();
    }

    public int getSourcePort() {
        return packet.getPort();
    }

    @Override
    public String toString() {
        return "JolokiaDiscoveryIncomingMessage{" +
               "source = " + getSourceAddress() + ":" + getSourcePort() + ", " +
                "type = " + type + ", " +
                "payload = " + payload + ", " +
                "url = " + payload.get(Payload.URL) +
               '}';
    }
}
