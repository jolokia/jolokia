/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.client.jmxadapter;

import javax.management.remote.JMXServiceURL;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class JMXServiceURLTest {

    @Test
    public void creatingWithJolokiaProtocol() throws Exception {
        JMXServiceURL url = new JMXServiceURL("jolokia", "localhost", 8778);
        assertEquals(url.toString(), "service:jmx:jolokia://localhost:8778");
    }

    @Test
    public void creatingWithJolokiaHttpProtocol() throws Exception {
        JMXServiceURL url1 = new JMXServiceURL("jolokia+http", "localhost", 8778);
        assertEquals(url1.toString(), "service:jmx:jolokia+http://localhost:8778");
        JMXServiceURL url2 = new JMXServiceURL("jolokia+https", "localhost", 8778);
        assertEquals(url2.toString(), "service:jmx:jolokia+https://localhost:8778");
    }

}
