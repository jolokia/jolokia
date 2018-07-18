package org.jolokia.http;

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
import java.util.Map;

import javax.management.*;

import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;
import org.jolokia.config.Configuration;
import org.jolokia.backend.BackendManager;
import org.jolokia.request.JmxReadRequest;
import org.jolokia.request.JmxRequest;
import org.jolokia.test.util.HttpTestUtil;
import org.jolokia.util.LogHandler;
import org.jolokia.util.RequestType;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 31.08.11
 */
public class HttpRequestHandlerTest {

    private BackendManager backend;
    private HttpRequestHandler handler;

    @BeforeMethod
    public void setup() {
        backend = createMock(BackendManager.class);
        expect(backend.isDebug()).andReturn(true).anyTimes();

        handler = new HttpRequestHandler(new Configuration(),backend, createDummyLogHandler());
    }

    @AfterMethod
    public void tearDown() {
        verify(backend);
    }

    @Test
    public void accessAllowed() {
        expect(backend.isRemoteAccessAllowed("localhost","127.0.0.1")).andReturn(true);
        expect(backend.isOriginAllowed((String) isNull(), eq(true))).andReturn(true);
        replay(backend);

        handler.checkAccess("localhost", "127.0.0.1",null);
    }

    @Test(expectedExceptions = { SecurityException.class })
    public void accessDenied() {
        expect(backend.isRemoteAccessAllowed("localhost","127.0.0.1")).andReturn(false);
        replay(backend);

        handler.checkAccess("localhost", "127.0.0.1",null);
    }

    @Test(expectedExceptions = { SecurityException.class })
    public void accessDeniedViaOrigin() {
        expect(backend.isRemoteAccessAllowed("localhost","127.0.0.1")).andReturn(true);
        expect(backend.isOriginAllowed("www.jolokia.org",true)).andReturn(false);
        replay(backend);

        handler.checkAccess("localhost", "127.0.0.1","www.jolokia.org");
    }


    @Test
    public void get() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        JSONObject resp = new JSONObject();
        expect(backend.handleRequest(isA(JmxReadRequest.class))).andReturn(resp);
        replay(backend);

        JSONObject response = (JSONObject) handler.handleGetRequest("/jolokia", HttpTestUtil.HEAP_MEMORY_GET_REQUEST, null);
        assertTrue(response == resp);
    }

    @Test
    public void getWithDoubleSlashes() throws MBeanException, AttributeNotFoundException, ReflectionException, InstanceNotFoundException, IOException {
        JSONObject resp = new JSONObject();
        expect(backend.handleRequest(eqReadRequest("read", "bla:type=s/lash/", "attribute"))).andReturn(resp);
        replay(backend);

        JSONObject response = (JSONObject) handler.handleGetRequest("/read/bla%3Atype%3Ds!/lash!//attribute",
                                 "/read/bla:type=s!/lash!/Ok",null);
        assertTrue(response == resp);
    }

    private JmxRequest eqReadRequest(String pType, final String pMBean, final String pAttribute) {
        EasyMock.reportMatcher(new IArgumentMatcher() {
            public boolean matches(Object argument) {
                try {
                    JmxReadRequest req = (JmxReadRequest) argument;
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


    @Test
    public void singlePost() throws IOException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException {
        JSONObject resp = new JSONObject();
        expect(backend.handleRequest(isA(JmxReadRequest.class))).andReturn(resp);
        replay(backend);

        InputStream is = HttpTestUtil.createServletInputStream(HttpTestUtil.HEAP_MEMORY_POST_REQUEST);
        JSONObject response = (JSONObject) handler.handlePostRequest("/jolokia",is,"utf-8",null);
        assertTrue(response == resp);
    }


    @Test
    public void doublePost() throws IOException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException {
        JSONObject resp = new JSONObject();
        expect(backend.handleRequest(isA(JmxReadRequest.class))).andReturn(resp).times(2);
        replay(backend);

        InputStream is = HttpTestUtil.createServletInputStream("[" + HttpTestUtil.HEAP_MEMORY_POST_REQUEST + "," + HttpTestUtil.HEAP_MEMORY_POST_REQUEST + "]");
        JSONArray response = (JSONArray) handler.handlePostRequest("/jolokia", is, "utf-8", null);
        assertEquals(response.size(),2);
        assertTrue(response.get(0) == resp);
        assertTrue(response.get(1) == resp);
    }

    @Test
    public void preflightCheck() {
        String origin = "http://bla.com";
        String headers ="X-Data: Test";
        expect(backend.isOriginAllowed(origin,false)).andReturn(true);
        replay(backend);

        Map<String,String> ret =  handler.handleCorsPreflightRequest(origin, headers);
        assertEquals(ret.get("Access-Control-Allow-Origin"),origin);
    }

    @Test
    public void preflightCheckNegative() {
        String origin = "http://bla.com";
        String headers ="X-Data: Test";
        expect(backend.isOriginAllowed(origin,false)).andReturn(false);
        replay(backend);

        Map<String,String> ret =  handler.handleCorsPreflightRequest(origin, headers);
        assertNull(ret.get("Access-Control-Allow-Origin"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void invalidJson() throws IOException {
        replay(backend);
        InputStream is = HttpTestUtil.createServletInputStream("{ bla;");
        handler.handlePostRequest("/jolokia",is,"utf-8",null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void invalidJson2() throws IOException {
        replay(backend);
        InputStream is = HttpTestUtil.createServletInputStream("12");
        handler.handlePostRequest("/jolokia",is,"utf-8",null);
    }

    @Test
    public void requestErrorHandling() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        Object[] exceptions = new Object[] {
                new ReflectionException(new NullPointerException()), 404,500,
                new InstanceNotFoundException(), 404, 500,
                new MBeanException(new NullPointerException()), 500, 500,
                new AttributeNotFoundException(), 404, 500,
                new UnsupportedOperationException(), 500, 500,
                new IOException(), 500, 500,
                new IllegalArgumentException(), 400, 400,
                new SecurityException(),403, 403,
                new RuntimeMBeanException(new NullPointerException()), 500, 500
        };

        for (int i = 0; i < exceptions.length; i += 3) {
            Exception e = (Exception) exceptions[i];
            reset(backend);
            expect(backend.isDebug()).andReturn(true).anyTimes();
            backend.error(find("" + exceptions[i + 1]), EasyMock.<Throwable>anyObject());
            backend.error(find("" + exceptions[i + 2]), EasyMock.<Throwable>anyObject());
            expect(backend.handleRequest(EasyMock.<JmxRequest>anyObject())).andThrow(e);
            replay(backend);
            JSONObject resp = (JSONObject) handler.handleGetRequest("/jolokia",
                                                                    "/read/java.lang:type=Memory/HeapMemoryUsage",null);
            assertEquals(resp.get("status"),exceptions[i+1]);

            resp = handler.handleThrowable(e);
            assertEquals(resp.get("status"),exceptions[i+2],e.getClass().getName());
        }
    }

    // ======================================================================================================

    private LogHandler createDummyLogHandler() {
        return new LogHandler() {
                public void debug(String message) {
                }

                public void info(String message) {
                }

                public void error(String message, Throwable t) {
                }
            };
    }
}
