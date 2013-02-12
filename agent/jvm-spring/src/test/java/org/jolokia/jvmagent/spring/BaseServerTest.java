package org.jolokia.jvmagent.spring;

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

import java.net.URL;

import org.jolokia.Version;
import org.jolokia.test.util.EnvTestUtil;

import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 29.12.12
 */
class BaseServerTest {

    protected void checkServerAndStop(SpringJolokiaAgent server) throws Exception {
        //Thread.sleep(2000);
        try {
            URL url = new URL(server.getUrl());
            System.out.println(">>> URL to check: " + server.getUrl());
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
