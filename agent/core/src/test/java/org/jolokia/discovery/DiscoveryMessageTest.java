package org.jolokia.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Arrays;

import org.json.simple.JSONObject;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 04.02.14
 */
public class DiscoveryMessageTest {

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*maximum.*" + AbstractDiscoveryMessage.MAX_MSG_SIZE + ".*")
    public void messageTooBig() {
        AbstractDiscoveryMessage msg = new AbstractDiscoveryMessage() {};
        msg.setType(AbstractDiscoveryMessage.MessageType.QUERY);
        msg.setAgentDetails(getAgentDetailsLargerThan(AbstractDiscoveryMessage.MAX_MSG_SIZE));
        msg.getData();
    }

    @Test
    public void incomingIgnoredPayload() throws IOException {
        DiscoveryIncomingMessage in = new DiscoveryIncomingMessage(createDatagramPacket(
                "type", AbstractDiscoveryMessage.MessageType.RESPONSE.toString().toLowerCase(),
                "blubber","bla",
                "url","http://localhost:8080/jolokia",
                "agent_id","test",
                "secured",false));
        String inS = in.toString();
        assertFalse(inS.contains("blubber"));
        assertTrue(inS.contains("url"));

        AgentDetails details = in.getAgentDetails();
        JSONObject json = details.toJSONObject();
        assertEquals(json.get("secured"),false);
        assertEquals(json.get("url"),"http://localhost:8080/jolokia");
        assertNull(json.get("blubber"));
    }

    @Test(expectedExceptions = IOException.class,expectedExceptionsMessageRegExp = ".*type.*")
    public void incomingNoType() throws IOException {
        new DiscoveryIncomingMessage(createDatagramPacket());
    }

    @Test(expectedExceptions = IOException.class,expectedExceptionsMessageRegExp = ".*type.*bla.*")
    public void incomingWrongType() throws IOException {
        new DiscoveryIncomingMessage(createDatagramPacket("type","bla"));
    }

    @Test(expectedExceptions = IOException.class,expectedExceptionsMessageRegExp = ".*not.*parse.*")
    public void incomingWithLargerBuf() throws IOException {
        JSONObject data = new JSONObject();
        data.put("type", AbstractDiscoveryMessage.MessageType.QUERY.toString());
        String json = data.toJSONString();
        byte[] largeBuf = Arrays.copyOf(json.getBytes(),json.length() + 512);
        DiscoveryIncomingMessage in = new DiscoveryIncomingMessage(new DatagramPacket(largeBuf,largeBuf.length));

    }

    public DatagramPacket createDatagramPacket(Object ... vals) {
        JSONObject data = new JSONObject();
        for (int i = 0; i < vals.length; i+=2) {
            data.put(vals[i],vals[i+1]);
        }
        String json = data.toJSONString();
        return new DatagramPacket(json.getBytes(),json.getBytes().length);
    }


    private AgentDetails getAgentDetailsLargerThan(int size) {
        AgentDetails details = new AgentDetails("test-id");
        details.setServerInfo("test","test","test");
        StringBuffer large = new StringBuffer();
        for (int i = 0; i < size; i++) {
            large.append("Y");
        }
        details.setUrl(large.toString());
        details.setSecured(true);
        return details;
    }
}
