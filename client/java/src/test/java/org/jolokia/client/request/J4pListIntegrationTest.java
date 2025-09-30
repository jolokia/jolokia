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
import org.jolokia.client.response.JolokiaListResponse;
import org.jolokia.client.response.JolokiaSearchResponse;
import org.jolokia.json.JSONObject;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;

/**
 * Integration test for listing MBeans
 *
 * @author roland
 */
public class J4pListIntegrationTest extends AbstractJ4pIntegrationTest {

    static private final String TYPE_ESCAPED = "type=naming!/";
    static private final String TYPE_UNESCAPED = "type=naming/";

    @Test
    public void simple() throws J4pException {
        for (JolokiaListRequest req : new JolokiaListRequest[]{
                new JolokiaListRequest(),
                new JolokiaListRequest(getTargetProxyConfig())
        }) {
            JolokiaListResponse resp = j4pClient.execute(req);
            assertNotNull(resp);
        }
    }

    @Test
    public void mbeanMeta() throws J4pException, MalformedObjectNameException {
        ObjectName objectName = new ObjectName("java.lang:type=Memory");
        for (JolokiaListRequest req : new JolokiaListRequest[]{
                new JolokiaListRequest(objectName),
                new JolokiaListRequest(getTargetProxyConfig(), new ObjectName("proxy@" + objectName))
        }) {
            JolokiaListResponse resp = j4pClient.execute(req);
            JSONObject val = resp.getValue();
            // the response is recombined, so it always contains top level domains and 2nd level ObjectNames
            val = (JSONObject) val.values().iterator().next();
            val = (JSONObject) val.get("type=Memory");
            assertTrue(val.containsKey("desc"));
            assertTrue(val.containsKey("op"));
            assertTrue(val.containsKey("attr"));
        }
    }

    @Test
    public void withSpace() throws J4pException {
        for (JolokiaListRequest req : new JolokiaListRequest[] {
                new JolokiaListRequest("jolokia.it/name=name with space," + TYPE_ESCAPED),
                new JolokiaListRequest(getTargetProxyConfig(),"jolokia.it/name=name with space," + TYPE_ESCAPED)
        }) {
            JolokiaListResponse resp = j4pClient.execute(req);
            JSONObject val = resp.getValue();
            // the response is recombined, so it always contains top level domains and 2nd level ObjectNames
            val = (JSONObject) val.get("jolokia.it");
            val = (JSONObject) val.get("name=name with space," + TYPE_UNESCAPED);
            assertEquals("java.lang.String", ((Map<?, ?>) ((Map<?, ?>) val.get("attr")).get("Ok")).get("type"));
        }
    }

    @Test
    public void withSlash() throws MalformedObjectNameException, J4pException {
        JolokiaListRequest[] reqs =  new JolokiaListRequest[] {
                new JolokiaListRequest(new ObjectName("jolokia.it:" + TYPE_UNESCAPED + ",name=n!a!m!e with !/!")),
                new JolokiaListRequest(getTargetProxyConfig(),new ObjectName("jolokia.it:" + TYPE_UNESCAPED + ",name=n!a!m!e with !/!")),
                new JolokiaListRequest(Arrays.asList("jolokia.it",TYPE_UNESCAPED + ",name=n!a!m!e with !/!")),
                new JolokiaListRequest(getTargetProxyConfig(),Arrays.asList("jolokia.it", TYPE_UNESCAPED + ",name=n!a!m!e with !/!")),
                new JolokiaListRequest("jolokia.it/" + TYPE_ESCAPED + ",name=n!!a!!m!!e with !!!/!!"),
                new JolokiaListRequest(getTargetProxyConfig(),"jolokia.it/" + TYPE_ESCAPED + ",name=n!!a!!m!!e with !!!/!!")
        };
        for (JolokiaListRequest req : reqs) {
            JolokiaListResponse resp = j4pClient.execute(req);
            JSONObject val = resp.getValue();
            // the response is recombined, so it always contains top level domains and 2nd level ObjectNames
            val = (JSONObject) val.values().iterator().next();
            val = (JSONObject) val.values().iterator().next();
            assertEquals("java.lang.String", ((Map<?, ?>) ((Map<?, ?>) val.get("attr")).get("Ok")).get("type"));
        }
    }

    @Test
    public void withEscapedWildcards() throws Exception {
        JolokiaSearchRequest searchRequest = new JolokiaSearchRequest("jboss.as.expr:*");
        JolokiaSearchResponse searchResp = j4pClient.execute(searchRequest);

        for (ObjectName oName : searchResp.getObjectNames()) {
            JolokiaListRequest listRequest = new JolokiaListRequest(oName);
            JolokiaListResponse listResp = j4pClient.execute(listRequest);
            Map<?, ?> val = listResp.getValue();
            assertEquals("java.lang.String", ((Map<?, ?>) ((Map<?, ?>) val.get("attr")).get("Ok")).get("type"));
        }
    }
}
