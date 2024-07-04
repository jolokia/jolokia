package org.jolokia.service.discovery;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Arrays;

import org.jolokia.server.core.service.api.AgentDetails;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

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
        Assert.assertFalse(inS.contains("blubber"));
        Assert.assertTrue(inS.contains("url"));

        AgentDetails details = in.getAgentDetails();
        JSONObject json = details.toJSONObject();
        Assert.assertEquals(json.get("secured"), false);
        Assert.assertEquals(json.get("url"), "http://localhost:8080/jolokia");
        Assert.assertNull(json.opt("blubber"));
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
    @Ignore("It just returns after getting to last closing \"}\"")
    public void incomingWithLargerBuf() throws IOException {
        JSONObject data = new JSONObject();
        data.put("type", AbstractDiscoveryMessage.MessageType.QUERY.toString());
        String json = data.toString();
        byte[] largeBuf = Arrays.copyOf(json.getBytes(),json.length() + 512);
        DiscoveryIncomingMessage in = new DiscoveryIncomingMessage(new DatagramPacket(largeBuf,largeBuf.length));
    }

    public DatagramPacket createDatagramPacket(Object ... vals) {
        JSONObject data = new JSONObject();
        for (int i = 0; i < vals.length; i+=2) {
            data.put(vals[i].toString(), vals[i+1]);
        }
        String json = data.toString();
        return new DatagramPacket(json.getBytes(),json.getBytes().length);
    }


    private AgentDetails getAgentDetailsLargerThan(int size) {
        AgentDetails details = new AgentDetails("test-id");
        String large = "Y".repeat(Math.max(0, size));
        details.setUrl(large);
        details.setSecured(true);
        return details;
    }
}
