package org.jolokia.server.core.service.api;/*
 *
 * Copyright 2015 Roland Huss
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

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Map;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.Configuration;
import org.jolokia.server.core.config.StaticConfiguration;
import org.jolokia.server.core.detector.DefaultServerHandle;
import org.jolokia.server.core.util.NetworkUtil;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 06/10/15
 */
public class AgentDetailsTest {

    @Test
    public void agentIdHost() throws SocketException, UnknownHostException {
        Configuration myConfig = new StaticConfiguration(ConfigKey.AGENT_ID, "${host}");
        AgentDetails details = new AgentDetails(myConfig,new DefaultServerHandle(null,null,null));
        assertEquals(details.getAgentId(), NetworkUtil.getLocalAddress().getHostName());
    }

    @Test
    public void agentIdIp() throws SocketException, UnknownHostException {
        Configuration myConfig = new StaticConfiguration(ConfigKey.AGENT_ID, "${ip}");
        AgentDetails details = new AgentDetails(myConfig,new DefaultServerHandle(null,null,null));
        assertEquals(details.getAgentId(), NetworkUtil.getLocalAddress().getHostAddress());
    }

    @Test
    public void agentIdSystemProperty()  {
        System.setProperty("agentIdSystemProperty","test1234");
        Configuration myConfig = new StaticConfiguration(ConfigKey.AGENT_ID, "${prop:agentIdSystemProperty}");
        AgentDetails details = new AgentDetails(myConfig,new DefaultServerHandle(null,null,null));
        assertEquals(details.getAgentId(), "test1234");
        System.clearProperty("agentIdSystemProperty");
    }

    @Test(enabled = false) // first env var could contain illegal characters ....
    public void agentIdEnvironmentVariable() {
        Map<String, String> env = System.getenv();
        Map.Entry<String, String> entry = env.entrySet().iterator().next();
        Configuration myConfig = new StaticConfiguration(ConfigKey.AGENT_ID, "${env:"+entry.getKey()+"}");
        AgentDetails details = new AgentDetails(myConfig,null);
        assertEquals(details.getAgentId(), entry.getValue());
    }
}
