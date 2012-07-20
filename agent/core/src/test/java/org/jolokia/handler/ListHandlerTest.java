/*
 * Copyright 2009-2011 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.handler;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;

import javax.management.*;

import org.easymock.EasyMock;
import org.jolokia.request.JmxListRequest;
import org.jolokia.request.JmxRequestBuilder;
import org.jolokia.restrictor.AllowAllRestrictor;
import static org.jolokia.test.util.EnvTestUtil.asSet;
import org.jolokia.util.*;
import org.testng.annotations.*;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 11.04.11
 */
public class ListHandlerTest {

    private ListHandler handler;

    @BeforeMethod
    private void createHandler() {
        handler = new ListHandler(new AllowAllRestrictor());
    }

    @Test
    public void singleSlashPath() throws Exception {
        for (String p : new String[] { null, "", "/" }) {
            JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).path(p).build();
            Map res = execute(request);
            assertTrue(res.containsKey("java.lang"));
            assertTrue(res.get("java.lang") instanceof Map);
        }
    }

    @Test
    public void domainPath() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).pathParts("java.lang").build();
        Map res = execute(request);
        assertTrue(res.containsKey("type=Memory"));
        assertTrue(res.get("type=Memory") instanceof Map);
    }

    @Test
    public void propertiesPath() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory").build();
        Map res = execute(request);
        for (String k : new String[] { "desc", "op", "attr"}) {
            assertTrue(res.containsKey(k));
        }
        assertEquals(res.size(), 3);
    }

    @Test
    public void attrPath() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory","attr").build();
        Map res = execute(request);
        assertTrue(res.containsKey("HeapMemoryUsage"));
    }

    @Test
    public void descPath() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory","desc").build();
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        String res = (String) handler.handleRequest(asSet(conn), request);
        assertNotNull(res);
    }

    @Test
    public void descPathWithDepth() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST)
                .pathParts("java.lang","type=Memory","desc")
                .option(ConfigKey.MAX_DEPTH,"4")
                .build();
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        String res = (String) handler.handleRequest(asSet(conn), request);
        assertNotNull(res);
    }

    @Test
    public void opPath() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory","op").build();
        Map res = execute(request);
        assertTrue(res.containsKey("gc"));
    }

    @Test
    public void maxDepth1() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).option(ConfigKey.MAX_DEPTH,"1").build();
        Map res = execute(request);
        assertTrue(res.containsKey("java.lang"));
        assertFalse(res.get("java.lang") instanceof Map);
    }

    @Test
    public void maxDepth2() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).option(ConfigKey.MAX_DEPTH,"2").build();
        Map res = execute(request);
        assertTrue(res.containsKey("java.lang"));
        Map inner = (Map) res.get("java.lang");
        assertTrue(inner.containsKey("type=Memory"));
        assertFalse(inner.get("type=Memory") instanceof Map);
    }


    @Test
    public void maxDepthAndPath() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory")
                .option(ConfigKey.MAX_DEPTH, "3").build();
        Map res =  execute(request);
        assertEquals(res.size(), 3);
        Map ops = (Map) res.get("op");
        assertTrue(ops.containsKey("gc"));
        assertTrue(ops.get("gc") instanceof Map);
        Map attrs = (Map) res.get("attr");
        // Java 7 introduces a new attribute 'ObjectName' here
        assertEquals(attrs.size(),attrs.containsKey("ObjectName") ? 5 : 4);
        assertTrue(attrs.get("HeapMemoryUsage") instanceof Map);
        assertTrue(res.get("desc") instanceof String);
    }


    @Test
    public void truncatedList() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).pathParts("java.lang", "type=Runtime").build();
        Map res = execute(request);
        assertFalse(res.containsKey("op"));
        assertEquals(res.size(),2);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void invalidPath() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Memory", "attr", "unknownAttribute")
                .build();
        execute(request);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void invalidPath2() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Runtime", "op", "bla")
                .option(ConfigKey.MAX_DEPTH,"3")
                .build();
        execute(request);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void invalidPath3() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Runtime", "bla")
                .option(ConfigKey.MAX_DEPTH,"3")
                .build();
        execute(request);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void invalidPath4() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=*")
                .build();
        execute(request);
    }

    @Test
    public void invalidPath5() throws Exception {
        for (String what : new String[] { "attr", "op", "not" }) {
            try {
                JmxListRequest request = new JmxRequestBuilder(RequestType.LIST)
                        .pathParts("java.lang", "type=Memory", what, "HeapMemoryUsage", "bla")
                        .build();
                execute(request);
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("bla"));
            }
        }
    }

    @Test(expectedExceptions = { IllegalArgumentException.class }, expectedExceptionsMessageRegExp = ".*bla.*")
    public void invalidPath8() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Memory", "desc", "bla")
                .build();
        execute(request);
    }

    private Map execute(JmxListRequest pRequest) throws ReflectionException, InstanceNotFoundException, MBeanException, AttributeNotFoundException, IOException {
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        return (Map) handler.handleRequest(asSet(conn), pRequest);
    }


    @Test
    public void emptyMaps() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Runtime", "op")
                .build();
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        Map res = (Map) handler.handleRequest(asSet(conn),request);
        assertEquals(res.size(),0);

        request = new JmxRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Runtime", "not")
                .build();
        conn = ManagementFactory.getPlatformMBeanServer();
        res = (Map) handler.handleRequest(asSet(conn),request);
        assertEquals(res.size(),0);
    }

    @Test
    public void singleMBeanMultipleServers() throws MalformedObjectNameException, InstanceNotFoundException, IOException, AttributeNotFoundException, ReflectionException, MBeanException, IntrospectionException {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Memory", "attr")
                .build();
        MBeanServerConnection dummyConn = EasyMock.createMock(MBeanServerConnection.class);
        Set<MBeanServerConnection> conns = new LinkedHashSet<MBeanServerConnection>();
        conns.add(dummyConn);
        conns.add(ManagementFactory.getPlatformMBeanServer());

        expect(dummyConn.getMBeanInfo(new ObjectName("java.lang:type=Memory"))).andThrow(new InstanceNotFoundException());
        replay(dummyConn);
        Map res = (Map) handler.handleRequest(conns,request);
        assertEquals(((Map) res.get("Verbose")).get("type"),"boolean");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*not found.*")
    public void noMBeanMultipleServers() throws MalformedObjectNameException, InstanceNotFoundException, IOException, AttributeNotFoundException, ReflectionException, MBeanException, IntrospectionException {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST)
                .pathParts("bullerbue", "country=sweden")
                .build();
        MBeanServerConnection dummyConn = EasyMock.createMock(MBeanServerConnection.class);
        Set<MBeanServerConnection> conns = new LinkedHashSet<MBeanServerConnection>();
        conns.add(dummyConn);
        conns.add(ManagementFactory.getPlatformMBeanServer());

        expect(dummyConn.getMBeanInfo(new ObjectName("bullerbue:country=sweden"))).andThrow(new InstanceNotFoundException());
        replay(dummyConn);
        handler.handleRequest(conns,request);
    }


}
