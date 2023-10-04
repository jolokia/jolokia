package org.jolokia.service.jmx.handler;

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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;

import javax.management.*;

import org.jolokia.server.core.request.JolokiaListRequest;
import org.jolokia.server.core.request.JolokiaRequestBuilder;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.jolokia.service.jmx.handler.list.DataKeys;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 14.04.11
 */
public class ListHandlerMockTest extends BaseHandlerTest {

    private ListHandler handler;

    @BeforeMethod
    public void createHandler() {
        TestJolokiaContext ctx = new TestJolokiaContext();
        handler = new ListHandler();
        handler.init(ctx, null);
    }

    @Test(expectedExceptions = { UnsupportedOperationException.class })
    public void wrongMethod() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).build();

        // Should always return true in order to be able to merge lists
        assertTrue(handler.handleAllServersAtOnce(request));

        MBeanServerConnection connection = createMock(MBeanServerConnection.class);
        replay(connection);
        handler.handleSingleServerRequest(connection, request);
    }

    @Test
    public void plainTest() throws Exception {

        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).build();

        MBeanServerConnection connection = createMock(MBeanServerConnection.class);
        Set<ObjectName> nameSet = new HashSet<>();
        for (String name : new String[] { "java.lang:type=Memory", "java.lang:type=Runtime" }) {
            ObjectName oName = new ObjectName(name);
            nameSet.add(oName);
            expect(connection.getMBeanInfo(oName)).andReturn(getRealMBeanInfo(oName));

        }
        expect(connection.queryNames(null,null)).andReturn(nameSet);
        replay(connection);

        @SuppressWarnings("unchecked")
        Map<String, ?> res = (Map<String, ?>) handler.handleAllServerRequest(getMBeanServerManager(connection), request, null);
        assertTrue(res.containsKey("java.lang"));
        @SuppressWarnings("unchecked")
        Map<String, ?> inner = (Map<String, ?>) res.get("java.lang");
        assertTrue(inner.containsKey("type=Memory"));
        assertTrue(inner.containsKey("type=Runtime"));
        assertEquals(inner.size(), 2);
        //noinspection unchecked
        inner = (Map<String, ?>) inner.get("type=Memory");
        for (String k : new String[] {DataKeys.DESCRIPTION.getKey(), DataKeys.OPERATIONS.getKey(), DataKeys.ATTRIBUTES.getKey(), DataKeys.CLASSNAME.getKey(), DataKeys.NOTIFICATIONS.getKey()}) {
            assertTrue(inner.containsKey(k));
        }
        assertEquals(inner.size(), 5);
        verify(connection);
    }



    @Test
    public void iOException() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).build();

        MBeanServerConnection connection = prepareForIOException(false);


        @SuppressWarnings("unchecked")
        Map<String, ?> res = (Map<String, ?>) handler.handleAllServerRequest(getMBeanServerManager(connection), request, null);
        verify(connection);
        assertEquals(res.size(),1);
        @SuppressWarnings("unchecked")
        Map<String, ?> jl = (Map<String, ?>) res.get("java.lang");
        assertEquals(jl.size(),2);
        @SuppressWarnings("unchecked")
        Map<String, ?> rt = (Map<String, ?>) jl.get("type=Runtime");
        assertNotNull(rt.get("error"));
        assertEquals(rt.size(),1);
    }

    @Test(expectedExceptions = {IOException.class})
    public void iOExceptionWithPath() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Runtime","attr").build();

        MBeanServerConnection server = prepareForIOException(true);
        handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
    }

    private MBeanServerConnection prepareForIOException(boolean registerCheck) throws MalformedObjectNameException, InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
        MBeanServerConnection server = createMock(MBeanServerConnection.class);
        Set<ObjectName> nameSet = new HashSet<>();
        ObjectName oName = new ObjectName("java.lang:type=Memory");
        nameSet.add(oName);
        expect(server.getMBeanInfo(oName)).andReturn(getRealMBeanInfo(oName));
        oName = new ObjectName("java.lang:type=Runtime");
        if (registerCheck) {
            expect(server.isRegistered(oName)).andReturn(true);
        }
        nameSet.add(oName);
        expect(server.getMBeanInfo(oName)).andThrow(new IOException());
        expect(server.queryNames(null, null)).andReturn(nameSet);
        replay(server);
        return server;
    }

    private MBeanInfo getRealMBeanInfo(ObjectName oName) throws IntrospectionException, InstanceNotFoundException, IOException, ReflectionException {
        MBeanServerConnection conn = ManagementFactory.getPlatformMBeanServer();
        return conn.getMBeanInfo(oName);
    }


}
