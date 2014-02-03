package org.jolokia.discovery;

import java.io.IOException;
import java.util.List;

import org.json.simple.JSONArray;

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
                new DiscoveryOutgoingMessage.Builder(AbstractDiscoveryMessage.MessageType.QUERY)
                        .build();
        List<DiscoveryIncomingMessage> discovered = MulticastUtil.sendQueryAndCollectAnswers(out);
        JSONArray ret = new JSONArray();
        for (DiscoveryIncomingMessage in : discovered) {
            ret.add(in.getAgentDetails().toJSONObject());
        }
        return ret;
    }
}
