package org.jolokia.server.core.http;

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
import java.io.InputStream;
import java.util.*;

import javax.management.*;

import org.easymock.*;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.*;
import org.jolokia.server.core.restrictor.AllowAllRestrictor;
import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.core.api.LogHandler;
import org.jolokia.server.core.service.impl.StdoutLogHandler;
import org.jolokia.server.core.service.request.RequestHandler;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.server.core.util.*;
import org.jolokia.json.*;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 31.08.11
 */
public class HttpRequestHandlerTest {

    private HttpRequestHandler handler;
    private TestJolokiaContext ctx;
    private RequestHandler requestHandler;

    @Test
    public void accessAllowed() throws Exception {
        Restrictor restrictor = createMock(Restrictor.class);
        expect(restrictor.isRemoteAccessAllowed("localhost", "127.0.0.1")).andReturn(true);
        expect(restrictor.isOriginAllowed(isNull(), eq(true))).andReturn(true);
        expect(restrictor.ignoreScheme()).andReturn(false);
        replay(restrictor);
        init(restrictor);
        handler.checkAccess("http", "localhost", "127.0.0.1", null);
        verify(restrictor);
    }

    @Test
    public void accessAllowedHttpOriginOverHttps() throws Exception {
        Restrictor restrictor = createMock(Restrictor.class);
        expect(restrictor.isRemoteAccessAllowed("localhost", "127.0.0.1")).andReturn(true);
        expect(restrictor.isOriginAllowed("http://www.jolokia.org", true)).andReturn(true);
        expect(restrictor.ignoreScheme()).andReturn(false);
        replay(restrictor);
        init(restrictor);
        handler.checkAccess("https", "localhost", "127.0.0.1", "http://www.jolokia.org");
        verify(restrictor);
    }

    @Test(expectedExceptions = { SecurityException.class })
    public void accessDenied() throws Exception {
        Restrictor restrictor = createMock(Restrictor.class);
        expect(restrictor.isRemoteAccessAllowed("localhost","127.0.0.1")).andReturn(false);
        replay(restrictor);
        init(restrictor);

        handler.checkAccess("http", "localhost", "127.0.0.1", null);
        verify(restrictor);
    }

    @Test(expectedExceptions = { SecurityException.class })
    public void accessDeniedViaOrigin() throws Exception {
        Restrictor restrictor = createMock(Restrictor.class);
        expect(restrictor.isRemoteAccessAllowed("localhost", "127.0.0.1")).andReturn(true);
        expect(restrictor.isOriginAllowed("http://www.jolokia.org", true)).andReturn(false);
        replay(restrictor);
        init(restrictor);

        handler.checkAccess("http", "localhost", "127.0.0.1", "http://www.jolokia.org");
    }

    @Test(expectedExceptions = { SecurityException.class })
    public void accessDeniedHttpsOriginOverHttp() throws Exception {
        Restrictor restrictor = createMock(Restrictor.class);
        expect(restrictor.isRemoteAccessAllowed("localhost", "127.0.0.1")).andReturn(true);
        expect(restrictor.isOriginAllowed("https://www.jolokia.org", true)).andReturn(true);
        expect(restrictor.ignoreScheme()).andReturn(false);
        replay(restrictor);
        init(restrictor);

        handler.checkAccess("http", "localhost", "127.0.0.1", "https://www.jolokia.org");
    }

    @Test
    public void accessAllowedHttpsOriginOverHttp() throws Exception {
        Restrictor restrictor = createMock(Restrictor.class);
        expect(restrictor.isRemoteAccessAllowed("localhost", "127.0.0.1")).andReturn(true);
        expect(restrictor.isOriginAllowed("https://www.jolokia.org", true)).andReturn(true);
        expect(restrictor.ignoreScheme()).andReturn(true);
        replay(restrictor);
        init(restrictor);

        handler.checkAccess("http", "localhost", "127.0.0.1", "https://www.jolokia.org");
    }

    @Test
    public void get() throws Exception {
        prepareDispatcher(JolokiaReadRequest.class);
        JSONObject response = (JSONObject) handler.handleGetRequest("/jolokia", HttpTestUtil.VERSION_GET_REQUEST, null);
        verifyDispatcher(response);
    }

    @Test
    public void getWithDoubleSlashes() throws Exception {
        prepareDispatcher(new String[] { "bla:type=s/lash/", "attribute" });
        JSONObject response = (JSONObject) handler.handleGetRequest("/read/bla%3Atype%3Ds!/lash!//attribute",
                                                                    "/read/bla:type=s!/lash!/Ok", null);
        verifyDispatcher(response);
    }


    @Test
    public void singlePost() throws Exception {
        prepareDispatcher();
        InputStream is = HttpTestUtil.createServletInputStream(HttpTestUtil.VERSION_POST_REQUEST);
        JSONObject response = (JSONObject) handler.handlePostRequest("/jolokia", is, "utf-8", null);
        verifyDispatcher(response);
    }


    @Test
    public void doublePost() throws Exception {
        prepareDispatcher(2, JolokiaReadRequest.class);
        InputStream is = HttpTestUtil.createServletInputStream("[" + HttpTestUtil.VERSION_POST_REQUEST + "," + HttpTestUtil.VERSION_POST_REQUEST + "]");
        JSONArray response = (JSONArray) handler.handlePostRequest("/jolokia", is, "utf-8", null);
        verifyDispatcher(2, response);
    }

    @Test
    public void preflightCheck() {
        String origin = "http://bla.com";
        String headers ="X-Data: Test";
        //expect(backend.isCorsAccessAllowed(origin)).andReturn(true);
        //replay(backend);

        Map<String,String> ret =  handler.handleCorsPreflightRequest(origin, headers);
        assertEquals(ret.get("Access-Control-Allow-Origin"), origin);
    }

    @Test
    public void preflightCheckNegative() throws Exception {
        String origin = "http://bla.com";
        String headers ="X-Data: Test";
        Restrictor restrictor = createMock(Restrictor.class);
        expect(restrictor.isOriginAllowed(origin, false)).andReturn(false);
        replay(restrictor);
        init(restrictor);

        Map<String,String> ret =  handler.handleCorsPreflightRequest(origin, headers);
        assertNull(ret.get("Access-Control-Allow-Origin"));
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void invalidJson() throws Exception {
        //replay(backend);
        init();
        InputStream is = HttpTestUtil.createServletInputStream("{ bla;");
        handler.handlePostRequest("/jolokia", is, "utf-8", null);
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void invalidJson2() throws Exception {
        //replay(backend);
        init();
        InputStream is = HttpTestUtil.createServletInputStream("12");
        handler.handlePostRequest("/jolokia", is, "utf-8", null);
    }

    @Test
    public void requestErrorHandling() throws Exception {
        Object[] exceptions = new Object[]{
            new ReflectionException(new NullPointerException()), 500, 500,
            new InstanceNotFoundException(), 404, 500,
            new MBeanException(new NullPointerException()), 500, 500,
            new AttributeNotFoundException(), 404, 500,
            new UnsupportedOperationException(), 500, 500,
            new IOException(), 500, 500,
            new IllegalArgumentException(), 500, 500,
            new SecurityException(), 403, 403,
            new RuntimeMBeanException(new NullPointerException()), 500, 500,
            new JMException(), 500, 500
        };

        for (int i = 0; i < exceptions.length; i += 3) {
            Exception e = (Exception) exceptions[i];
            System.out.println("Checking " + e.getClass().getName());
            LogHandler log = createMock(LogHandler.class);
            expect(log.isDebug()).andReturn(true).anyTimes();
            log.error(find("" + exceptions[i + 1]), EasyMock.anyObject());
            log.error(find("" + exceptions[i + 2]), EasyMock.anyObject());
            log.debug(anyObject());
            expectLastCall().asStub();
            init(log);
            expect(requestHandler.handleRequest(EasyMock.anyObject(), EasyMock.anyObject())).andThrow(e);
            replay(requestHandler, log);
            JSONObject resp = (JSONObject) handler.handleGetRequest("/jolokia",
                "/read/java.lang:type=Memory/HeapMemoryUsage", null);
            assertEquals(resp.get("status"), exceptions[i + 1]);

            resp = handler.handleThrowable(e);
            assertEquals(resp.get("status"), exceptions[i + 2], e.getClass().getName());
            ctx = null;
        }
    }

    @Test
    public void parameterValidation() throws Exception {
        init();
        Map<String, String[]> input = new HashMap<>();
        input.put(ConfigKey.MAX_DEPTH.getKeyValue(), new String[] { "7" });
        input.put(ConfigKey.LIST_KEYS.getKeyValue(), new String[] { "on" });
        input.put(ConfigKey.INCLUDE_STACKTRACE.getKeyValue(), new String[] { "runtime" });
        ProcessingParameters parameters = handler.getProcessingParameter(input);

        assertEquals(parameters.get(ConfigKey.MAX_DEPTH), "7");
        assertEquals(parameters.get(ConfigKey.LIST_KEYS), "on");
        assertEquals(parameters.get(ConfigKey.INCLUDE_STACKTRACE), "runtime");
    }

    @Test
    public void failedParameterValidation() throws Exception {
        init();
        {
            Map<String, String[]> input = new HashMap<>();
            input.put(ConfigKey.MAX_DEPTH.getKeyValue(), new String[]{"7a"});
            try {
                handler.getProcessingParameter(input);
                fail();
            } catch (BadRequestException e) {
                assertTrue(e.getMessage().contains(ConfigKey.MAX_DEPTH.getKeyValue()));
            }
        }
        {
            Map<String, String[]> input = new HashMap<>();
            input.put(ConfigKey.LIST_KEYS.getKeyValue(), new String[]{"asd"});
            try {
                handler.getProcessingParameter(input);
                fail();
            } catch (BadRequestException e) {
                assertTrue(e.getMessage().contains(ConfigKey.LIST_KEYS.getKeyValue()));
            }
        }
        {
            Map<String, String[]> input = new HashMap<>();
            input.put(ConfigKey.INCLUDE_STACKTRACE.getKeyValue(), new String[]{"!!!"});
            try {
                handler.getProcessingParameter(input);
                fail();
            } catch (BadRequestException e) {
                assertTrue(e.getMessage().contains(ConfigKey.INCLUDE_STACKTRACE.getKeyValue()));
            }
        }
    }

    // ======================================================================================================

    private void init() throws Exception {
        init(new AllowAllRestrictor(),new StdoutLogHandler(false));
    }

    private void init(LogHandler pLogHandler) throws Exception {
        init(new AllowAllRestrictor(),pLogHandler);
    }

    private void init(Restrictor pRestrictor) throws Exception {
        init(pRestrictor,new StdoutLogHandler(false));
    }

    private void init(Restrictor pRestrictor, LogHandler pLogHandler) throws Exception {
        requestHandler = createMock(RequestHandler.class);
        requestHandler.destroy();
        expectLastCall().asStub();
        expect(requestHandler.canHandle(anyObject())).andStubReturn(true);
        expect(requestHandler.compareTo(anyObject())).andStubReturn(1);
        SortedSet<RequestHandler> services = createMock(SortedSet.class);
        expect(services.add(requestHandler)).andStubReturn(true);
        expect(services.iterator()).andStubAnswer(() -> new SingletonIterator<>(requestHandler));
        expect(services.comparator()).andStubReturn(null);
        expect(services.size()).andStubReturn(1);
        replay(services);
        ctx = new TestJolokiaContext.Builder()
                .restrictor(pRestrictor)
                .logHandler(pLogHandler)
                .services(RequestHandler.class,services)
                .services(Serializer.class,new TestSerializer())
                .build();
        handler = new HttpRequestHandler(ctx);
    }

    private void prepareDispatcher() throws Exception {
        prepareDispatcher(1,JolokiaReadRequest.class);
    }

    private void prepareDispatcher(Object pJmxRequest) throws Exception {
        prepareDispatcher(1,pJmxRequest);
    }

    private void prepareDispatcher(int i, Object pRequest) throws Exception {
        init();
        if (pRequest instanceof JolokiaRequest) {
            expect(requestHandler.handleRequest((JolokiaRequest) pRequest,anyObject())).andReturn("hello").times(i);
        }
        else if (pRequest instanceof String[]) {
            String[] a = (String[]) pRequest;
            expect(requestHandler.handleRequest(eqReadRequest(a[0], a[1]),anyObject())).andReturn("hello").times(i);
        } else {
            expect(requestHandler.handleRequest(isA(JolokiaRequest.class),anyObject())).andReturn("hello").times(i);
        }
        replay(requestHandler);
    }

    private void verifyDispatcher(JSONObject pResponse) {
        verifyDispatcher(1,pResponse);
    }

    private void verifyDispatcher(int i, JSONStructure response) {
        if (i == 1) {
            JSONObject val = (JSONObject) ((JSONObject) response).get("value");
            assertEquals(val.get("testString"),"hello");
        } else {
            JSONArray ret = (JSONArray) response;
            assertEquals(ret.size(),i);
            for (int j = 0; j < i; j++) {
                JSONObject resp = (JSONObject) ret.get(j);
                JSONObject val = (JSONObject) resp.get("value");
                assertEquals(val.get("testString"),"hello");
            }
        }
        verify(requestHandler);
    }

    private JolokiaRequest eqReadRequest(final String pMBean, final String pAttribute) {
        EasyMock.reportMatcher(new IArgumentMatcher() {
            public boolean matches(Object argument) {
                try {
                    JolokiaReadRequest req = (JolokiaReadRequest) argument;
                    return req.getType() == RequestType.READ &&
                           new ObjectName(pMBean).equals(req.getObjectName()) &&
                           pAttribute.equals(req.getAttributeName());
                } catch (MalformedObjectNameException e) {
                    return false;
                }
            }

            public void appendTo(StringBuffer buffer) {
                buffer.append("eqReadRequest(mbean = \"");
                buffer.append(pMBean);
                buffer.append("\", attribute = \"");
                buffer.append(pAttribute);
                buffer.append("\")");

            }
        });
        return null;
    }

    private static class SingletonIterator<T> implements Iterator<T> {
        boolean first = true;
        T object;

       private SingletonIterator(T pObject) {
            object = pObject;
        }

        public boolean hasNext() {
            if (first) {
                first = false;
                return true;
            }
            return false;
        }
        public T next() { return object;  }
        public void remove() { }
    }
}
