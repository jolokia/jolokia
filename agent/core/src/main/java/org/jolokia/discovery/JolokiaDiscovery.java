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
        DiscoveryOutgoingMessage out =
                new DiscoveryOutgoingMessage.Builder(QUERY)
                        .build();
        List<DiscoveryIncomingMessage> discovered = MulticastUtil.sendQueryAndCollectAnswers(out,LogHandler.QUIET,1000);
        JSONArray ret = new JSONArray();
        for (DiscoveryIncomingMessage in : discovered) {
            ret.add(in.getAgentDetails().toJSONObject());
        }
        return ret;
    }
}
