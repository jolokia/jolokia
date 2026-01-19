/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.service.jmx.handler;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

import javax.management.*;

import org.jolokia.json.JSONObject;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.BadRequestException;
import org.jolokia.server.core.request.JolokiaReadRequest;
import org.jolokia.server.core.request.JolokiaRequestBuilder;
import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.util.HttpMethod;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;
import static org.jolokia.server.core.util.RequestType.READ;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author roland
 * @since Mar 6, 2010
 */
public class ReadHandlerTest extends BaseHandlerTest {

    // handler to test
    private ReadHandler handler;

    private ObjectName testBeanName;

    private TestJolokiaContext ctx;

    @BeforeMethod
    public void createHandler() throws MalformedObjectNameException {
        ctx = new TestJolokiaContext();
        handler = new ReadHandler();
        handler.init(ctx,null);
        testBeanName = new ObjectName("jolokia:type=test");
    }

    @Test
    public void singleBeanSingleAttribute() throws Exception {
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, testBeanName.getCanonicalName()).
                attribute("testAttribute").
                build();

        MBeanServerConnection connection = createMock(MBeanServerConnection.class);
        expect(connection.getAttribute(testBeanName,"testAttribute")).andReturn("testValue");
        replay(connection);
        Object res = handler.handleSingleServerRequest(connection, request);
        verify(connection);
        assertEquals("testValue",res);
    }

    @Test
    public void singleBeanNoAttributes() throws Exception {
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, testBeanName.getCanonicalName()).
                attribute(null).
                build();

        MBeanServer server = createMBeanServer();
        String[] attrs = new String[] {"attr0","atrr1","attr2"};
        String[] vals = new String[] {"val0", "val1", "val2"};
        prepareMBeanInfos(server, testBeanName, attrs);
        expect(server.getAttributes(testBeanName,attrs)).andReturn(new AttributeList(List.of(
            new Attribute(attrs[0], vals[0]),
            new Attribute(attrs[1], vals[1]),
            new Attribute(attrs[2], vals[2])
        )));
        replay(server);

        @SuppressWarnings("unchecked")
        Map<String, ?> res = (Map<String, ?>) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
        verify(server);
        for (int i=0;i<attrs.length;i++) {
            assertEquals(vals[i],res.get(attrs[i]));
        }
    }

    @Test
    public void singleBeanMultiAttributes() throws Exception {
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, testBeanName.getCanonicalName()).
                attributes(Arrays.asList("attr0","attr1")).
                build();

        MBeanServer server = createMBeanServer();
        expect(server.isRegistered(testBeanName)).andStubReturn(true);
        expect(server.getAttributes(testBeanName,new String[] {"attr0", "attr1"})).andReturn(new AttributeList(List.of(
            new Attribute("attr0", "val0"),
            new Attribute("attr1", "val1")
        )));
        replay(server);

        @SuppressWarnings("unchecked")
        Map<String, ?> res = (Map<String, ?>) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
        verify(server);
        assertEquals("val0", res.get("attr0"));
        assertEquals("val1",res.get("attr1"));
    }

    @Test(expectedExceptions = AttributeNotFoundException.class)
    public void singleBeanMultiAttributesWithAWrongAttributeNameThrowingException() throws Exception {
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, testBeanName.getCanonicalName()).
                attributes(Arrays.asList("attr0", "attr1")).
                option(ConfigKey.IGNORE_ERRORS, "false").
                build();

        MBeanServer server = createMock(MBeanServer.class);
        expect(server.isRegistered(testBeanName)).andStubReturn(true);
        expect(server.getAttributes(testBeanName,new String[] {"attr0", "attr1"})).andReturn(new AttributeList(List.of(
            new Attribute("attr0", "val0")
        )));
        expect(server.getAttribute(testBeanName, "attr0")).andReturn(new Attribute("attr0", "val0"));
        expect(server.getAttribute(testBeanName,"attr1")).andThrow(new AttributeNotFoundException("Couldn't find attr1"));
        MBeanInfo mBeanInfoMock = createMock(MBeanInfo.class);
        expect(server.getMBeanInfo(request.getObjectName())).andReturn(mBeanInfoMock);
        expect(mBeanInfoMock.getAttributes()).andReturn(new MBeanAttributeInfo[] {
        });
        replay(server, mBeanInfoMock);

        @SuppressWarnings("unchecked")
        Map<String, ?> res = (Map<String, ?>) handler.handleAllServerRequest(getMBeanServerManager(server),request,null);
    }

    @Test
    public void singleBeanMultiAttributesWithAWrongAttributeNameHandlingException() throws Exception {
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, testBeanName.getCanonicalName()).
                attributes(Arrays.asList("attr0", "attr1")).
                option(ConfigKey.IGNORE_ERRORS, "true").
                build();

        MBeanServer server = createMock(MBeanServer.class);
        expect(server.isRegistered(testBeanName)).andStubReturn(true);
        expect(server.getAttributes(testBeanName,new String[] {"attr0", "attr1"})).andReturn(new AttributeList(List.of(
            new Attribute("attr0", "val0")
        )));
        expect(server.getAttribute(testBeanName,"attr1")).andThrow(new AttributeNotFoundException("Couldn't find attr1"));
        replay(server);

        @SuppressWarnings("unchecked")
        Map<String, ?> res = (Map<String, ?>) handler.handleAllServerRequest(getMBeanServerManager(server),request,null);
        verify(server);
        assertEquals("val0",res.get("attr0"));
        Object result = res.get("attr1");
        assertTrue(result instanceof JSONObject);
        assertTrue(((String) ((JSONObject) result).get("error")).contains("Couldn't find attr1"));
    }

    @Test
    public void searchPatternNoMatch() throws Exception {
        ObjectName patternMBean = new ObjectName("bla:type=*");
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, patternMBean).
                attribute("mem1").
                build();
        MBeanServer server = createMBeanServer();
        expect(server.queryNames(patternMBean,null)).andReturn(new HashSet<>());
        replay(server);
        try {
            handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
            fail("Exception should be thrown");
        } catch (InstanceNotFoundException ignored) {}
    }

    @Test
    @SuppressWarnings("unchecked")
    public void searchPatternSingleAttribute() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, patternMBean).
                attribute("mem1").
                build();

        ObjectName[] beans =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
        expect(server.getAttribute(beans[0],"mem1")).andReturn("memval1");

        replay(server);
        @SuppressWarnings("unchecked")
        Map<String, ?> res = (Map<String, ?>) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
        verify(server);
        assertEquals(1,res.size());
        assertEquals("memval1",((Map<String, ?>) res.get("java.lang:type=Memory")).get("mem1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void searchPatternNoAttribute() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JolokiaReadRequest[] requests = new JolokiaReadRequest[2];
        requests[0] =
                new JolokiaRequestBuilder(READ, patternMBean).
                        attribute(null).
                        build();
        requests[1] =
                new JolokiaRequestBuilder(READ, patternMBean).
                        // A single null element is enough to denote "all"
                        attributes(Collections.singletonList(null)).
                        build();

        for (JolokiaReadRequest request : requests) {
            ObjectName[] beans =  {
                    new ObjectName("java.lang:type=Memory"),
                    new ObjectName("java.lang:type=GarbageCollection")
            };
            MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
            expect(server.getAttributes(beans[0],new String[] {"mem0", "mem1", "common"})).andReturn(new AttributeList(List.of(
                new Attribute("mem0", "memval0"),
                new Attribute("mem1", "memval1"),
                new Attribute("common", "commonVal0")
            )));
            expect(server.getAttributes(beans[1],new String[] {"gc0", "gc1", "gc3", "common"})).andReturn(new AttributeList(List.of(
                new Attribute("gc0", "gcval0"),
                new Attribute("gc1", "gcval1"),
                new Attribute("gc3", "gcval3"),
                new Attribute("common", "commonVal1")
            )));
            replay(server);

            Map<String, ?> res = (Map<String, ?>) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);

            assertEquals("memval0",((Map<String, ?>) res.get("java.lang:type=Memory")).get("mem0"));
            assertEquals("memval1",((Map<String, ?>) res.get("java.lang:type=Memory")).get("mem1"));
            assertEquals("commonVal0",((Map<String, ?>) res.get("java.lang:type=Memory")).get("common"));
            assertEquals("gcval0",((Map<String, ?>) res.get("java.lang:type=GarbageCollection")).get("gc0"));
            assertEquals("gcval1",((Map<String, ?>) res.get("java.lang:type=GarbageCollection")).get("gc1"));
            assertEquals("gcval3",((Map<String, ?>) res.get("java.lang:type=GarbageCollection")).get("gc3"));
            assertEquals("commonVal1",((Map<String, ?>) res.get("java.lang:type=GarbageCollection")).get("common"));

            verify(server);
        }
    }

    @Test
    public void searchPatternNoAttributesFound() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, patternMBean).
                attribute(null).
                build();
        ObjectName[] beans =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = createMBeanServer();
        expect(server.queryNames(patternMBean,null)).andReturn(new HashSet<>(Arrays.asList(beans)));
        prepareMBeanInfos(server,beans[0],new String[0]);
        prepareMBeanInfos(server,beans[1],new String[] { "gc0" });
        expect(server.getAttribute(beans[1],"gc0")).andReturn("gcval0");
        replay(server);

        @SuppressWarnings("unchecked")
        Map<String, ?> res = (Map<String, ?>) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);

        // Only a single entry fetched
        assertEquals(1, res.size());
        verify(server);
    }

    @Test
    public void searchPatternNoMatchingAttribute() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, patternMBean).
                attribute("blub").
                build();

        ObjectName[] beans =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
        replay(server);
        try {
            handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
        } catch (AttributeNotFoundException exp) {
            // expected
        }
        verify(server);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void searchPatternMultiAttributes1() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, patternMBean).
                attributes(Arrays.asList("mem0","gc3")).
                build();

        ObjectName[] beans =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
        expect(server.getAttribute(beans[0],"mem0")).andReturn("memval0");
        expect(server.getAttribute(beans[1],"gc3")).andReturn("gcval3");

        replay(server);
        Map<String, ?> res = (Map<String, ?>) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
        verify(server);
        assertEquals(2,res.size());
        assertEquals("memval0",((Map<String, ?>) res.get("java.lang:type=Memory")).get("mem0"));
        assertEquals("gcval3",((Map<String, ?>) res.get("java.lang:type=GarbageCollection")).get("gc3"));
    }

    @Test
    public void searchPatternMultiAttributes3() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, patternMBean).
                attributes(List.of("bla")).
                build();

        ObjectName[] beans =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
        replay(server);

        try {
            @SuppressWarnings("unchecked")
            Map<String, ?> res = (Map<String, ?>) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
            fail("Request should fail since attribute name doesn't match any MBean's attribute");
        } catch (AttributeNotFoundException exp) {
            // Expect this since no MBean matches the given attribute
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void searchPatternMultiAttributes4() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, patternMBean).
                attributes(List.of("common")).
                build();

        ObjectName[] beans =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
        expect(server.getAttribute(beans[0],"common")).andReturn("com1");
        expect(server.getAttribute(beans[1],"common")).andReturn("com2");

        replay(server);
        Map<String, ?> res = (Map<String, ?>) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
        verify(server);
        assertEquals(2,res.size());
        assertEquals("com1",((Map<String, ?>) res.get("java.lang:type=Memory")).get("common"));
        assertEquals("com2",((Map<String, ?>) res.get("java.lang:type=GarbageCollection")).get("common"));
    }

    private MBeanServer prepareMultiAttributeTest(ObjectName pPatternMBean, ObjectName[] pBeans)
            throws IOException, InstanceNotFoundException, ReflectionException, IntrospectionException {
        MBeanServer server = createMBeanServer();
        String[][] params = {
                new String[] {"mem0","mem1","common" },
                new String[] {"gc0","gc1","gc3","common"},
        };
        expect(server.queryNames(pPatternMBean,null)).andReturn(new HashSet<>(Arrays.asList(pBeans)));
        for (int i=0;i< pBeans.length;i++) {
            prepareMBeanInfos(server, pBeans[i],params[i]);
        }
        return server;
    }

    @Test
    public void handleAllServersAtOnceTest() throws BadRequestException {
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, testBeanName).
                attribute("attr").
                build();
        assertFalse(handler.handleAllServersAtOnce(request));
        request = new JolokiaRequestBuilder(READ, testBeanName).
                attributes(Arrays.asList("attr1","attr2")).
                build();
        assertTrue(handler.handleAllServersAtOnce(request));
        request = new JolokiaRequestBuilder(READ, testBeanName).
                attributes((List<String>) null).
                build();
        assertTrue(handler.handleAllServersAtOnce(request));
        request = new JolokiaRequestBuilder(READ, "java.lang:*").
                attribute("attr").
                build();
        assertTrue(handler.handleAllServersAtOnce(request));
    }

    @Test
    public void restrictAccess() throws Exception {
        Restrictor restrictor = createMock(Restrictor.class);
        expect(restrictor.isAttributeReadAllowed(testBeanName, "attr")).andReturn(false);
        expect(restrictor.isHttpMethodAllowed(HttpMethod.POST)).andReturn(true);
        ctx = new TestJolokiaContext.Builder().restrictor(restrictor).build();
        handler = new ReadHandler();
        handler.init(ctx, null);
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, testBeanName).
            attribute("attr").
            build();
        MBeanServer server = createMBeanServer();
        MBeanInfo mBeanInfoMock = createMock(MBeanInfo.class);
        MBeanAttributeInfo attrMock = createMock(MBeanAttributeInfo.class);
        expect(server.getMBeanInfo(request.getObjectName())).andReturn(mBeanInfoMock);
        expect(mBeanInfoMock.getAttributes()).andReturn(new MBeanAttributeInfo[] {
            attrMock
        }).anyTimes();
        expect(attrMock.isReadable()).andReturn(true);
        expect(attrMock.getName()).andReturn("attr");
        replay(restrictor, server, mBeanInfoMock, attrMock);
        try {
            handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
            fail("Restrictor should forbid access");
        } catch (SecurityException ignored) {
        }
        verify(restrictor, server);
    }

    @Test
    public void restrictHttpMethodAccess() throws Exception {
        Restrictor restrictor = createMock(Restrictor.class);
        expect(restrictor.isHttpMethodAllowed(HttpMethod.POST)).andReturn(false);
        ctx = new TestJolokiaContext.Builder().restrictor(restrictor).build();
        handler = new ReadHandler();
        handler.init(ctx,null);
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, testBeanName).
                attribute("attr").
                build();
        MBeanServer server = createMock(MBeanServer.class);
        replay(restrictor,server);
        try {
            handler.handleAllServerRequest(getMBeanServerManager(server),request, null);
            fail("Restrictor should forbid HTTP Method Access");
        } catch (SecurityException ignored) {}
        verify(restrictor,server);
    }

    private MBeanServer createMBeanServer() {
        return createMock(MBeanServer.class);
    }

    private MBeanAttributeInfo[] prepareMBeanInfos(MBeanServerConnection pConnection, ObjectName pObjectName, String[] pAttrs)
        throws InstanceNotFoundException, ReflectionException, IOException, IntrospectionException {
        MBeanInfo mBeanInfo = createMock(MockableMBeanInfo.class);
        expect(pConnection.isRegistered(pObjectName)).andStubReturn(true);
        expect(pConnection.getMBeanInfo(pObjectName)).andReturn(mBeanInfo);
        MBeanAttributeInfo[] infos = new MBeanAttributeInfo[pAttrs.length];
        for (int i=0;i<pAttrs.length;i++) {
            infos[i] = createMock(MockableMBeanAttributeInfo.class);
            expect(infos[i].getName()).andReturn(pAttrs[i]);
            expect(infos[i].isReadable()).andReturn(true);
        }
        expect(mBeanInfo.getAttributes()).andReturn(infos);
        replay(mBeanInfo);
        for (MBeanAttributeInfo info : infos) {
            replay(info);
        }

        return infos;
    }

    // to prevent
    // java.lang.IllegalAccessException: no such field: javax.management.MBeanInfo$$$EasyMock$1.$callback/org.easymock.internal.ClassMockingData/putField
    public static class MockableMBeanInfo extends MBeanInfo {
        public MockableMBeanInfo(String className, String description, MBeanAttributeInfo[] attributes, MBeanConstructorInfo[] constructors, MBeanOperationInfo[] operations, MBeanNotificationInfo[] notifications) throws IllegalArgumentException {
            super(className, description, attributes, constructors, operations, notifications);
        }

        public MockableMBeanInfo(String className, String description, MBeanAttributeInfo[] attributes, MBeanConstructorInfo[] constructors, MBeanOperationInfo[] operations, MBeanNotificationInfo[] notifications, Descriptor descriptor) throws IllegalArgumentException {
            super(className, description, attributes, constructors, operations, notifications, descriptor);
        }
    }

    // to prevent
    // java.lang.IllegalAccessException: no such field: javax.management.MBeanAttributeInfo$$$EasyMock$2.$callback/org.easymock.internal.ClassMockingData/putField
    public static class MockableMBeanAttributeInfo extends MBeanAttributeInfo {
        public MockableMBeanAttributeInfo(String name, String type, String description, boolean isReadable, boolean isWritable, boolean isIs) {
            super(name, type, description, isReadable, isWritable, isIs);
        }

        public MockableMBeanAttributeInfo(String name, String type, String description, boolean isReadable, boolean isWritable, boolean isIs, Descriptor descriptor) {
            super(name, type, description, isReadable, isWritable, isIs, descriptor);
        }

        public MockableMBeanAttributeInfo(String name, String description, Method getter, Method setter) throws IntrospectionException {
            super(name, description, getter, setter);
        }
    }

}
