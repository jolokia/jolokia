package org.jolokia.discovery;

import java.io.IOException;
import java.net.SocketException;
import java.net.URL;
import java.util.List;
import java.util.UUID;

import org.jolokia.Version;
import org.jolokia.service.JolokiaContext;
import org.jolokia.util.*;
import org.json.simple.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.Test;

import static org.jolokia.discovery.AbstractDiscoveryMessage.MessageType.QUERY;
import static org.jolokia.discovery.MulticastUtil.sendQueryAndCollectAnswers;
import static org.testng.Assert.*;

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
                assertFalse(in.isQuery());
                JSONObject details = agentDetails.toJSONObject();
                if (details.get("server_vendor") != null && details.get("server_vendor").equals("jolokia")) {
                    assertEquals(details.get("url"), JOLOKIA_URL);
                    assertEquals(details.get("agent_version"), Version.getAgentVersion());
                    return;
                }
            }
            assertEquals(idCount,1,"Exactly one in message with the send id should have been received");
            assertEquals(urlCount,1,"Only one message with the url should be included");
            fail("No message found");
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
