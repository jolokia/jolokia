package org.jolokia.discovery;

import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * @author roland
 * @since 27.01.14
 */
public class DiscoveryIncomingMessage extends AbstractDiscoveryMessage {

    private InetAddress sourceAddress;
    private int sourcePort;

    /**
     * Parse a message from a datagram packet.
     *
     * @param packet packet received
     * @throws IOException if reading/parsing failed.
     */
    public DiscoveryIncomingMessage(DatagramPacket pPacket) throws IOException {
        sourceAddress = pPacket.getAddress();
        sourcePort = pPacket.getPort();

        Map<Payload,Object> inData = parseData(pPacket.getData(), pPacket.getLength());
        String typeS = (String) inData.remove(Payload.TYPE);
        if (typeS == null) {
            throw new IOException("No message type given in discovery message " + inData);
        }
        try {
            MessageType type = MessageType.valueOf(typeS.toUpperCase());
            setType(type);
        } catch (IllegalArgumentException exp) {
            throw new IOException("Invalid type " + typeS + " given in discovery message");
        }
        setAgentDetails(new AgentDetails(inData));
    }

    public InetAddress getSourceAddress() {
        return sourceAddress;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    @Override
    public String toString() {
        return "JolokiaDiscoveryIncomingMessage{" +
               "source = " + getSourceAddress() + ":" + getSourcePort() + ", " +
                "type = " + type + ", " +
                "details = " + agentDetails +
               '}';
    }

    public static Map<Payload,Object> parseData(byte[] pData, int pLength) throws IOException {
        JSONParser parser = new JSONParser();
        ByteArrayInputStream is = new ByteArrayInputStream(pData,0,pLength);
        try {
            JSONObject inMsg = (JSONObject) parser.parse(new InputStreamReader(is, "UTF-8"));
            Map<Payload, Object> data = new HashMap<Payload, Object>();
            for (Object key : inMsg.keySet()) {
                try {
                    data.put(Payload.valueOf(key.toString().toUpperCase()), inMsg.get(key));
                } catch (IllegalArgumentException exp) {
                    // We simply ignore key which are unknown
                }
            }
            return data;
        } catch (ParseException e) {
            throw new IOException("Cannot parse discovery message as JSON",e);
        }
    }

}
