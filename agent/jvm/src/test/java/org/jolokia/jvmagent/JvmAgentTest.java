package org.jolokia.jvmagent;

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

import java.io.IOException;
import java.util.List;

import org.jolokia.discovery.AgentDetails;
import org.jolokia.discovery.file.FileDiscovery;
import org.jolokia.test.util.EnvTestUtil;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 28.09.11
 */
public class JvmAgentTest {

    @Test
    public void premain() throws IOException {
        JvmAgent.premain("port=" + EnvTestUtil.getFreePort());
        JvmAgent.agentmain("mode=stop");
    }

    @Test
    public void agentmain() throws IOException {
        JvmAgent.agentmain("mode=start,name=Test Jolokia Agent,port=" + EnvTestUtil.getFreePort());

        // now we should be able to discover the available agent URLs
        FileDiscovery discovery = FileDiscovery.getInstance();
        assertNotNull(discovery, "should have found an discovery");
        List<AgentDetails> agents = discovery.findAgents();
        assertTrue(agents.size() > 0, "Should have found at least one agent");
        System.out.println("Found agents: " + agents);

        JvmAgent.agentmain("mode=stop");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // ignore
        }
        System.out.println("Now has agents: " + discovery.findAgents());
    }

    @Test
    public void startException() throws IOException {
        int port = EnvTestUtil.getFreePort();
        JvmAgent.agentmain("port=" + port);
        try {
            JvmAgent.agentmain("port=" + port);
        } finally {
         JvmAgent.agentmain("mode=stop");
        }
    }

}
