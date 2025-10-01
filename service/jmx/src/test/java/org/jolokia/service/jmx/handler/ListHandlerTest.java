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
import java.util.function.ToIntFunction;

import javax.management.*;

import org.easymock.EasyMock;
import org.jolokia.json.JSONObject;
import org.jolokia.server.core.request.*;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.service.api.JolokiaService;
import org.jolokia.server.core.service.impl.ClasspathServiceCreator;
import org.jolokia.server.core.service.impl.StdoutLogHandler;
import org.jolokia.server.core.util.jmx.DefaultMBeanServerAccess;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.jolokia.service.jmx.api.CacheKeyProvider;
import org.jolokia.service.jmx.handler.list.DataKeys;
import org.jolokia.service.jmx.handler.list.DataUpdater;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;
import static org.jolokia.service.jmx.handler.list.DataKeys.*;

/**
 * @author roland
 * @since 11.04.11
 */
public class ListHandlerTest extends BaseHandlerTest {

    private ListHandler handler;
    private ListHandler handlerWithRealm;

    private MBeanServerAccess executor;

    @BeforeMethod
    public void createHandler() {
        TestJolokiaContext ctx = new TestJolokiaContext();
        Set<JolokiaService<?>> discovered = new ClasspathServiceCreator(getClass().getClassLoader(), "services").getServices(new StdoutLogHandler());
        for (JolokiaService<?> service : discovered) {
            if (!(service instanceof DataUpdater || service instanceof CacheKeyProvider)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            TreeSet<JolokiaService<?>> set = (TreeSet<JolokiaService<?>>) ctx.getServices().computeIfAbsent(service.getType(),
                t -> new TreeSet<>(Comparator.comparingInt(new ToIntFunction<JolokiaService<?>>() {
                    @Override
                    public int applyAsInt(JolokiaService<?> value) {
                        return value.getOrder();
                    }
                })));
            set.add(service);
        }

        handler = new ListHandler();
        handler.init(ctx, null);
        handlerWithRealm = new ListHandler();
        handlerWithRealm.init(ctx, "proxy");

        executor = new DefaultMBeanServerAccess();
    }

    @Test
    public void singleSlashPath() throws Exception {
        for (String p : new String[]{null, "", "/"}) {
            JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).path(p).build();

            Map<String, ?> res = execute(handler,request);
            assertTrue(res.containsKey("java.lang"));
            assertTrue(res.get("java.lang") instanceof Map);

            res = execute(handlerWithRealm,request);
            assertTrue(res.containsKey("proxy@java.lang"));
            assertTrue(res.get("proxy@java.lang") instanceof Map);

            Map<String, String> baseMap = createMap("first", "second");
            res = execute(handler,request,baseMap);
            assertTrue(res.containsKey("java.lang"));
            assertEquals(res.get("first"), "second");
        }
    }

    private Map<String, String> createMap(String... args) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < args.length; i+=2) {
            map.put(args[i], args[i + 1]);
        }
        return map;
    }

    @Test
    public void domainPath() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang").build();
        Map<String, ?> res = execute(handler, request);
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
        Map<String, ?> res = execute(handler, request);
        checkKeys(res, DESCRIPTION, OPERATIONS, ATTRIBUTES, CLASSNAME, NOTIFICATIONS);

        res = execute(handlerWithRealm,request);
        checkKeys(res);
    }

    @Test
    public void keyListing() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
            .option(ConfigKey.LIST_KEYS, "true")
            .pathParts("java.lang", "type=Memory").build();
        Map<String, ?> res = execute(handler, request);
        checkKeys(res, KEYS);
        JSONObject keys = (JSONObject) res.get("keys");
        assertEquals(keys.get("type"), "Memory");

        res = execute(handlerWithRealm,request);
        checkKeys(res);
    }

    @Test
    public void listCache() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
            .option(ConfigKey.LIST_CACHE, "true").build();
        Map<String, ?> res = execute(handler, request);
        assertEquals(res.size(), 2);
        assertNotNull(res.get("domains"));
        assertNotNull(res.get("cache"));

        JSONObject bufferPoolInfo = (JSONObject) ((JSONObject) res.get("cache")).get("java.nio:BufferPool");
        assertNotNull(bufferPoolInfo);

        String key = (String) ((JSONObject) ((JSONObject) res.get("domains")).get("java.nio")).get("name=direct,type=BufferPool");
        if (key != null) {
            assertEquals(key, "java.nio:BufferPool");
        }
    }

    @Test
    public void discoveredDataUpdater() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang", "type=Memory").build();
        Map<String, ?> res = execute(handler, request);
        checkKeys(res, DESCRIPTION, OPERATIONS, ATTRIBUTES, CLASSNAME, NOTIFICATIONS);
        assertEquals(res.get("isSpecial"), "very very special");

        res = execute(handlerWithRealm,request);
        checkKeys(res);
    }

    private void checkKeys(Map<?, ?> pRes, DataKeys ... pKeys) {
        for (DataKeys k : pKeys) {
            assertTrue(pRes.containsKey(k.getKey()));
        }
        assertTrue(pRes.size() >= pKeys.length);
    }

    @Test
    public void attrPath() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory",
                                                                                           ATTRIBUTES.getKey()).build();

        Map<String, ?> res = execute(handler, request);
        assertTrue(res.containsKey("HeapMemoryUsage"));

        res = execute(handlerWithRealm, request);
        checkKeys(res);

        request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("proxy@java.lang","type=Memory",ATTRIBUTES.getKey())
                                                             .build();
        res = execute(handlerWithRealm, request);
        assertTrue(res.containsKey("HeapMemoryUsage"));
    }

    @Test
    public void descPath() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory",
                                                                                           DESCRIPTION.getKey()).build();
        String res = (String) handler.handleAllServerRequest(executor, request, null);
        assertNotNull(res);
    }

    @Test
    public void descPathWithDepth() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang","type=Memory",DESCRIPTION.getKey())
                .option(ConfigKey.MAX_DEPTH,"4")
                .build();
        String res = (String) handler.handleAllServerRequest(executor, request, null);
        assertNotNull(res);
    }

    @Test
    public void opPath() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory",
                                                                                           OPERATIONS.getKey()).build();
        Map<String, ?> res = execute(handler, request);
        assertTrue(res.containsKey("gc"));
    }

    @Test
    public void maxDepth1() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).option(ConfigKey.MAX_DEPTH,"1").build();
        Map<String, ?> res = execute(handler, request);
        assertTrue(res.containsKey("java.lang"));
        assertFalse(res.get("java.lang") instanceof Map);
    }

    @Test
    public void maxDepth2() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).option(ConfigKey.MAX_DEPTH,"2").build();
        Map<String, ?> res = execute(handler, request);
        assertTrue(res.containsKey("java.lang"));
        Map<?, ?> inner = (Map<?, ?>) res.get("java.lang");
        assertTrue(inner.containsKey("type=Memory"));
        assertFalse(inner.get("type=Memory") instanceof Map);
    }


    @Test
    public void maxDepthAndPath() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang","type=Memory")
                .option(ConfigKey.MAX_DEPTH, "3").build();
        Map<String, ?> res =  execute(handler, request);
        assertEquals(res.size(), 6);
        @SuppressWarnings("unchecked")
        Map<String, ?> ops = (Map<String, ?>) res.get(OPERATIONS.getKey());
        assertTrue(ops.containsKey("gc"));
        assertTrue(ops.get("gc") instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, ?> attrs = (Map<String, ?>) res.get(ATTRIBUTES.getKey());
        String vendor = System.getProperty("java.vendor");
        if (vendor != null && vendor.toUpperCase().contains("IBM")) {
            // let's be flexible here... number differs between 11 and 17
            assertTrue(attrs.size() > 20);
        } else {
            // Java 7 introduces a new attribute 'ObjectName' here
            assertEquals(attrs.size(), attrs.containsKey("ObjectName") ? 5 : 4);
        }
        assertTrue(attrs.get("HeapMemoryUsage") instanceof Map);
        assertTrue(res.get(DESCRIPTION.getKey()) instanceof String);

    }

    @Test
    public void leafValue() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang",
                                                                                           "type=Memory",
                                                                                           DESCRIPTION.getKey())
                                                                                .build();
        String value = (String) handler.handleAllServerRequest(executor, request, null);
        assertNotNull(value);

        String newValue = (String) handler.handleAllServerRequest(executor, request, createMap("first", "second"));
        assertEquals(newValue,value);
    }

    @Test
    public void keyOrder() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).option(ConfigKey.CANONICAL_NAMING,"true").build();
        Map<String, ?> res = execute(handler, request);
        @SuppressWarnings("unchecked")
        Map<String,?> mbeans = (Map<String,?>) res.get("java.lang");
        for (String key : mbeans.keySet()) {
            String[] parts = key.split(",");
            String[] partsSorted = parts.clone();
            Arrays.sort(partsSorted);
            assertEquals(parts,partsSorted);
        }
    }

    @Test
    public void truncatedList() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST).pathParts("java.lang", "type=Runtime").build();
        Map<String, ?> res = execute(handler, request);
        assertFalse(res.containsKey(OPERATIONS.getKey()));
        assertEquals(res.size(),4);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void invalidPath() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Memory", ATTRIBUTES.getKey(), "unknownAttribute")
                .build();
        execute(handler, request);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void invalidPath2() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Runtime", OPERATIONS.getKey(), "bla")
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

    @Test
    public void wildcardPath4() throws Exception {
        // wildcard at level 2 - return objects of java.lang domain at level 1
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=*")
                .build();
        Map<String, ?> response = execute(handler, request);
        assertTrue(response.containsKey("type=Memory"));
        assertTrue(response.containsKey("type=Threading"));

        // wildcard at level 1 - top level response fields are matching domains
        
        request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.*", "type=*")
                .build();
        response = execute(handler, request);
        assertTrue(response.containsKey("java.util.logging"));
        assertTrue(response.containsKey("java.lang"));
        JSONObject javaLang = (JSONObject) response.get("java.lang");
        assertTrue(javaLang.containsKey("type=Threading"));
    }

    @Test
    public void invalidPath5() throws Exception {
        for (DataKeys what : new DataKeys[] {ATTRIBUTES, OPERATIONS, NOTIFICATIONS }) {
            try {
                JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                        .pathParts("java.lang", "type=Memory", what.getKey(), "HeapMemoryUsage", "bla")
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
                .pathParts("java.lang", "type=Memory", DESCRIPTION.getKey(), "bla")
                .build();
        execute(handler, request);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> execute(ListHandler pHandler, JolokiaListRequest pRequest, Map<?, ?> ... pPreviousResult) throws ReflectionException, InstanceNotFoundException, MBeanException, AttributeNotFoundException, IOException, NotChangedException, EmptyResponseException {
        return (Map<String, ?>) pHandler.handleAllServerRequest(executor, pRequest,
                                                     pPreviousResult != null && pPreviousResult.length > 0 ? pPreviousResult[0] : null);
    }


    @Test
    public void emptyMaps() throws Exception {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Runtime", OPERATIONS.getKey())
                .build();
        @SuppressWarnings("unchecked")
        Map<String, ?> res = (Map<String, ?>) handler.handleAllServerRequest(executor, request, null);
        assertEquals(res.size(),0);

        request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Runtime", NOTIFICATIONS.getKey())
                .build();
        //noinspection unchecked
        res = (Map<String, ?>) handler.handleAllServerRequest(executor, request, null);
        assertEquals(res.size(),0);
    }

    @Test
    public void singleMBeanMultipleServers() throws MalformedObjectNameException, InstanceNotFoundException, IOException, AttributeNotFoundException, ReflectionException, MBeanException, IntrospectionException, NotChangedException, EmptyResponseException {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("java.lang", "type=Memory", ATTRIBUTES.getKey())
                .build();
        MBeanServerConnection dummyConn = EasyMock.createMock(MBeanServerConnection.class);
        Set<MBeanServerConnection> conns = new LinkedHashSet<>();
        conns.add(dummyConn);
        conns.add(ManagementFactory.getPlatformMBeanServer());

        expect(dummyConn.getMBeanInfo(new ObjectName("java.lang:type=Memory"))).andThrow(new InstanceNotFoundException());
        replay(dummyConn);
        @SuppressWarnings("unchecked")
        Map<String, ?> res = (Map<String, ?>) handler.handleAllServerRequest(executor, request, null);
        //noinspection unchecked
        assertEquals(((Map<String, ?>) res.get("Verbose")).get(TYPE.getKey()),"boolean");
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*No MBean.*")
    public void noMBeanMultipleServers() throws MalformedObjectNameException, InstanceNotFoundException, IOException, AttributeNotFoundException, ReflectionException, MBeanException, IntrospectionException, NotChangedException, EmptyResponseException {
        JolokiaListRequest request = new JolokiaRequestBuilder(RequestType.LIST)
                .pathParts("bullerbue", "country=sweden")
                .build();
        MBeanServer dummyConn = EasyMock.createMock(MBeanServer.class);

        MBeanServerAccess servers = new DefaultMBeanServerAccess(new HashSet<>(Collections.singletonList(dummyConn)));

        ObjectName name = new ObjectName("bullerbue:country=sweden");
        expect(dummyConn.getMBeanInfo(name)).andThrow(new InstanceNotFoundException());
        expect(dummyConn.isRegistered(name)).andReturn(false);
        replay(dummyConn);
        handler.handleAllServerRequest(servers, request, null);
    }


}
