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
 * Integration test for listing MBeans
 *
 * @author roland
 */
public class J4pListIntegrationTest extends AbstractJ4pIntegrationTest {

    static private final String TYPE_ESCAPED = "type=naming!/";
    static private final String TYPE_UNESCAPED = "type=naming/";

    @Test
    public void simple() throws J4pException {
        for (J4pListRequest req : new J4pListRequest[]{
                new J4pListRequest(),
                new J4pListRequest(getTargetProxyConfig())
        }) {
            J4pListResponse resp = j4pClient.execute(req);
            assertNotNull(resp);
        }
    }

    @Test
    public void mbeanMeta() throws J4pException, MalformedObjectNameException {
        ObjectName objectName = new ObjectName("java.lang:type=Memory");
        for (J4pListRequest req : new J4pListRequest[]{
                new J4pListRequest(objectName),
                new J4pListRequest(getTargetProxyConfig(), objectName)
        }) {
            J4pListResponse resp = j4pClient.execute(req);
            Map val = resp.getValue();
            assertTrue(val.containsKey("desc"));
            assertTrue(val.containsKey("op"));
            assertTrue(val.containsKey("attr"));
        }
    }

    @Test
    public void withSpace() throws MalformedObjectNameException, J4pException {
        for (J4pListRequest req : new J4pListRequest[] {
                new J4pListRequest("jolokia.it/name=name with space," + TYPE_ESCAPED),
                new J4pListRequest(getTargetProxyConfig(),"jolokia.it/name=name with space," + TYPE_ESCAPED)
        }) {
            J4pListResponse resp = j4pClient.execute(req);
            Map val = resp.getValue();
            assertEquals( ((Map) ((Map) val.get("attr")).get("Ok")).get("type"),"java.lang.String");
        }
    }

    @Test
    public void withSlash() throws MalformedObjectNameException, J4pException {
        J4pListRequest reqs[] =  new J4pListRequest[] {
                new J4pListRequest(new ObjectName("jolokia.it:" + TYPE_UNESCAPED + ",name=n!a!m!e with !/!")),
                new J4pListRequest(getTargetProxyConfig(),new ObjectName("jolokia.it:" + TYPE_UNESCAPED + ",name=n!a!m!e with !/!")),
                new J4pListRequest(Arrays.asList("jolokia.it",TYPE_UNESCAPED + ",name=n!a!m!e with !/!")),
                new J4pListRequest(getTargetProxyConfig(),Arrays.asList("jolokia.it", TYPE_UNESCAPED + ",name=n!a!m!e with !/!")),
                new J4pListRequest("jolokia.it/" + TYPE_ESCAPED + ",name=n!!a!!m!!e with !!!/!!"),
                new J4pListRequest(getTargetProxyConfig(),"jolokia.it/" + TYPE_ESCAPED + ",name=n!!a!!m!!e with !!!/!!")
        };
        for (J4pListRequest req : reqs) {
            J4pListResponse resp = j4pClient.execute(req);
            Map val = resp.getValue();
            assertEquals( ((Map) ((Map) val.get("attr")).get("Ok")).get("type"),"java.lang.String");
        }
    }

    @Test
    public void withEscapedWildcards() throws Exception {
        J4pSearchRequest searchRequest = new J4pSearchRequest("jboss.as.expr:*");
        J4pSearchResponse searchResp = j4pClient.execute(searchRequest);

        for (ObjectName oName : searchResp.getObjectNames()) {
            J4pListRequest listRequest = new J4pListRequest(oName);
            J4pListResponse listResp = j4pClient.execute(listRequest);
            Map val = listResp.getValue();
            assertEquals( ((Map) ((Map) val.get("attr")).get("Ok")).get("type"),"java.lang.String");
        }
    }
}
