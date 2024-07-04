package org.jolokia.service.discovery;

import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.jolokia.server.core.service.api.AgentDetails;
import org.jolokia.server.core.util.JSONAware;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author roland
 * @since 27.01.14
 */
public class DiscoveryIncomingMessage extends AbstractDiscoveryMessage {

    private final InetAddress sourceAddress;
    private final int sourcePort;

    /**
     * Parse a message from a datagram packet.
     *
     * @param pPacket packet received
     * @throws IOException if reading/parsing failed.
     */
    public DiscoveryIncomingMessage(DatagramPacket pPacket) throws IOException {
        sourceAddress = pPacket.getAddress();
        sourcePort = pPacket.getPort();

        JSONObject data = parseData(pPacket.getData(), pPacket.getLength());
        initType(data);
        Map<AgentDetails.AgentDetailProperty,Object> inData = extractDetails(data);
        if (isResponse()) {
            setAgentDetails(new AgentDetails(inData));
        }
    }

    private void initType(JSONObject pData) throws IOException {
        String typeS = (String) pData.remove(MESSAGE_TYPE);
        if (typeS == null) {
            throw new IOException("No message type given in discovery message " + pData);
        }
        try {
            MessageType type = MessageType.valueOf(typeS.toUpperCase());
            setType(type);
        } catch (IllegalArgumentException exp) {
            throw new IOException("Invalid type " + typeS + " given in discovery message",exp);
        }
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
               "source = " + getSourceAddress() + ":" + getSourcePort() + ": " + super.toString() + "}";
    }

    private Map<AgentDetails.AgentDetailProperty,Object> extractDetails(JSONObject pData) {
        Map<AgentDetails.AgentDetailProperty, Object> data = new HashMap<>();
        for (Map.Entry<String, ?> entry : pData.toMap().entrySet()) {
            try {
                data.put(AgentDetails.AgentDetailProperty.fromKey(entry.getKey()), entry.getValue());
            } catch (IllegalArgumentException exp) {
                // We simply ignore key which are unknown
            }
        }
        return data;
    }

    private JSONObject parseData(byte[] pData, int pLength) throws IOException {
        ByteArrayInputStream is = new ByteArrayInputStream(pData,0,pLength);
        try {
            return JSONAware.parse(new InputStreamReader(is, StandardCharsets.UTF_8)).getObject();
        } catch (JSONException e) {
            throw new IOException("Cannot parse discovery message as JSON",e);
        }
    }

}
