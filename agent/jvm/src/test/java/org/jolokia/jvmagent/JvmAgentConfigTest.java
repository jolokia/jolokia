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

import com.sun.net.httpserver.Authenticator;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.jvmagent.security.UserPasswordAuthenticator;
import org.jolokia.util.EscapeUtil;
import org.testng.annotations.Test;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 13.08.11
 */
public class JvmAgentConfigTest {

    @Test
    public void simple() {
        JvmAgentConfig config = new JvmAgentConfig("port=4711,mode=stop");
        assertEquals(config.getPort(), 4711);
        assertTrue(config.isModeStop());
        assertEquals(config.getBacklog(), 10);
    }

    @Test
    public void withMultipleEquals() {
        JvmAgentConfig config = new JvmAgentConfig("clientPrincipal=O=jolokia.org\\,DN=Roland Huss,protocol=https");
        assertEquals(config.getClientPrincipals().get(0),"O=jolokia.org,DN=Roland Huss");
        assertEquals(config.getProtocol(),"https");
        assertEquals(config.getBacklog(), 10);

    }

    @Test
    public void detectorArgs() {
        JvmAgentConfig config = new JvmAgentConfig("bootAmx=true");
        Configuration jConfig = config.getJolokiaConfig();
        String detectorOpts = jConfig.get(ConfigKey.DETECTOR_OPTIONS);
        assertEquals(detectorOpts.replaceAll("\\s*", ""), "{\"glassfish\":{\"bootAmx\":true}}");
    }

    @Test
    public void listArgs() {
        JvmAgentConfig config = new JvmAgentConfig("clientPrincipal=O=jolokia.org\\,CN=Roland Huss,clientPrincipal.1=O=redhat.com\\,CN=jolokia,clientPrincipal.3=bla");
        assertEquals(config.getClientPrincipals().size(),2);
        assertEquals(config.getClientPrincipals().get(0),"O=jolokia.org,CN=Roland Huss");
        assertEquals(config.getClientPrincipals().get(1),"O=redhat.com,CN=jolokia");
    }

    @Test
    public void defaults() throws UnknownHostException {
        JvmAgentConfig config = new JvmAgentConfig("");
        assertEquals(config.getAddress(), InetAddress.getByName(null));
        assertFalse(config.isModeStop());
        assertEquals(config.getProtocol(), "http");
        assertEquals(config.getPort(), 8778);
        assertNull(config.getAuthenticator());
        assertEquals(config.getBacklog(), 10);
        assertEquals(config.getContextPath(), "/jolokia/");
        assertEquals(config.getExecutor(), "single");
        assertEquals(config.getThreadNr(), 5);
        assertFalse(config.useSslClientAuthentication());
        assertNull(config.getKeystore());
        assertEquals(config.getKeystorePassword().length, 0);
    }

    @Test
    public void context() {
        JvmAgentConfig config = new JvmAgentConfig("agentContext=/bla");
        assertEquals(config.getContextPath(), "/bla/");
    }

    @Test
    public void jolokiaConfig() {
        JvmAgentConfig config = new JvmAgentConfig("maxDepth=42");

        Configuration jolokiaConfig = config.getJolokiaConfig();
        assertEquals(jolokiaConfig.get(ConfigKey.MAX_DEPTH), "42");
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
        Authenticator authenticator = config.getAuthenticator();
        assertNotNull(authenticator);
        assertEquals(config.getClientPrincipals().get(0),"O=jolokia.org,OU=JVM");
        assertTrue(authenticator instanceof UserPasswordAuthenticator);
        assertTrue(((UserPasswordAuthenticator) authenticator).checkCredentials("roland", "s!cr!t"));
    }

    @Test
    public void readConfigWithCustomAuthenticator() throws IOException {
        String path = copyResourceToTemp("/agent-custom-authenticator-test.properties");
        JvmAgentConfig config = new JvmAgentConfig("config=" + path);
        assertEquals(config.getProtocol(), "http");
        Authenticator authenticator = config.getAuthenticator();
        assertNotNull(authenticator);
        assertTrue(authenticator instanceof Dummy);
        assertSame(((Dummy) authenticator).getConfig(), config.getJolokiaConfig());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*bla\\.txt.*")
    public void configNotFound() {
        new JvmAgentConfig("config=/bla.txt");
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

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*blub.*")
    public void invalidMode() {
        new JvmAgentConfig("mode=blub");
    }

    @Test
    public void keystorePassword() throws UnknownHostException {
        JvmAgentConfig config = new JvmAgentConfig("keystorePassword=passwd");
        assertEquals(config.getKeystorePassword(), "passwd".toCharArray());
    }

    @Test
    public void keystorePasswordEncrypted() throws UnknownHostException {
        JvmAgentConfig config = new JvmAgentConfig("keystorePassword=[[b4m+ADwT8u8HAoVvv3n6WLAEfFFceJHSu6rsNT1/CsHiWFzUseNMS4C2d1AtxJNC]]");
        assertEquals(config.getKeystorePassword(), "1234567890123456".toCharArray());
    }


    // =======================================================================================

    private String copyResourceToTemp(String pResource) throws IOException {
        InputStream is = this.getClass().getResourceAsStream(pResource);
        assertNotNull(is, "Cannot find " + pResource);
        File out = File.createTempFile("prop", ".properties");
        copy(is, new FileOutputStream(out));
        String path = out.getAbsolutePath();

        if (EscapeUtil.CSV_ESCAPE.equals("\\\\") && (File.separator.equals("\\"))) {
           /* Path can be similar to C:\...\...\...\...\Temp\prop424242424242424242.properties on Win,
              so we need to escape \ otherwise tests will fail. We need to escape it twice, once for
              list of parameter split unescaping and once more for parameter=value split unecapsulation
           */
            //First "\\\\" is regex
            path = path.replaceAll("\\\\", Matcher.quoteReplacement("\\\\\\\\"));
        }
        return path;
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        try {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }
}
