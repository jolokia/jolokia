package org.jolokia.mule;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.IOException;
import java.net.*;

import org.jolokia.test.util.EnvTestUtil;
import org.mule.api.MuleException;
import org.mule.api.lifecycle.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 30.08.11
 */
public abstract class JolokiaMuleAgentTestCase {

    private JolokiaMuleAgent agent = null;
    protected abstract JolokiaMuleAgent createJolokiaMuleAgent();

    @BeforeMethod
    public void setup() {
        agent = createJolokiaMuleAgent();
    }

    @Test
    public void initialise() {
        agent.setDebug(true);
        agent.setPort(1811);
        agent.setDebugMaxEntries(1);
        agent.setHistoryMaxEntries(2);
        agent.setMaxCollectionSize(3);
        agent.setMaxDepth(4);
        agent.setMaxObjects(5);
        agent.setUser("roland");
        agent.setPassword("s!cr!t");

        assertEquals(agent.isDebug(),true);
        assertEquals(agent.getPort(),1811);
        assertEquals(agent.getDebugMaxEntries(),1);
        assertEquals(agent.getHistoryMaxEntries(),2);
        assertEquals(agent.getMaxCollectionSize(),3);
        assertEquals(agent.getMaxDepth(),4);
        assertEquals(agent.getMaxObjects(),5);
        assertEquals(agent.getUser(),"roland");
        assertEquals(agent.getPassword(),"s!cr!t");
    }

    @Test
    public void description() {
        agent.setPort(4711);
        String description = agent.getDescription();
        assertTrue(description.contains("4711"));
    }

    @Test(expectedExceptions = StartException.class)
    public void illegalStart() throws MuleException {
        agent.start();
    }

    @Test(expectedExceptions = StopException.class)
    public void illegalStop() throws MuleException {
        agent.stop();
    }

    @Test
    public void lifecycleHooks() throws InitialisationException {
        agent.registered();
        agent.initialise();
        agent.dispose();
        agent.unregistered();
    }

    @Test
    public void startStop() throws MuleException, IOException {
        agent.setPort(EnvTestUtil.getFreePort());
        agent.setUser("roland");
        agent.setPassword("s!cr!et");
        agent.initialise();
        agent.start();
        agent.stop();
    }

    @Test(expectedExceptions = StartException.class)
    public void startFailed() throws IOException, MuleException {
        int port = EnvTestUtil.getFreePort();
        // Block port now ...
        ServerSocket s = new ServerSocket();
        String host = "localhost";
        try {
            System.out.println("Port: " + port);

            s.bind(new InetSocketAddress(Inet4Address.getByName(host),port));

            agent.setPort(port);
            agent.setHost(host);
            agent.initialise();
            agent.start();
        } finally {
            s.close();
        }
    }


    @Test
    public void stop() {

    }
}
