package org.jolokia.handler;

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

import org.jolokia.config.*;
import org.jolokia.request.JmxReadRequest;
import org.jolokia.request.JmxRequestBuilder;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.HttpMethod;
import org.json.simple.JSONObject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.jolokia.util.RequestType.READ;
import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author roland
 * @since Mar 6, 2010
 */
public class ReadHandlerTest extends BaseHandlerTest {

    // handler to test
    private ReadHandler handler;

    private ObjectName testBeanName;

    @BeforeMethod
    public void createHandler() throws MalformedObjectNameException {
        handler = new ReadHandler(new AllowAllRestrictor(), new Configuration());
        testBeanName = new ObjectName("jolokia:type=test");
    }

    @Test
    public void singleBeanSingleAttribute() throws Exception {
        JmxReadRequest request = new JmxRequestBuilder(READ, testBeanName.getCanonicalName()).
                attribute("testAttribute").
                build();

        MBeanServerConnection connection = createMock(MBeanServerConnection.class);
        expect(connection.getAttribute(testBeanName,"testAttribute")).andReturn("testValue");
        replay(connection);
        Object res = handler.handleRequest(connection,request);
        verify(connection);
        assertEquals("testValue",res);
    }

    @Test
    public void singleBeanSingleAttributeInTagFormat() throws Exception {
        JmxReadRequest request = new JmxRequestBuilder(READ, testBeanName.getCanonicalName()).
                attribute("testAttribute").
                option(ConfigKey.VALUE_FORMAT,"tag").
                build();

        MBeanServerConnection connection = createMock(MBeanServerConnection.class);
        expect(connection.getAttribute(testBeanName,"testAttribute")).andReturn("testValue");
        replay(connection);
        JSONObject res = (JSONObject) handler.handleRequest(connection, request);
        verify(connection);

        verifyTagFormatValue(res,testBeanName,"testValue",ValueFormat.KEY_ATTRIBUTE,"testAttribute");
    }

    @Test
    public void singleBeanNoAttributes() throws Exception {
        JmxReadRequest request = new JmxRequestBuilder(READ, testBeanName.getCanonicalName()).
                attribute(null).
                build();


        MBeanServer server = createMock(MBeanServer.class);
        String attrs[] = new String[] {"attr0","atrr1","attr2"};
        String vals[]  = new String[] {"val0", "val1", "val2"};
        prepareMBeanInfos(server, testBeanName, attrs);
        for (int i=0;i<attrs.length;i++) {
            expect(server.getAttribute(testBeanName,attrs[i])).andReturn(vals[i]);
        }
        replay(server);

        Map res = (Map) handler.handleRequest(getMBeanServerManager(server),request);
        verify(server);
        for (int i=0;i<attrs.length;i++) {
            assertEquals(vals[i],res.get(attrs[i]));

        }
    }

    @Test
    public void singleBeanMultiAttributes() throws Exception {
        JmxReadRequest request = new JmxRequestBuilder(READ, testBeanName.getCanonicalName()).
                attributes(Arrays.asList("attr0", "attr1")).
                build();


        MBeanServer server = createMock(MBeanServer.class);
        expect(server.isRegistered(testBeanName)).andStubReturn(true);
        expect(server.getAttribute(testBeanName,"attr0")).andReturn("val0");
        expect(server.getAttribute(testBeanName,"attr1")).andReturn("val1");
        replay(server);

        Map res = (Map) handler.handleRequest(getMBeanServerManager(server),request);
        verify(server);
        assertEquals("val0", res.get("attr0"));
        assertEquals("val1",res.get("attr1"));
    }


    @Test
    public void singleBeanMultiAttributesWithTagFormat() throws Exception {
        JmxReadRequest request = new JmxRequestBuilder(READ, testBeanName.getCanonicalName()).
                attributes(Arrays.asList("attr0", "attr1")).
                option(ConfigKey.VALUE_FORMAT,"tag").
                build();

        MBeanServer server = createMock(MBeanServer.class);
        expect(server.isRegistered(testBeanName)).andStubReturn(true);
        expect(server.getAttribute(testBeanName,"attr0")).andReturn("val0");
        expect(server.getAttribute(testBeanName,"attr1")).andReturn("val1");
        replay(server);

        List res = (List) handler.handleRequest(getMBeanServerManager(server),request);
        verify(server);

        String[][] toCheck = {
                { "attr0", "val0" },
                { "attr1", "val1" }
        };
        for (int i = 0; i < res.size(); i++) {
            Map value = (Map) res.get(i);
            verifyTagFormatValue(value,testBeanName,toCheck[i][1],ValueFormat.KEY_ATTRIBUTE,toCheck[i][0]);
        }
    }


    @Test(expectedExceptions = AttributeNotFoundException.class)
    public void singleBeanMultiAttributesWithAWrongAttributeNameThrowingException() throws Exception {
        JmxReadRequest request = new JmxRequestBuilder(READ, testBeanName.getCanonicalName()).
                attributes(Arrays.asList("attr0", "attr1")).
                option(ConfigKey.IGNORE_ERRORS, "false").
                build();

        MBeanServer server = preparteMultiAttributeRequestWithException();
        handler.handleRequest(getMBeanServerManager(server),request);
    }

    private MBeanServer preparteMultiAttributeRequestWithException() throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException {
        MBeanServer server = createMock(MBeanServer.class);
        expect(server.isRegistered(testBeanName)).andStubReturn(true);
        expect(server.getAttribute(testBeanName,"attr0")).andReturn("val0");
        expect(server.getAttribute(testBeanName,"attr1")).andThrow(new AttributeNotFoundException("Couldn't find attr1"));
        replay(server);
        return server;
    }

    @Test
    public void singleBeanMultiAttributesWithAWrongAttributeNameHandlingException() throws Exception {
        JmxReadRequest request = new JmxRequestBuilder(READ, testBeanName.getCanonicalName()).
                attributes(Arrays.asList("attr0", "attr1")).
                option(ConfigKey.IGNORE_ERRORS, "true").
                build();

        MBeanServer server = preparteMultiAttributeRequestWithException();
        Map res = (Map) handler.handleRequest(getMBeanServerManager(server),request);
        verify(server);
        assertEquals("val0",res.get("attr0"));
        String err = (String) res.get("attr1");
        assertTrue(err != null && err.contains("ERROR"));
    }

    @Test
    public void singleBeanMultiAttributesWithAWrongAttributeNameHandlingExceptionAndTagValueFormat() throws Exception {
        JmxReadRequest request = new JmxRequestBuilder(READ, testBeanName.getCanonicalName()).
                attributes(Arrays.asList("attr0", "attr1")).
                option(ConfigKey.IGNORE_ERRORS, "true").
                option(ConfigKey.VALUE_FORMAT, "tag").
                build();

        MBeanServer server = preparteMultiAttributeRequestWithException();
        List res = (List) handler.handleRequest(getMBeanServerManager(server),request);
        verify(server);

        verifyTagFormatValue((Map) res.get(0), testBeanName, "val0", ValueFormat.KEY_ATTRIBUTE, "attr0");
        Map errorEntry = (Map) res.get(1);
        String err = (String) errorEntry.get(ValueFormat.KEY_VALUE);
        assertTrue(err != null && err.contains("ERROR"));
    }

    // ======================================================================================================

    @Test
    public void searchPatternNoMatch() throws Exception {
        ObjectName patternMBean = new ObjectName("bla:type=*");
        JmxReadRequest request = new JmxRequestBuilder(READ, patternMBean).
                attribute("mem1").
                build();
        MBeanServer server = createMock(MBeanServer.class);
        expect(server.queryNames(patternMBean,null)).andReturn(new HashSet());
        replay(server);
        try {
            handler.handleRequest(getMBeanServerManager(server),request);
            fail("Exception should be thrown");
        } catch (InstanceNotFoundException exp) {}
    }

    @Test
    public void searchPatternSingleAttribute() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JmxReadRequest request = new JmxRequestBuilder(READ, patternMBean).
                attribute("mem1").
                build();

        ObjectName beans[] =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
        expect(server.getAttribute(beans[0],"mem1")).andReturn("memval1");

        replay(server);
        Map res = (Map) handler.handleRequest(getMBeanServerManager(server), request);
        verify(server);
        assertEquals(1,res.size());
        assertEquals("memval1",((Map) res.get("java.lang:type=Memory")).get("mem1"));
    }

    @Test
    public void searchPatternSingleAttributeWithTagFormat() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JmxReadRequest request = new JmxRequestBuilder(READ, patternMBean).
                attribute("mem1").
                option(ConfigKey.VALUE_FORMAT,"tag").
                build();

        ObjectName beans[] =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
        expect(server.getAttribute(beans[0],"mem1")).andReturn("memval1");
        replay(server);
        List res = (List) handler.handleRequest(getMBeanServerManager(server), request);
        verify(server);
        assertEquals(1,res.size());
        verifyTagFormatValue((Map) res.get(0),beans[0],"memval1",ValueFormat.KEY_ATTRIBUTE,"mem1");
    }

    @Test
    public void searchPatternNoAttribute() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JmxReadRequest[] requests = new JmxReadRequest[2];
        for (ValueFormat format : ValueFormat.values()) {
            requests[0] =
                    new JmxRequestBuilder(READ, patternMBean).
                            attribute(null).
                            option(ConfigKey.VALUE_FORMAT,format.name()).
                            build();
            requests[1] =
                    new JmxRequestBuilder(READ, patternMBean).
                            // A single null element is enough to denote "all"
                            attributes(Arrays.asList((String) null)).
                            option(ConfigKey.VALUE_FORMAT,format.name()).
                            build();

            for (JmxReadRequest request : requests) {
                ObjectName beans[] = {
                        new ObjectName("java.lang:type=Memory"),
                        new ObjectName("java.lang:type=GarbageCollection")
                };
                MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
                expect(server.getAttribute(beans[0], "mem0")).andReturn("memval0");
                expect(server.getAttribute(beans[0], "mem1")).andReturn("memval1");
                expect(server.getAttribute(beans[0], "common")).andReturn("commonVal0");
                expect(server.getAttribute(beans[1], "gc0")).andReturn("gcval0");
                expect(server.getAttribute(beans[1], "gc1")).andReturn("gcval1");
                expect(server.getAttribute(beans[1], "gc3")).andReturn("gcval3");
                expect(server.getAttribute(beans[1], "common")).andReturn("commonVal1");
                replay(server);

                String[][] expectVals = new String[][] {
                        { "java.lang:type=Memory", "mem0", "memval0" },
                        { "java.lang:type=Memory", "mem1", "memval1" },
                        { "java.lang:type=Memory", "common", "commonVal0" },
                        { "java.lang:type=GarbageCollection", "gc0", "gcval0" },
                        { "java.lang:type=GarbageCollection", "gc1", "gcval1" },
                        { "java.lang:type=GarbageCollection", "gc3", "gcval3" },
                        { "java.lang:type=GarbageCollection", "common", "commonVal1" }
                };

                if (format == ValueFormat.PLAIN) {
                    Map res = (Map) handler.handleRequest(getMBeanServerManager(server), request);

                    for (int i = 0; i < expectVals.length; i++) {
                        assertEquals(expectVals[i][2],((Map) res.get(expectVals[i][0])).get(expectVals[i][1]));
                    }
                 } else {
                    List<Map> res = (List<Map>) handler.handleRequest(getMBeanServerManager(server), request);
                    assertEquals(expectVals.length,res.size());
                    Set<String> expected = new HashSet<String>();
                    for (int i = 0; i < expectVals.length; i++) {
                        expected.add(expectVals[i][0] + expectVals[i][1] + expectVals[i][2]);
                    }
                    for (Map val : res) {
                        String key = val.get(ValueFormat.KEY_DOMAIN) + ":type=" + val.get("type") +
                                     val.get(ValueFormat.KEY_ATTRIBUTE) + val.get(ValueFormat.KEY_VALUE);
                        assertTrue(expected.contains(key));
                    }
                }

                verify(server);
            }
        }
    }

    @Test
    public void searchPatternNoAttributesFound() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JmxReadRequest request = new JmxRequestBuilder(READ, patternMBean).
                attribute(null).
                build();
        ObjectName beans[] =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = createMock(MBeanServer.class);
        expect(server.queryNames(patternMBean,null)).andReturn(new HashSet(Arrays.asList(beans)));
        prepareMBeanInfos(server,beans[0],new String[0]);
        prepareMBeanInfos(server,beans[1],new String[] { "gc0" });
        expect(server.getAttribute(beans[1],"gc0")).andReturn("gcval0");
        replay(server);

        Map res = (Map) handler.handleRequest(getMBeanServerManager(server), request);

        // Only a single entry fetched
        assertEquals(res.size(),1);
        verify(server);
    }

    @Test
    public void searchPatternNoMatchingAttribute() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JmxReadRequest request = new JmxRequestBuilder(READ, patternMBean).
                attribute("blub").
                build();

        ObjectName beans[] =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
        replay(server);
        try {
            handler.handleRequest(getMBeanServerManager(server), request);
        } catch (IllegalArgumentException exp) {
            // expected
        }
        verify(server);
    }

    @Test
    public void searchPatternMultiAttributes1() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JmxReadRequest request = new JmxRequestBuilder(READ, patternMBean).
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
        Map res = (Map) handler.handleRequest(getMBeanServerManager(server), request);
        verify(server);
        assertEquals(2,res.size());
        assertEquals("memval0",((Map) res.get("java.lang:type=Memory")).get("mem0"));
        assertEquals("gcval3",((Map) res.get("java.lang:type=GarbageCollection")).get("gc3"));
    }


    @Test
    public void searchPatternMultiAttributes3() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JmxReadRequest request = new JmxRequestBuilder(READ, patternMBean).
                attributes(Arrays.asList("bla")).
                build();

        ObjectName beans[] =  {
                new ObjectName("java.lang:type=Memory"),
                new ObjectName("java.lang:type=GarbageCollection")
        };
        MBeanServer server = prepareMultiAttributeTest(patternMBean, beans);
        replay(server);

        try {
            Map res = (Map) handler.handleRequest(getMBeanServerManager(server), request);
            fail("Request should fail since attribute name doesn't match any MBean's attribute");
        } catch (IllegalArgumentException exp) {
            // Expect this since no MBean matches the given attribute
        }
    }

    @Test
    public void searchPatternMultiAttributes4() throws Exception {
        ObjectName patternMBean = new ObjectName("java.lang:type=*");
        JmxReadRequest request = new JmxRequestBuilder(READ, patternMBean).
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
        Map res = (Map) handler.handleRequest(getMBeanServerManager(server), request);
        verify(server);
        assertEquals(2,res.size());
        assertEquals("com1",((Map) res.get("java.lang:type=Memory")).get("common"));
        assertEquals("com2",((Map) res.get("java.lang:type=GarbageCollection")).get("common"));
    }

    private MBeanServer prepareMultiAttributeTest(ObjectName pPatternMBean, ObjectName[] pBeans)
            throws IOException, MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException, IntrospectionException {
        MBeanServer server = createMock(MBeanServer.class);
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
        JmxReadRequest request = new JmxRequestBuilder(READ, testBeanName).
                attribute("attr").
                build();
        assertFalse(handler.handleAllServersAtOnce(request));
        request = new JmxRequestBuilder(READ, testBeanName).
                attributes(Arrays.asList("attr1","attr2")).
                build();
        assertTrue(handler.handleAllServersAtOnce(request));
        request = new JmxRequestBuilder(READ, testBeanName).
                attributes((List) null).
                build();
        assertTrue(handler.handleAllServersAtOnce(request));
        request = new JmxRequestBuilder(READ, "java.lang:*").
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
        handler = new ReadHandler(restrictor, new Configuration());

        JmxReadRequest request = new JmxRequestBuilder(READ, testBeanName).
                attribute("attr").
                build();
        MBeanServer server = createMock(MBeanServer.class);
        replay(restrictor,server);
        try {
            handler.handleRequest(getMBeanServerManager(server),request);
            fail("Restrictor should forbid access");
        } catch (SecurityException exp) {}
        verify(restrictor,server);
    }

    @Test
    public void restrictHttpMethodAccess() throws Exception {
        Restrictor restrictor = createMock(Restrictor.class);
        expect(restrictor.isHttpMethodAllowed(HttpMethod.POST)).andReturn(false);
        handler = new ReadHandler(restrictor, new Configuration());

        JmxReadRequest request = new JmxRequestBuilder(READ, testBeanName).
                attribute("attr").
                build();
        MBeanServer server = createMock(MBeanServer.class);
        replay(restrictor,server);
        try {
            handler.handleRequest(getMBeanServerManager(server),request);
            fail("Restrictor should forbid HTTP Method Access");
        } catch (SecurityException exp) {}
        verify(restrictor,server);
    }

    @Test
    public void formatValuePlain() throws MalformedObjectNameException {
        for (String paramVal : new String[] { null, "plain" }) {
            JmxReadRequest request =
                    new JmxRequestBuilder(READ,"testDomain:type1=label1,type2=label2")
                    .option(ConfigKey.VALUE_FORMAT,paramVal)
                    .build();
            String value = (String) handler.formatValue(request, "value");
            assertEquals("value", value);
        }
    }

    @Test
    public void formatValueTag() throws MalformedObjectNameException {
        JmxReadRequest request =
                new JmxRequestBuilder(READ,"testDomain:type1=label1,type2=label2")
                        .option(ConfigKey.VALUE_FORMAT,"tag")
                        .build();
        getAndVerifyValueInTagFormat(request);
    }

    @Test
    public void formatValueTagFromConfiguration() throws MalformedObjectNameException {
        handler = new ReadHandler(new AllowAllRestrictor(), new Configuration(ConfigKey.VALUE_FORMAT,"tag"));
        JmxReadRequest request =
                new JmxRequestBuilder(READ,"testDomain:type1=label1,type2=label2")
                        .option(ConfigKey.VALUE_FORMAT,"tag")
                        .build();
        getAndVerifyValueInTagFormat(request);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*bla.*PLAIN.*TAG.*")
    public void invalidValueFormat() throws Exception {
        JmxReadRequest request =
                new JmxRequestBuilder(READ,"testDomain:type1=label1,type2=label2")
                        .option(ConfigKey.VALUE_FORMAT,"bla")
                        .build();
        handler.formatValue(request, "value");
    }
    private void getAndVerifyValueInTagFormat(JmxReadRequest request) throws MalformedObjectNameException {
        JSONObject value = (JSONObject) handler.formatValue(request, "value");
        assertEquals(4,value.size());
        assertEquals("testDomain",value.get(ValueFormat.KEY_DOMAIN));
        assertEquals("value",value.get(ValueFormat.KEY_VALUE));
        assertEquals("label1",value.get("type1"));
        assertEquals("label2",value.get("type2"));
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
