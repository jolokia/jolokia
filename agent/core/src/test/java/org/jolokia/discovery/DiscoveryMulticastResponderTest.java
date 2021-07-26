package org.jolokia.discovery;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;

import org.jolokia.config.ConfigKey;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.util.*;
import org.json.simple.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 04.02.14
 */
public class DiscoveryMulticastResponderTest {

    @Test
    public void simple() throws IOException, InterruptedException {
        System.out.println("=================================================");
        if (!NetworkUtil.isMulticastSupported()) {
            throw new SkipException("No multicast interface found, skipping test ");
        }
        AgentDetailsHolder holder = new TestAgentsDetailsHolder();
        String multicastGroup = ConfigKey.MULTICAST_GROUP.getDefaultValue();
        int multicastPort = Integer.valueOf(ConfigKey.MULTICAST_PORT.getDefaultValue());
        DiscoveryMulticastResponder responder =
                new DiscoveryMulticastResponder(holder,new AllowAllRestrictor(),multicastGroup,multicastPort,new LogHandler.StdoutLogHandler(true));
        responder.start();
        // Warming up
        Thread.sleep(1000);
        JolokiaDiscovery discovery = new JolokiaDiscovery("test",new LogHandler.StdoutLogHandler(true));
        try {
            List<JSONObject> msgs = discovery.lookupAgents();
            System.out.println("=================================================");
            if (msgs.size() == 0) {
                // We are retrying it with a longer timeout
                System.out.println("No answer received, trying now with 30s timeout");
                msgs = discovery.lookupAgentsWithTimeoutAndMulticastAddress(30000,multicastGroup,multicastPort);
            }
            assertTrue(msgs.size() > 0);
        } catch (UnknownHostException exp) {
            throw new SkipException("Skipping test because no single multicast request could be send on any interface");
        } finally {
            responder.stop();
        }

    }

    private class TestAgentsDetailsHolder implements AgentDetailsHolder {

        AgentDetails details = new AgentDetails(UUID.randomUUID().toString());

        public AgentDetails getAgentDetails() {
            return details;
        }
    }
}
