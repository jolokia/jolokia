package org.jolokia.service.discovery;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.jolokia.server.core.Version;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.service.api.AgentDetails;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.impl.StdoutLogHandler;
import org.jolokia.server.core.util.NetworkUtil;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.json.JSONObject;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import static org.jolokia.service.discovery.AbstractDiscoveryMessage.MessageType.QUERY;
import static org.jolokia.service.discovery.MulticastUtil.sendQueryAndCollectAnswers;

/**
 * @author roland
 * @since 27.01.14
 */

public class MulticastSocketListenerThreadTest {

    public static final String JOLOKIA_URL = "http://localhost:8080/jolokia";

    private MulticastSocketListenerThread startSocketListener(String pId) throws IOException, InterruptedException {
        final AgentDetails details = new AgentDetails(pId);
        details.updateAgentParameters(JOLOKIA_URL, false);
        //details.setServerInfo("jolokia", "jolokia-test", "1.0");

        JolokiaContext context = new TestJolokiaContext.Builder().agentDetails(details).build();
        MulticastSocketListenerThread listenerThread = new MulticastSocketListenerThread("ListenerThread", null,context);
        listenerThread.start();
        Thread.sleep(500);
        return listenerThread;
    }

    @Test
    public void simple() throws IOException, InterruptedException {
        checkForMulticastSupport();

        String id = UUID.randomUUID().toString();
        MulticastSocketListenerThread listenerThread = startSocketListener(id);

        try {
            DiscoveryOutgoingMessage out =
                    new DiscoveryOutgoingMessage.Builder(QUERY)
                            .agentId(id)
                            .build();
            List<DiscoveryIncomingMessage> discovered =
                sendQueryAndCollectAnswers(out, 500,
                                           ConfigKey.MULTICAST_GROUP.getDefaultValue(),
                                           Integer.parseInt(ConfigKey.MULTICAST_PORT.getDefaultValue()),
                                           new StdoutLogHandler(true));
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
                if (id.equals(details.get("agent_id"))) {
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

    private void checkForMulticastSupport() {
        if (!NetworkUtil.isMulticastSupported()) {
            throw new SkipException("No multicast supported");
        }
    }
}
