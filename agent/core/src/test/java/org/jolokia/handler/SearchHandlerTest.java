package org.jolokia.handler;

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
import java.util.*;

import javax.management.*;

import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JmxRequestBuilder;
import org.jolokia.request.JmxSearchRequest;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.config.ConfigKey;
import org.jolokia.util.RequestType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 12.09.11
 */
public class SearchHandlerTest extends BaseHandlerTest {


    private SearchHandler handler;

    private MBeanServer server;

    @BeforeMethod
    public void setup() {
        handler = new SearchHandler(new AllowAllRestrictor());
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void unsupported() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, MalformedObjectNameException, NotChangedException {
        handler.handleRequest((MBeanServerConnection) null,
                              new JmxRequestBuilder(RequestType.SEARCH, "java.lang:*").<JmxSearchRequest>build());
    }

    @Test
    public void handleAllServersAtOnce() throws MalformedObjectNameException {
        assertTrue(handler.handleAllServersAtOnce(new JmxRequestBuilder(RequestType.SEARCH, "java.lang:*").<JmxSearchRequest>build()));
    }

    @Test
    public void simple() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        List<String> res = doSearch("java.lang:*", null, "java.lang:type=Memory", "java.lang:type=Runtime");
        assertEquals(res.size(), 2);
        assertTrue(res.contains("java.lang:type=Memory"));
        assertTrue(res.contains("java.lang:type=Runtime"));
        verify(server);
    }


    @Test
    public void withEscaping() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        String attr = "java.lang:type=\"m:e*m\\\"?o\\\\y\\n\"";
        List<String> res = doSearch("java.lang:*", null, attr);
        assertEquals(res.size(),1);
        assertTrue(res.contains(attr));
        verify(server);
    }

    @Test
    public void canonical() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        List<String> res = doSearch("java.lang:*", "true", "java.lang:type=Memory,name=bla", "java.lang:type=Runtime,mode=run");
        assertEquals(res.size(),2);
        assertTrue(res.contains("java.lang:name=bla,type=Memory"));
        assertTrue(res.contains("java.lang:mode=run,type=Runtime"));
        verify(server);
    }

    @Test
    public void constructionTime() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        List<String> res = doSearch("java.lang:*", "false", "java.lang:type=Memory,name=bla", "java.lang:type=Runtime,mode=run");
        assertEquals(res.size(),2);
        assertTrue(res.contains("java.lang:type=Memory,name=bla"));
        assertTrue(res.contains("java.lang:type=Runtime,mode=run"));
        verify(server);
    }

    private List<String> doSearch(String pPattern, String pUseCanonicalName, String ... pFoundNames) throws MalformedObjectNameException, IOException, InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, NotChangedException {
        ObjectName oName = new ObjectName(pPattern);
        JmxSearchRequest request = new JmxRequestBuilder(RequestType.SEARCH,oName).option(ConfigKey.CANONICAL_NAMING,pUseCanonicalName).build();

        server = createMock(MBeanServer.class);
        Set<ObjectName> names = new HashSet<ObjectName>();
        for (String name : pFoundNames) {
            names.add(new ObjectName(name));
        }
        expect(server.queryNames(oName,null)).andReturn(names);
        replay(server);
        return (List<String>) handler.handleRequest(getMBeanServerManager(server),request);
    }
}
