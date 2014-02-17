package org.jolokia.discovery;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.jolokia.service.JolokiaContext;
import org.jolokia.util.NetworkUtil;
import org.jolokia.util.TestJolokiaContext;
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
        if (!NetworkUtil.isMulticastSupported()) {
            throw new SkipException("No multicast interface found, skipping test ");
        }
        JolokiaContext context = new TestJolokiaContext.Builder().agentDetails(new AgentDetails(UUID.randomUUID().toString())).build();
        DiscoveryMulticastResponder responder =
                new DiscoveryMulticastResponder(context);
        responder.start();
        // Warming up
        Thread.sleep(500);
        JolokiaDiscovery discovery = new JolokiaDiscovery();
        List<JSONObject> msgs = discovery.lookupAgents();
        assertTrue(msgs.size() > 0);
        responder.stop();
    }
}
