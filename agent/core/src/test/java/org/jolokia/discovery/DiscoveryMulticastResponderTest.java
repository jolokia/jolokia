package org.jolokia.discovery;

import java.io.IOException;
import java.util.List;

import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.util.*;
import org.json.simple.JSONObject;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 04.02.14
 */
public class DiscoveryMulticastResponderTest {

    @Test
    public void simple() throws IOException, InterruptedException {
        AgentDetailsHolder holder = new TestAgentsDetailsHolder();
        DiscoveryMulticastResponder responder =
                new DiscoveryMulticastResponder(NetworkUtil.getLocalAddress(),holder,new AllowAllRestrictor(),new LogHandler.StdoutLogHandler(true));
        responder.start();
        // Warming up
        Thread.sleep(500);
        JolokiaDiscovery discovery = new JolokiaDiscovery();
        List<JSONObject> msgs = discovery.lookupAgents();
        assertTrue(msgs.size() > 0);
        responder.stop();
    }

    private class TestAgentsDetailsHolder implements AgentDetailsHolder {

        AgentDetails details = new AgentDetails();

        public AgentDetails getAgentDetails() {
            return details;
        }
    }
}
