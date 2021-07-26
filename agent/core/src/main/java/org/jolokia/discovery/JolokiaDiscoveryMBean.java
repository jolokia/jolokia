package org.jolokia.discovery;

import java.io.IOException;
import java.util.List;

/**
 * MBean for looking up other agents
 * @author roland
 * @since 31.01.14
 */
public interface JolokiaDiscoveryMBean {

    String OBJECT_NAME = "jolokia:type=Discovery";

    /**
     * Lookup agents.
     *
     * @param pTimeout timeout for the lookup in milliseconds
     * @param pMulticastGroup multicast IPv4 address
     * @param pMulticastPort multicast port
     * @return an array with JSON objects containing the agent details discovered
     * @throws java.io.IOException
     */
    List lookupAgentsWithTimeoutAndMulticastAddress(int pTimeout, String pMulticastGroup, int pMulticastPort) throws IOException;

    /**
     * Lookup agents.
     *
     * @param pTimeout timeout for the lookup in milliseconds
     * @return an array with JSON objects containing the agent details discovered
     * @throws java.io.IOException
     */
    List lookupAgentsWithTimeout(int pTimeout) throws IOException;

    /**
     * Lookup agents with a timeout of 1 second
     *
     * @return an array with JSON objects containing the agent details discovered
     * @throws java.io.IOException
     */
    List lookupAgents() throws IOException;
}
