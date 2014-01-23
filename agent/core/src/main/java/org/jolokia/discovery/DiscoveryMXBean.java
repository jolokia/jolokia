/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jolokia.discovery;

import java.util.List;

/**
 * Provides the common API to all discovery mbeans so they work consistently across JMX
 */
public interface DiscoveryMXBean {

    /**
     * Uses the underlying discovery mechanism (e.g. files on the file system or ZeroConf etc) to discover
     * all the Jolokia Agents that can be found
     *
     * @return the found details (name and location URL) of the found jolokia agents
     */
    List<AgentDetails> findAgents();
}
