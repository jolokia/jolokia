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
import java.util.*;

import javax.management.*;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.JolokiaReadRequest;
import org.jolokia.server.core.request.JolokiaRequestBuilder;
import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.util.HttpMethod;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.jolokia.service.jmx.handler.BaseHandlerTest;
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
        String attrs[] = new String[] {"attr0","atrr1","attr2"};
        String vals[]  = new String[] {"val0", "val1", "val2"};
        prepareMBeanInfos(server, testBeanName, attrs);
        for (int i=0;i<attrs.length;i++) {
            expect(server.getAttribute(testBeanName,attrs[i])).andReturn(vals[i]);
        }
        replay(server);

        Map res = (Map) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
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
        expect(server.getAttribute(testBeanName,"attr0")).andReturn("val0");
        expect(server.getAttribute(testBeanName,"attr1")).andReturn("val1");
        replay(server);

        Map res = (Map) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
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
        expect(server.getAttribute(testBeanName,"attr0")).andReturn("val0");
        expect(server.getAttribute(testBeanName,"attr1")).andThrow(new AttributeNotFoundException("Couldn't find attr1"));
        replay(server);

        Map res = (Map) handler.handleAllServerRequest(getMBeanServerManager(server),request,null);
    }

    @Test
    public void singleBeanMultiAttributesWithAWrongAttributeNameHandlingException() throws Exception {
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, testBeanName.getCanonicalName()).
                attributes(Arrays.asList("attr0", "attr1")).
                option(ConfigKey.IGNORE_ERRORS, "true").
                build();

        MBeanServer server = createMock(MBeanServer.class);
        expect(server.isRegistered(testBeanName)).andStubReturn(true);
        expect(server.getAttribute(testBeanName,"attr0")).andReturn("val0");
        expect(server.getAttribute(testBeanName,"attr1")).andThrow(new AttributeNotFoundException("Couldn't find attr1"));
        replay(server);


        Map res = (Map) handler.handleAllServerRequest(getMBeanServerManager(server),request,null);
        verify(server);
        assertEquals("val0",res.get("attr0"));
        String err = (String) res.get("attr1");
        assertTrue(err != null && err.contains("ERROR"));
    }

    // ======================================================================================================

    @Test(groups = "java6")
    public void searchPatternNoMatch() throws Exception {
        ObjectName patternMBean = new ObjectName("bla:type=*");
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, patternMBean).
                attribute("mem1").
                build();
        MBeanServer server = createMBeanServer();
        expect(server.queryNames(patternMBean,null)).andReturn(new HashSet());
        replay(server);
        try {
            handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
            fail("Exception should be thrown");
        } catch (InstanceNotFoundException exp) {}
    }

    @Test(groups = "java6")
    public void searchPatternSingleAttribute() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, patternMBean).
                attribute("mem1").
                build();

        ObjectName beans[] =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
        expect(server.getAttribute(beans[0],"mem1")).andReturn("memval1");

        replay(server);
        Map res = (Map) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
        verify(server);
        assertEquals(1,res.size());
        assertEquals("memval1",((Map) res.get("java.lang:type=Memory")).get("mem1"));
    }

    @Test(groups = "java6")
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
                        attributes(Arrays.asList((String) null)).
                        build();

        for (JolokiaReadRequest request : requests) {
            ObjectName beans[] =  {
                    new ObjectName("java.lang:type=Memory"),
                    new ObjectName("java.lang:type=GarbageCollection")
            };
            MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
            expect(server.getAttribute(beans[0],"mem0")).andReturn("memval0");
            expect(server.getAttribute(beans[0],"mem1")).andReturn("memval1");
            expect(server.getAttribute(beans[0],"common")).andReturn("commonVal0");
            expect(server.getAttribute(beans[1],"gc0")).andReturn("gcval0");
            expect(server.getAttribute(beans[1],"gc1")).andReturn("gcval1");
            expect(server.getAttribute(beans[1],"gc3")).andReturn("gcval3");
            expect(server.getAttribute(beans[1],"common")).andReturn("commonVal1");
            replay(server);

            Map res = (Map) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);

            assertEquals("memval0",((Map) res.get("java.lang:type=Memory")).get("mem0"));
            assertEquals("memval1",((Map) res.get("java.lang:type=Memory")).get("mem1"));
            assertEquals("commonVal0",((Map) res.get("java.lang:type=Memory")).get("common"));
            assertEquals("gcval0",((Map) res.get("java.lang:type=GarbageCollection")).get("gc0"));
            assertEquals("gcval1",((Map) res.get("java.lang:type=GarbageCollection")).get("gc1"));
            assertEquals("gcval3",((Map) res.get("java.lang:type=GarbageCollection")).get("gc3"));
            assertEquals("commonVal1",((Map) res.get("java.lang:type=GarbageCollection")).get("common"));

            verify(server);
        }
    }

    @Test(groups = "java6")
    public void searchPatternNoAttributesFound() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, patternMBean).
                attribute(null).
                build();
        ObjectName beans[] =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = createMBeanServer();
        expect(server.queryNames(patternMBean,null)).andReturn(new HashSet(Arrays.asList(beans)));
        prepareMBeanInfos(server,beans[0],new String[0]);
        prepareMBeanInfos(server,beans[1],new String[] { "gc0" });
        expect(server.getAttribute(beans[1],"gc0")).andReturn("gcval0");
        replay(server);

        Map res = (Map) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);

        // Only a single entry fetched
        assertEquals(res.size(),1);
        verify(server);
    }



    @Test(groups = "java6")
    public void searchPatternNoMatchingAttribute() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, patternMBean).
                attribute("blub").
                build();

        ObjectName beans[] =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
        replay(server);
        try {
            handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
        } catch (IllegalArgumentException exp) {
            // expected
        }
        verify(server);
    }

    @Test(groups = "java6")
    public void searchPatternMultiAttributes1() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, patternMBean).
                attributes(Arrays.asList("mem0","gc3")).
                build();

        ObjectName beans[] =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
        expect(server.getAttribute(beans[0],"mem0")).andReturn("memval0");
        expect(server.getAttribute(beans[1],"gc3")).andReturn("gcval3");

        replay(server);
        Map res = (Map) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
        verify(server);
        assertEquals(2,res.size());
        assertEquals("memval0",((Map) res.get("java.lang:type=Memory")).get("mem0"));
        assertEquals("gcval3",((Map) res.get("java.lang:type=GarbageCollection")).get("gc3"));
    }


    @Test(groups = "java6")
    public void searchPatternMultiAttributes3() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, patternMBean).
                attributes(Arrays.asList("bla")).
                build();

        ObjectName beans[] =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
        replay(server);

        try {
            Map res = (Map) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
            fail("Request should fail since attribute name doesn't match any MBean's attribute");
        } catch (IllegalArgumentException exp) {
            // Expect this since no MBean matches the given attribute
        }
    }

    @Test(groups = "java6")
    public void searchPatternMultiAttributes4() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, patternMBean).
                attributes(Arrays.asList("common")).
                build();

        ObjectName beans[] =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
        expect(server.getAttribute(beans[0],"common")).andReturn("com1");
        expect(server.getAttribute(beans[1],"common")).andReturn("com2");

        replay(server);
        Map res = (Map) handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
        verify(server);
        assertEquals(2,res.size());
        assertEquals("com1",((Map) res.get("java.lang:type=Memory")).get("common"));
        assertEquals("com2",((Map) res.get("java.lang:type=GarbageCollection")).get("common"));
    }

    private MBeanServer prepareMultiAttributeTest(ObjectName pPatternMBean, ObjectName[] pBeans)
            throws IOException, MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException, IntrospectionException {
        MBeanServer server = createMBeanServer();
        String params[][] = {
                new String[] {"mem0","mem1","common" },
                new String[] {"gc0","gc1","gc3","common"},
        };
        expect(server.queryNames(pPatternMBean,null)).andReturn(new HashSet(Arrays.asList(pBeans)));
        for (int i=0;i< pBeans.length;i++) {
            prepareMBeanInfos(server, pBeans[i],params[i]);
        }
        return server;
    }

    // ==============================================================================================================
    @Test
    public void handleAllServersAtOnceTest() throws MalformedObjectNameException {
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, testBeanName).
                attribute("attr").
                build();
        assertFalse(handler.handleAllServersAtOnce(request));
        request = new JolokiaRequestBuilder(READ, testBeanName).
                attributes(Arrays.asList("attr1","attr2")).
                build();
        assertTrue(handler.handleAllServersAtOnce(request));
        request = new JolokiaRequestBuilder(READ, testBeanName).
                attributes((List) null).
                build();
        assertTrue(handler.handleAllServersAtOnce(request));
        request = new JolokiaRequestBuilder(READ, "java.lang:*").
                attribute("attr").
                build();
        assertTrue(handler.handleAllServersAtOnce(request));
    }


    // ==============================================================================================================

   @Test
    public void restrictAccess() throws Exception {
        Restrictor restrictor = createMock(Restrictor.class);
        expect(restrictor.isAttributeReadAllowed(testBeanName,"attr")).andReturn(false);
        expect(restrictor.isHttpMethodAllowed(HttpMethod.POST)).andReturn(true);
        ctx = new TestJolokiaContext.Builder().restrictor(restrictor).build();
        handler = new ReadHandler();
        handler.init(ctx,null);
        JolokiaReadRequest request = new JolokiaRequestBuilder(READ, testBeanName).
                attribute("attr").
                build();
        MBeanServer server = createMBeanServer();
        replay(restrictor,server);
        try {
            handler.handleAllServerRequest(getMBeanServerManager(server), request, null);
            fail("Restrictor should forbid access");
        } catch (SecurityException exp) {}
        verify(restrictor,server);
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
        } catch (SecurityException exp) {}
        verify(restrictor,server);
    }

    private MBeanServer createMBeanServer() throws InstanceNotFoundException {
        MBeanServer server = createMock(MBeanServer.class);
        return server;
    }

    // ==============================================================================================================

    private MBeanAttributeInfo[] prepareMBeanInfos(MBeanServerConnection pConnection, ObjectName pObjectName, String pAttrs[])
            throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException, IOException, IntrospectionException {
        MBeanInfo mBeanInfo = createMock(MBeanInfo.class);
        expect(pConnection.isRegistered(pObjectName)).andStubReturn(true);
        expect(pConnection.getMBeanInfo(pObjectName)).andReturn(mBeanInfo);
        MBeanAttributeInfo[] infos = new MBeanAttributeInfo[pAttrs.length];
        for (int i=0;i<pAttrs.length;i++) {
            infos[i] = createMock(MBeanAttributeInfo.class);
            expect(infos[i].getName()).andReturn(pAttrs[i]);
            expect(infos[i].isReadable()).andReturn(true);
        }
        expect(mBeanInfo.getAttributes()).andReturn(infos);
        replay(mBeanInfo);
        for (int j=0;j<infos.length;j++) {
            replay(infos[j]);
        }

        return infos;
    }


}
