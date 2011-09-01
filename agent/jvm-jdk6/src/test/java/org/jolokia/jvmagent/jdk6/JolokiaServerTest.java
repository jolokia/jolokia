package org.jolokia.jvmagent.jdk6;

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
import java.net.URL;

import org.jolokia.Version;
import org.jolokia.util.EnvTestUtil;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 31.08.11
 */
public class JolokiaServerTest {

    @Test
    public void http() throws IOException, InterruptedException {
        String configs[] = {
                null,
                "executor=fixed,threadNr=5",
                "executor=cached",
                "executor=single"
        };

        for (String c : configs) {
            roundtrip(c,true);
        }
    }


    @Test(expectedExceptions = IOException.class,expectedExceptionsMessageRegExp = ".*401.*")
    public void httpWithAuthenticationRejected() throws IOException {
        roundtrip("user=roland,password=s!cr!t",true);

    }


    @Test
    public void ssl() throws IOException {
        int port = EnvTestUtil.getFreePort();
        String keystorePath = getKeystorePath();
        String keystorePassword = "jetty7";
        roundtrip("host=localhost,port=" + port +
                  ",keystore=" + keystorePath +
                  ",keystorePassword=" + keystorePassword +
                  ",protocol=https" +
                  ",user=roland,password=s!cr!t",
                  false);
    }

    @Test(expectedExceptions = SecurityException.class,expectedExceptionsMessageRegExp = ".*No password.*")
    public void invalidConfig() throws IOException {
        new JolokiaServer(new ServerConfig("user=roland"));
    }

    // ==================================================================


    private String getKeystorePath() {
        URL ksURL = this.getClass().getResource("/keystore");
        if (ksURL != null && "file".equalsIgnoreCase(ksURL.getProtocol())) {
            return ksURL.getPath();
        }
        throw new IllegalStateException(ksURL + " is not a file URL");
    }

    private void roundtrip(String pConfig, boolean pDoRequest) throws IOException {
        int port = EnvTestUtil.getFreePort();
        String c = pConfig != null ? pConfig + "," : "";
        ServerConfig config = new ServerConfig(c + "host=localhost,port=" + port);
        JolokiaServer server = new JolokiaServer(config);
        server.start();
        //Thread.sleep(2000);
        try {
            if (pDoRequest) {
                URL url = new URL(server.getUrl());
                String resp = EnvTestUtil.readToString(url.openStream());
                assertTrue(resp.matches(".*type.*version.*" + Version.getAgentVersion() + ".*"));
            }
        } finally {
            server.stop();
        }
    }


}
