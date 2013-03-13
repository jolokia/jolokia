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

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 13.08.11
 */
public class JvmAgentConfigTest {


    @Test
    public void simple() {
        JvmAgentConfig config = new JvmAgentConfig("port=4711,mode=stop");
        assertEquals(config.getPort(),4711);
        assertTrue(config.isModeStop());
        assertEquals(config.getBacklog(), 10);
    }

    @Test
    public void detectorArgs() {
        JvmAgentConfig config = new JvmAgentConfig("bootAmx=true");
        Configuration jConfig = config.getJolokiaConfig();
        String detectorOpts = jConfig.get(ConfigKey.DETECTOR_OPTIONS);
        assertEquals(detectorOpts.replaceAll("\\s*",""),"{\"glassfish\":{\"bootAmx\":true}}");
    }

    @Test
    public void defaults() throws UnknownHostException {
        JvmAgentConfig config = new JvmAgentConfig("");
        assertEquals(config.getAddress(), InetAddress.getByName(null));
        assertFalse(config.isModeStop());
        assertEquals(config.getProtocol(), "http");
        assertEquals(config.getPort(), 8778);
        assertNull(config.getUser());
        assertNull(config.getPassword());
        assertEquals(config.getBacklog(),10);
        assertEquals(config.getContextPath(),"/jolokia/");
        assertEquals(config.getExecutor(),"single");
        assertEquals(config.getThreadNr(),5);
        assertFalse(config.useClientAuthentication());
        assertNull(config.getKeystore());
        assertEquals(config.getKeystorePassword().length, 0);
    }

    @Test
    public void context() {
        JvmAgentConfig config = new JvmAgentConfig("agentContext=/bla");
        assertEquals(config.getContextPath(),"/bla/");
    }

    @Test
    public void jolokiaConfig() {
        JvmAgentConfig config = new JvmAgentConfig("maxDepth=42");

        Configuration jolokiaConfig = config.getJolokiaConfig();
        assertEquals(jolokiaConfig.get(ConfigKey.MAX_DEPTH),"42");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void invalidOptions() {
        new JvmAgentConfig("port=a=1812");
    }

    @Test
    public void readConfig() throws IOException {
        String path = copyResourceToTemp("/agent-test.properties");
        JvmAgentConfig config = new JvmAgentConfig("config=" + path);
        assertEquals(config.getProtocol(), "https");
        assertEquals(config.getUser(),"roland");
        assertEquals(config.getPassword(),"s!cr!t");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*bla\\.txt.*")
    public void configNotFound() {
        new JvmAgentConfig("config=/bla.txt");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void noKeystore() {
        new JvmAgentConfig("protocol=https");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void wrongProtocol() {
        new JvmAgentConfig("protocol=ftp");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void invalidHost() {
        new JvmAgentConfig("host=[192.168.5.0]");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void invalidPort() {
        new JvmAgentConfig("port=bla");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*blub.*")
    public void invalidMode() {
        new JvmAgentConfig("mode=blub");
    }

    // =======================================================================================

    private String copyResourceToTemp(String pResource) throws IOException {
        InputStream is = this.getClass().getResourceAsStream(pResource);
        assertNotNull(is,"Cannot find " + pResource);
        File out = File.createTempFile("prop",".properties");
        copy(is,new FileOutputStream(out));
        return out.getAbsolutePath();
    }

    private void copy(InputStream in, OutputStream out) throws IOException   {
        try {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        }
        finally {
            if (in != null) {
                in.close();
            }
        }
    }
}
