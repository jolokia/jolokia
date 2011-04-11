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

import org.jolokia.request.JmxListRequest;
import org.jolokia.request.JmxRequestBuilder;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.util.ConfigKey;
import org.jolokia.util.RequestType;
import org.json.simple.JSONObject;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;
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

    @Test(expectedExceptions = { UnsupportedOperationException.class })
    public void wrongMethod() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).build();

        // Should always return true in order to be able to merge lists
        assertTrue(handler.handleAllServersAtOnce(request));
        // Path value handling is done internally
        assertFalse(handler.useReturnValueWithPath());

        MBeanServerConnection connection = createMock(MBeanServerConnection.class);
        replay(connection);
        Object res = handler.handleRequest(connection,request);
    }

    @Test
    public void plainTest() throws Exception {

        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).build();

        MBeanServerConnection connection = createMock(MBeanServerConnection.class);
        Set<ObjectName> nameSet = new HashSet<ObjectName>();
        for (String name : new String[] { "java.lang:type=Memory", "java.lang:type=Runtime" }) {
            ObjectName oName = new ObjectName(name);
            nameSet.add(oName);
            expect(connection.getMBeanInfo(oName)).andReturn(getRealMBeanInfo(oName));

        }
        expect(connection.queryNames(null, null)).andReturn(nameSet);
        replay(connection);
        Map res = (Map) handler.handleRequest(asSet(connection),request);
        assertTrue(res.containsKey("java.lang"));
        Map inner = (Map) res.get("java.lang");
        assertTrue(inner.containsKey("type=Memory"));
        assertTrue(inner.containsKey("type=Runtime"));
        assertEquals(inner.size(), 2);
        inner = (Map) inner.get("type=Memory");
        for (String k : new String[] { "desc", "op", "attr"}) {
            assertTrue(inner.containsKey(k));
        }
        assertEquals(inner.size(), 3);
        System.out.println(inner);
        verify(connection);
    }

    @Test
    public void domainPath() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).pathParts("java.lang").build();
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        Map res = (Map) handler.handleRequest(asSet(conn),request);
        assertTrue(res.containsKey("type=Memory"));
        assertTrue(res.get("type=Memory") instanceof Map);
    }

    @Test
    public void propertiesPath() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory").build();
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        Map res = (Map) handler.handleRequest(asSet(conn),request);
        for (String k : new String[] { "desc", "op", "attr"}) {
            assertTrue(res.containsKey(k));
        }
        assertEquals(res.size(), 3);
    }

    @Test
    public void attrPath() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory","attr").build();
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        Map res = (Map) handler.handleRequest(asSet(conn),request);
        assertTrue(res.containsKey("HeapMemoryUsage"));
    }

    @Test
    public void descPath() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory","desc").build();
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        String res = (String) handler.handleRequest(asSet(conn),request);
        assertNotNull(res);
    }

    @Test
    public void opPath() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory","op").build();
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        Map res = (Map) handler.handleRequest(asSet(conn),request);
        assertTrue(res.containsKey("gc"));
    }

    @Test
    public void maxDepth1() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).option(ConfigKey.MAX_DEPTH,"1").build();
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        Map res = (Map) handler.handleRequest(asSet(conn),request);
        assertTrue(res.containsKey("java.lang"));
        assertFalse(res.get("java.lang") instanceof Map);
    }

    @Test
    public void maxDepth2() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).option(ConfigKey.MAX_DEPTH,"2").build();
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        Map res = (Map) handler.handleRequest(asSet(conn),request);
        assertTrue(res.containsKey("java.lang"));
        Map inner = (Map) res.get("java.lang");
        assertTrue(inner.containsKey("type=Memory"));
        assertFalse(inner.get("type=Memory") instanceof Map);
    }


    @Test
    public void maxDepthAndPath() throws Exception {
        JmxListRequest request = new JmxRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory").option(ConfigKey.MAX_DEPTH,"1").build();
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        Object res = (Object) handler.handleRequest(asSet(conn),request);
        System.out.println(res);
    }



    private Set<MBeanServerConnection> asSet(MBeanServerConnection ... pConnections) {
        Set<MBeanServerConnection> ret = new HashSet<MBeanServerConnection>();
        for (MBeanServerConnection conn : pConnections) {
            ret.add(conn);
        }
        return ret;
    }

    private MBeanInfo getRealMBeanInfo(ObjectName oName) throws MalformedObjectNameException, IntrospectionException, InstanceNotFoundException, IOException, ReflectionException {
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        return conn.getMBeanInfo(oName);
    }
}
