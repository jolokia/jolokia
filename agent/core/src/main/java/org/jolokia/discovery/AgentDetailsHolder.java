package org.jolokia.discovery;

/**
 * Holder which maintain the agent details for the current agent.
 *
 * @author roland
 * @since 31.01.14
 */
public interface AgentDetailsHolder {

    /**
     * Get the details which specify the current agent. The returned
     * details should not be kept but instead each time details are needed
     * this interface should be queried again.
     *
     * @return the details for this agent.
     */
    AgentDetails getAgentDetails();
}
