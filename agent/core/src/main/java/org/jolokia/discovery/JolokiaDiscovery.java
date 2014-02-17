package org.jolokia.discovery;

import java.io.IOException;
import java.util.List;

import org.jolokia.util.LogHandler;
import org.json.simple.JSONArray;

import static org.jolokia.discovery.AbstractDiscoveryMessage.MessageType.QUERY;

/**
 * Discover Jolokia agents via multicast
 *
 * @author roland
 * @since 31.01.14
 */
public class JolokiaDiscovery implements JolokiaDiscoveryMBean {

    /** {@inheritDoc} */
    public List lookupAgents() throws IOException {
        return lookupAgentsWithTimeout(1000);
    }

    /** {@inheritDoc} */
    public List lookupAgentsWithTimeout(int pTimeout) throws IOException {
        DiscoveryOutgoingMessage out =
                new DiscoveryOutgoingMessage.Builder(QUERY)
                        .build();
        List<DiscoveryIncomingMessage> discovered = MulticastUtil.sendQueryAndCollectAnswers(out, pTimeout, LogHandler.QUIET);
        JSONArray ret = new JSONArray();
        for (DiscoveryIncomingMessage in : discovered) {
            ret.add(in.getAgentDetails().toJSONObject());
        }
        return ret;
    }
}
