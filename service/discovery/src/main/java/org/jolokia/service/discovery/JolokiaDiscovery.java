package org.jolokia.service.discovery;

import java.io.IOException;
import java.util.List;

import javax.management.ObjectName;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.service.api.*;
import org.jolokia.json.JSONArray;

import static org.jolokia.service.discovery.AbstractDiscoveryMessage.MessageType.QUERY;

/**
 * Discover Jolokia agents via multicast
 *
 * @author roland
 * @since 31.01.14
 */
public class JolokiaDiscovery extends AbstractJolokiaService<JolokiaService.Init>
        implements JolokiaDiscoveryMBean, JolokiaService.Init {

    // Name has we have been registered
    private ObjectName objectName;

    /**
     * Constructor to be called when called as a service
     * @param pOrder service order
     */
    public JolokiaDiscovery(int pOrder) {
        super(JolokiaService.Init.class,pOrder);
    }

    /**
     * Constructor called for programmatic lookup of the agent
     *
     */
    public JolokiaDiscovery() {
        super(JolokiaService.Init.class, 0);
    }

    @Override
    public void init(JolokiaContext pJolokiaContext) {
        super.init(pJolokiaContext);
        objectName = registerJolokiaMBean(JolokiaDiscovery.OBJECT_NAME,this);
    }

    @Override
    public void destroy() throws Exception {
        unregisterJolokiaMBean(objectName);
        super.destroy(); // Important, must be after any method using a JolokiaContext
    }

    /** {@inheritDoc} */
    @SuppressWarnings("rawtypes")
    public List lookupAgents() throws IOException {
        return lookupAgentsWithTimeout(1000);
    }

    /** {@inheritDoc} */
    @SuppressWarnings("rawtypes")
    public List lookupAgentsWithTimeout(int pTimeout) throws IOException {
        String group = getJolokiaContext().getConfig(ConfigKey.MULTICAST_GROUP, true);
        String port = getJolokiaContext().getConfig(ConfigKey.MULTICAST_PORT, true);
        return lookupAgentsWithTimeoutAndMulticastAddress(1000, group, Integer.parseInt(port));
    }

    /** {@inheritDoc} */
    @SuppressWarnings("rawtypes")
    public List lookupAgentsWithTimeoutAndMulticastAddress(int pTimeout, String pMulticastGroup, int pMulticastPort) throws IOException {
        JolokiaContext ctx = getJolokiaContext();
        DiscoveryOutgoingMessage out =
                new DiscoveryOutgoingMessage.Builder(QUERY)
                        .agentId(ctx.getAgentDetails().getAgentId())
                        .build();
        List<DiscoveryIncomingMessage> discovered = MulticastUtil.sendQueryAndCollectAnswers(out, pTimeout, pMulticastGroup, pMulticastPort, ctx);
        JSONArray ret = new JSONArray(discovered.size());
        for (DiscoveryIncomingMessage in : discovered) {
            ret.add(in.getAgentDetails().toJSONObject());
        }
        return ret;
    }
}
