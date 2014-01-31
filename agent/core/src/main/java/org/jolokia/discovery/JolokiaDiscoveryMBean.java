package org.jolokia.discovery;

import java.io.IOException;

import org.json.simple.JSONArray;

/**
 * MBean for looking up other agents
 * @author roland
 * @since 31.01.14
 */
public interface JolokiaDiscoveryMBean {

    /**
     * Lookup agents.
     *
     * @return an array with JSON objects containing the agent details discovered
     */
    JSONArray lookupAgents() throws IOException;
}
