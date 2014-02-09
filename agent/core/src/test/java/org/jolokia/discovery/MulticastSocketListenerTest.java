package org.jolokia.discovery;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import org.jolokia.Version;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.util.LogHandler;
import org.jolokia.util.NetworkUtil;
import org.json.simple.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.*;

import static org.jolokia.discovery.AbstractDiscoveryMessage.MessageType.QUERY;
import static org.jolokia.discovery.MulticastUtil.sendQueryAndCollectAnswers;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 27.01.14
 */

public class MulticastSocketListenerTest {

    public static final String JOLOKIA_URL = "http://localhost:8080/jolokia";
    URL url;
    private MulticastSocketListener listener;
    private Thread thread;

    @BeforeClass
    public void startSocketListener() throws IOException, InterruptedException {
        url = new URL(JOLOKIA_URL);
        final AgentDetails details = new AgentDetails();
        details.updateAgentParameters(JOLOKIA_URL, 100, false);
        details.setServerInfo("jolokia", "jolokia-test", "1.0");

        listener = new MulticastSocketListener(null,
                                               getAgentDetailsHolder(details),
                                               new AllowAllRestrictor(),
                                               new LogHandler.StdoutLogHandler(true));
        thread = new Thread(listener);
        thread.start();
        Thread.sleep(500);
    }


    private AgentDetailsHolder getAgentDetailsHolder(final AgentDetails pDetails) {
        return new AgentDetailsHolder() {
            public AgentDetails getAgentDetails() {
                return pDetails;
            }
        };
    }

    @AfterClass
    public void stopSocketListener() {
        listener.stop();
    }

    @Test
    public void simple() throws IOException {
        if (!NetworkUtil.isMulticastSupported()) {
            throw new SkipException("No multicast supported");
        }
        DiscoveryOutgoingMessage out =
                new DiscoveryOutgoingMessage.Builder(QUERY)
                .build();
        List<DiscoveryIncomingMessage> discovered = sendQueryAndCollectAnswers(out, 500, new LogHandler.StdoutLogHandler(true));
        for (DiscoveryIncomingMessage in : discovered) {
            assertFalse(in.isQuery());
            AgentDetails agentDetails = in.getAgentDetails();
            JSONObject details = agentDetails.toJSONObject();
            if (details.get("server_vendor") != null && details.get("server_vendor").equals("jolokia")) {
                assertEquals(details.get("url"), JOLOKIA_URL);
                assertEquals(details.get("version"), Version.getAgentVersion());
                return;
            }
        }
        fail("No message found");
    }
}
