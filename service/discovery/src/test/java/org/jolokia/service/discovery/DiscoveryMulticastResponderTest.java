package org.jolokia.service.discovery;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.jolokia.core.config.ConfigKey;
import org.jolokia.core.service.AgentDetails;
import org.jolokia.core.service.JolokiaContext;
import org.jolokia.core.util.NetworkUtil;
import org.jolokia.core.util.TestJolokiaContext;
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
        JolokiaContext context = new TestJolokiaContext.Builder()
                .config(ConfigKey.DISCOVERY_ENABLED,Boolean.toString(enabled))
                .agentDetails(new AgentDetails(UUID.randomUUID().toString()))
                .build();
        DiscoveryMulticastResponder responder =
                new DiscoveryMulticastResponder();
        responder.init(context);
        // Warming up
        Thread.sleep(enabled ? 300 : 100);
        JolokiaDiscovery discovery = new JolokiaDiscovery("test");
        List<JSONObject> msgs = discovery.lookupAgents();
        Assert.assertTrue(enabled ? msgs.size() > 0 : msgs.size() == 0);
        responder.destroy();
    }


}
