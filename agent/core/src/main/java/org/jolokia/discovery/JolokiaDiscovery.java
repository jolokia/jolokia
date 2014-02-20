package org.jolokia.discovery;

import java.io.IOException;
import java.util.List;

import javax.management.JMException;

import org.jolokia.service.*;
import org.jolokia.util.LogHandler;
import org.json.simple.JSONArray;

import static org.jolokia.discovery.AbstractDiscoveryMessage.MessageType.QUERY;

/**
 * Discover Jolokia agents via multicast
 *
 * @author roland
 * @since 31.01.14
 */
public class JolokiaDiscovery extends AbstractJolokiaService<JolokiaService.Init>
        implements JolokiaDiscoveryMBean, JolokiaService.Init {

    // Agent id used for use in the query
    private String agentId = null;

    // Used for logging when doing lookups
    private LogHandler logHandler = LogHandler.QUIET;

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
     * @param pAgentId agent id to be used for correlating messages
     */
    public JolokiaDiscovery(String pAgentId) {
        super(JolokiaService.Init.class, 0);
        agentId = pAgentId;
    }

    @Override
    public void init(JolokiaContext pJolokiaContext) {
        super.init(pJolokiaContext);
        logHandler = pJolokiaContext;
        AgentDetails details = pJolokiaContext.getAgentDetails();
        agentId = details.getAgentId();
        String objectName = JolokiaDiscoveryMBean.OBJECT_NAME + ",agent=" + agentId;
        try {
            pJolokiaContext.registerMBean(this,objectName);
        } catch (JMException e) {
            throw new IllegalArgumentException("Cannot register MBean " + objectName + " as notification pull store: " + e,e);
        }
        logHandler = pJolokiaContext;
    }

    /** {@inheritDoc} */
    public List lookupAgents() throws IOException {
        return lookupAgentsWithTimeout(1000);
    }

    /** {@inheritDoc} */
    public List lookupAgentsWithTimeout(int pTimeout) throws IOException {
        DiscoveryOutgoingMessage out =
                new DiscoveryOutgoingMessage.Builder(QUERY)
                        .agentId(agentId)
                        .build();
        List<DiscoveryIncomingMessage> discovered = MulticastUtil.sendQueryAndCollectAnswers(out, pTimeout, logHandler);
        JSONArray ret = new JSONArray();
        for (DiscoveryIncomingMessage in : discovered) {
            ret.add(in.getAgentDetails().toJSONObject());
        }
        return ret;
    }
}
