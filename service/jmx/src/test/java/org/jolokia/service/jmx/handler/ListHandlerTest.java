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

import org.easymock.EasyMock;
import org.jolokia.core.request.NotChangedException;
import org.jolokia.core.config.ConfigKey;
import org.jolokia.core.util.jmx.DefaultMBeanServerAccess;
import org.jolokia.core.util.jmx.MBeanServerAccess;
import org.jolokia.core.request.JolokiaListRequest;
import org.jolokia.core.request.JolokiaRequestBuilder;
import org.jolokia.core.util.RequestType;
import org.jolokia.core.util.TestJolokiaContext;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 11.04.11
 */
public class ListHandlerTest extends BaseHandlerTest {

    private ListHandler handler;
    private ListHandler handlerWithRealm;

    private MBeanServerAccess executor;

    private TestJolokiaContext ctx;

    @BeforeMethod
    public void createHandler() throws MalformedObjectNameException {
        ctx = new TestJolokiaContext();
        handler = new ListHandler(ctx, null);
        handlerWithRealm = new ListHandler(ctx, "proxy");
        executor = new DefaultMBeanServerAccess();
    }

    @AfterMethod
    private void destroy() throws JMException {
        executor.destroy();
    }

    @Test
    public void singleSlashPath() throws Exception {
        for (String p : new String[]{null, "", "/"}) {
            JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).path(p).build();

            Map res = execute(handler,request);
            assertTrue(res.containsKey("java.lang"));
            assertTrue(res.get("java.lang") instanceof Map);

            res = execute(handlerWithRealm,request);
            assertTrue(res.containsKey("proxy@java.lang"));
            assertTrue(res.get("proxy@java.lang") instanceof Map);

            Map baseMap = createMap("first","second");
            res = execute(handler,request,baseMap);
            assertTrue(res.containsKey("java.lang"));
            assertEquals(res.get("first"), "second");
        }
    }

    private Map createMap(String... args) {
        Map map = new HashMap();
        for (int i = 0; i < args.length; i+=2) {
            map.put(args[i], args[i + 1]);
        }
        return map;
    }

    @Test
    public void domainPath() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang").build();
        Map res = execute(handler, request);
        assertTrue(res.containsKey("type=Memory"));
        assertTrue(res.get("type=Memory") instanceof Map); 

        res = execute(handlerWithRealm,request);
        assertEquals(res.size(),0);

        res = execute(handlerWithRealm, request, createMap("first","second"));
        assertEquals(res.size(),1);
        assertTrue(res.containsKey("first"));
    }

    @Test
    public void propertiesPath() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang", "type=Memory").build();
        Map res = execute(handler, request);
        checkKeys(res, "desc", "op", "attr");

        res = execute(handlerWithRealm,request);
        checkKeys(res);
    }

    private void checkKeys(Map pRes, String ... pKeys) {
        for (String k : pKeys) {
            assertTrue(pRes.containsKey(k));
        }
        assertEquals(pRes.size(), pKeys.length);
    }

    @Test
    public void attrPath() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory","attr").build();

        Map res = execute(handler, request);
        assertTrue(res.containsKey("HeapMemoryUsage"));

        res = execute(handlerWithRealm, request);
        checkKeys(res);

        request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("proxy@java.lang","type=Memory","attr").build();
        res = execute(handlerWithRealm, request);
        assertTrue(res.containsKey("HeapMemoryUsage"));
    }

    @Test
    public void descPath() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory","desc").build();
        String res = (String) handler.handleRequest(executor, request, null);
        assertNotNull(res);
    }

    @Test
    public void descPathWithDepth() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang","type=Memory","desc")
                .option(ConfigKey.MAX_DEPTH,"4")
                .build();
        String res = (String) handler.handleRequest(executor, request, null);
        assertNotNull(res);
    }

    @Test
    public void opPath() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory","op").build();
        Map res = execute(handler, request);
        assertTrue(res.containsKey("gc"));
    }

    @Test
    public void maxDepth1() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).option(ConfigKey.MAX_DEPTH,"1").build();
        Map res = execute(handler, request);
        assertTrue(res.containsKey("java.lang"));
        assertFalse(res.get("java.lang") instanceof Map);
    }

    @Test
    public void maxDepth2() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).option(ConfigKey.MAX_DEPTH,"2").build();
        Map res = execute(handler, request);
        assertTrue(res.containsKey("java.lang"));
        Map inner = (Map) res.get("java.lang");
        assertTrue(inner.containsKey("type=Memory"));
        assertFalse(inner.get("type=Memory") instanceof Map);
    }


    @Test
    public void maxDepthAndPath() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory")
                .option(ConfigKey.MAX_DEPTH, "3").build();
        Map res =  execute(handler, request);
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
    public void leafValue() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang", "type=Memory", "desc").build();
        String value = (String) handler.handleRequest(executor, request, null);
        assertNotNull(value);

        String newValue = (String) handler.handleRequest(executor, request, createMap("first","second"));
        assertEquals(newValue,value);
    }

    @Test
    public void keyOrder() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).option(ConfigKey.CANONICAL_NAMING,"true").build();
        Map res = execute(handler, request);
        Map<String,?> mbeans = (Map<String,?>) res.get("java.lang");
        for (String key : mbeans.keySet()) {
            String parts[] = key.split(",");
            String partsSorted[] = parts.clone();
            Arrays.sort(partsSorted);
            assertEquals(parts,partsSorted);
        }
    }

    @Test
    public void truncatedList() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang", "type=Runtime").build();
        Map res = execute(handler, request);
        assertFalse(res.containsKey("op"));
        assertEquals(res.size(),2);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void invalidPath() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Memory", "attr", "unknownAttribute")
                .build();
        execute(handler, request);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void invalidPath2() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Runtime", "op", "bla")
                .option(ConfigKey.MAX_DEPTH,"3")
                .build();
        execute(handler, request);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void invalidPath3() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Runtime", "bla")
                .option(ConfigKey.MAX_DEPTH,"3")
                .build();
        execute(handler, request);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void invalidPath4() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=*")
                .build();
        execute(handler, request);
    }

    @Test
    public void invalidPath5() throws Exception {
        for (String what : new String[] { "attr", "op", "not" }) {
            try {
                JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                        .pathParts("java.lang", "type=Memory", what, "HeapMemoryUsage", "bla")
                        .build();
                execute(handler, request);
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("bla"));
            }
        }
    }

    @Test(expectedExceptions = { IllegalArgumentException.class }, expectedExceptionsMessageRegExp = ".*bla.*")
    public void invalidPath8() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Memory", "desc", "bla")
                .build();
        execute(handler, request);
    }

    private Map execute(ListHandler pHandler, JolokiaListRequest pRequest, Map ... pPreviousResult) throws ReflectionException, InstanceNotFoundException, MBeanException, AttributeNotFoundException, IOException, NotChangedException {
        return (Map) pHandler.handleRequest(executor, pRequest,
                                            pPreviousResult != null && pPreviousResult.length > 0 ? pPreviousResult[0] : null);
    }


    @Test
    public void emptyMaps() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Runtime", "op")
                .build();
        Map res = (Map) handler.handleRequest(executor,request, null);
        assertEquals(res.size(),0);

        request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Runtime", "not")
                .build();
        res = (Map) handler.handleRequest(executor,request, null);
        assertEquals(res.size(),0);
    }

    @Test
    public void singleMBeanMultipleServers() throws MalformedObjectNameException, InstanceNotFoundException, IOException, AttributeNotFoundException, ReflectionException, MBeanException, IntrospectionException, NotChangedException {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Memory", "attr")
                .build();
        MBeanServerConnection dummyConn = EasyMock.createMock(MBeanServerConnection.class);
        Set<MBeanServerConnection> conns = new LinkedHashSet<MBeanServerConnection>();
        conns.add(dummyConn);
        conns.add(ManagementFactory.getPlatformMBeanServer());

        expect(dummyConn.getMBeanInfo(new ObjectName("java.lang:type=Memory"))).andThrow(new InstanceNotFoundException());
        replay(dummyConn);
        Map res = (Map) handler.handleRequest(executor,request, null);
        assertEquals(((Map) res.get("Verbose")).get("type"),"boolean");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*No MBean.*")
    public void noMBeanMultipleServers() throws MalformedObjectNameException, InstanceNotFoundException, IOException, AttributeNotFoundException, ReflectionException, MBeanException, IntrospectionException, NotChangedException {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("bullerbue", "country=sweden")
                .build();
        MBeanServer dummyConn = EasyMock.createMock(MBeanServer.class);

        MBeanServerAccess servers = new DefaultMBeanServerAccess(new HashSet<MBeanServerConnection>(Arrays.asList(dummyConn)));

        ObjectName name = new ObjectName("bullerbue:country=sweden");
        expect(dummyConn.getMBeanInfo(name)).andThrow(new InstanceNotFoundException());
        expect(dummyConn.isRegistered(name)).andReturn(false);
        replay(dummyConn);
        handler.handleRequest(servers,request, null);
    }


}
