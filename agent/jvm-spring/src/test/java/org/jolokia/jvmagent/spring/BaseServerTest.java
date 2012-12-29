package org.jolokia.jvmagent.spring;

import java.net.URL;

import org.jolokia.Version;
import org.jolokia.test.util.EnvTestUtil;

import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 29.12.12
 */
class BaseServerTest {

    protected void checkServerAndStop(SpringJolokiaServer server) throws Exception {
        //Thread.sleep(2000);
        try {
            URL url = new URL(server.getUrl());
            String resp = EnvTestUtil.readToString(url.openStream());
            assertTrue(resp.matches(".*type.*version.*" + Version.getAgentVersion() + ".*"));
        } finally {
            server.destroy();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {

            }
        }
    }
}
