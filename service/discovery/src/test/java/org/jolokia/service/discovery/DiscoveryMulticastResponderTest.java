package org.jolokia.service.discovery;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.service.api.AgentDetails;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.NetworkUtil;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.json.simple.JSONObject;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * @author roland
 * @since 04.02.14
 */
public class DiscoveryMulticastResponderTest {

    @Test
    public void enabledLookup() throws IOException, InterruptedException {
        lookup(true);
    }

    @Test
    public void disabledLookup() throws IOException, InterruptedException {
        lookup(false);
    }

    private void lookup(boolean enabled) throws IOException, InterruptedException {
        if (!NetworkUtil.isMulticastSupported()) {
            throw new SkipException("No multicast interface found, skipping test ");
        }
        String id = UUID.randomUUID().toString();
        JolokiaContext context = new TestJolokiaContext.Builder()
                .config(ConfigKey.DISCOVERY_ENABLED,Boolean.toString(enabled))
                .agentDetails(new AgentDetails(id))
                .build();
        DiscoveryMulticastResponder responder = new DiscoveryMulticastResponder();
        responder.init(context);
        // Warming up
        Thread.sleep(enabled ? 300 : 100);
        JolokiaDiscovery discovery = new JolokiaDiscovery("test");
        List<JSONObject> msgs = discovery.lookupAgents();
        if (enabled) {
            Assert.assertTrue(msgs.size() > 0);
        } else {
            for (JSONObject resp : msgs) {
                Assert.assertNotEquals(resp.get("agent_id"),id);
            }
        }
        responder.destroy();
    }


}
