package org.jolokia.agent.service.discovery;

import java.io.IOException;
import java.net.SocketException;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import org.jolokia.core.Version;
import org.jolokia.core.service.AgentDetails;
import org.jolokia.core.service.JolokiaContext;
import org.jolokia.core.util.*;
import org.json.simple.JSONObject;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import static org.jolokia.agent.service.discovery.AbstractDiscoveryMessage.MessageType.QUERY;
import static org.jolokia.agent.service.discovery.MulticastUtil.sendQueryAndCollectAnswers;

/**
 * @author roland
 * @since 27.01.14
 */

public class MulticastSocketListenerThreadTest {

    public static final String JOLOKIA_URL = "http://localhost:8080/jolokia";
    private URL url;
    private String id;


    private MulticastSocketListenerThread startSocketListener() throws IOException, InterruptedException {
        url = new URL(JOLOKIA_URL);
        id = UUID.randomUUID().toString();
        final AgentDetails details = new AgentDetails(id);
        details.updateAgentParameters(JOLOKIA_URL, false);
        details.setServerInfo("jolokia", "jolokia-test", "1.0");

        JolokiaContext context = new TestJolokiaContext.Builder().agentDetails(details).build();
        MulticastSocketListenerThread listenerThread = new MulticastSocketListenerThread(null,context);
        listenerThread.start();
        Thread.sleep(500);
        return listenerThread;
    }

    @Test
    public void simple() throws IOException, InterruptedException {
        checkForMulticastSupport();

        MulticastSocketListenerThread listenerThread = startSocketListener();

        try {
            DiscoveryOutgoingMessage out =
                    new DiscoveryOutgoingMessage.Builder(QUERY)
                            .agentId(UUID.randomUUID().toString())
                            .build();
            List<DiscoveryIncomingMessage> discovered = sendQueryAndCollectAnswers(out, 500, new LogHandler.StdoutLogHandler(true));
            int idCount = 0;
            int urlCount = 0;
            for (DiscoveryIncomingMessage in : discovered) {
                AgentDetails agentDetails = in.getAgentDetails();
                if (agentDetails.getAgentId().equals(id)) {
                    idCount++;
                }
                if (JOLOKIA_URL.equals(in.getAgentDetails().toJSONObject().get("url"))) {
                    urlCount++;
                }
                Assert.assertFalse(in.isQuery());
                JSONObject details = agentDetails.toJSONObject();
                if (details.get("server_vendor") != null && details.get("server_vendor").equals("jolokia")) {
                    Assert.assertEquals(details.get("url"), JOLOKIA_URL);
                    Assert.assertEquals(details.get("agent_version"), Version.getAgentVersion());
                    return;
                }
            }
            Assert.assertEquals(idCount, 1, "Exactly one in message with the send id should have been received");
            Assert.assertEquals(urlCount, 1, "Only one message with the url should be included");
            Assert.fail("No message found");
        } finally {
            listenerThread.shutdown();
        }
    }

    private void checkForMulticastSupport() throws SocketException {
        if (!NetworkUtil.isMulticastSupported()) {
            throw new SkipException("No multicast supported");
        }
    }
}
