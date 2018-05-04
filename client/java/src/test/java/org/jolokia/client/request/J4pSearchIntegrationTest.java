package org.jolokia.client.request;

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

import java.util.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.client.exception.J4pException;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;

/**
 * Integration test for searching MBeans
 *
 * @author roland
 */
public class J4pSearchIntegrationTest extends AbstractJ4pIntegrationTest {

    @Test
    public void simple() throws MalformedObjectNameException, J4pException {
        for (J4pSearchRequest req : new J4pSearchRequest[] {
                new J4pSearchRequest("java.lang:type=*"),
                new J4pSearchRequest(getTargetProxyConfig(),"java.lang:type=*")
        }) {
            J4pSearchResponse resp = j4pClient.execute(req);
            assertNotNull(resp);
            List<ObjectName> names = resp.getObjectNames();
            assertTrue(names.contains(new ObjectName("java.lang:type=Memory")));
        }
    }

    @Test
    public void emptySearch() throws MalformedObjectNameException, J4pException {
        for (J4pTargetConfig cfg : new J4pTargetConfig[] { null, getTargetProxyConfig()}) {
            J4pSearchResponse resp = j4pClient.execute(new J4pSearchRequest(cfg,"bla:gimme=*"));
            assertEquals(resp.getObjectNames().size(),0);
            assertEquals(resp.getMBeanNames().size(),0);
        }
    }

    @Test(expectedExceptions = { MalformedObjectNameException.class })
    public void invalidSearchPattern() throws MalformedObjectNameException {
        new J4pSearchRequest("bla:blub:args=*");
    }

    @Test
    public void advancedPattern() throws MalformedObjectNameException, J4pException {
        for (J4pTargetConfig cfg : new J4pTargetConfig[] { null, getTargetProxyConfig()}) {
            J4pSearchResponse resp = j4pClient.execute(new J4pSearchRequest(cfg,"java.lang:type=Mem*"));
            List<String> names = resp.getMBeanNames();
            assertEquals(names.size(),1);
        }
    }
}
