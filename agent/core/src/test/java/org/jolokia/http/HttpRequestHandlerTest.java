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
import java.util.Arrays;
import java.util.Map;

import javax.management.*;

import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;
import org.jolokia.backend.dispatcher.*;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.request.JmxReadRequest;
import org.jolokia.request.JmxRequest;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.test.util.HttpTestUtil;
import org.jolokia.util.*;
import org.json.simple.*;
import org.testng.annotations.AfterMethod;
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

    @AfterMethod
    public void destroy() throws JMException {
        if (ctx != null) {
            ctx.destroy();
        }
    }

    @Test
    public void accessAllowed() throws JMException {
        Restrictor restrictor = createMock(Restrictor.class);
        expect(restrictor.isRemoteAccessAllowed("localhost","127.0.0.1")).andReturn(true);
        replay(restrictor);
        init(restrictor);
        handler.checkClientIPAccess("localhost", "127.0.0.1");
        verify(restrictor);
    }

    @Test(expectedExceptions = { SecurityException.class })
    public void accessDenied() throws JMException {
        Restrictor restrictor = createMock(Restrictor.class);
        expect(restrictor.isRemoteAccessAllowed("localhost","127.0.0.1")).andReturn(false);
        replay(restrictor);
        init(restrictor);

        handler.checkClientIPAccess("localhost","127.0.0.1");
        verify(restrictor);
    }

    @Test
    public void get() throws JMException, IOException, NotChangedException {
        prepareDispatcher(JmxReadRequest.class);
        JSONObject response = (JSONObject) handler.handleGetRequest("/jolokia", HttpTestUtil.HEAP_MEMORY_GET_REQUEST, null);
        verifyDispatcher(response);
    }

    @Test
    public void getWithDoubleSlashes() throws JMException, IOException, NotChangedException {
        prepareDispatcher(new String[] { "bla:type=s/lash/", "attribute" });
        JSONObject response = (JSONObject) handler.handleGetRequest("/read/bla%3Atype%3Ds!/lash!//attribute",
                                                                    "/read/bla:type=s!/lash!/Ok", null);
        verifyDispatcher(response);
    }


    @Test
    public void singlePost() throws IOException, JMException, NotChangedException {
        prepareDispatcher();
        InputStream is = HttpTestUtil.createServletInputStream(HttpTestUtil.HEAP_MEMORY_POST_REQUEST);
        JSONObject response = (JSONObject) handler.handlePostRequest("/jolokia",is,"utf-8",null);
        verifyDispatcher(response);
    }


    @Test
    public void doublePost() throws IOException, JMException, NotChangedException {
        prepareDispatcher(2,JmxReadRequest.class);
        InputStream is = HttpTestUtil.createServletInputStream("[" + HttpTestUtil.HEAP_MEMORY_POST_REQUEST + "," + HttpTestUtil.HEAP_MEMORY_POST_REQUEST + "]");
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
    public void preflightCheckNegative() throws JMException {
        String origin = "http://bla.com";
        String headers ="X-Data: Test";
        Restrictor restrictor = createMock(Restrictor.class);
        expect(restrictor.isCorsAccessAllowed(origin)).andReturn(false);
        replay(restrictor);
        init(restrictor);

        Map<String,String> ret =  handler.handleCorsPreflightRequest(origin, headers);
        assertNull(ret.get("Access-Control-Allow-Origin"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void invalidJson() throws IOException {
        //replay(backend);
        InputStream is = HttpTestUtil.createServletInputStream("{ bla;");
        handler.handlePostRequest("/jolokia",is,"utf-8",null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void invalidJson2() throws IOException {
        //replay(backend);
        InputStream is = HttpTestUtil.createServletInputStream("12");
        handler.handlePostRequest("/jolokia",is,"utf-8",null);
    }

    @Test
    public void requestErrorHandling() throws JMException, IOException, NotChangedException {
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
            LogHandler log = createMock(LogHandler.class);
            expect(log.isDebug()).andReturn(true).anyTimes();
            log.error(find("" + exceptions[i + 1]), EasyMock.<Throwable>anyObject());
            log.error(find("" + exceptions[i + 2]), EasyMock.<Throwable>anyObject());
            log.debug((String) anyObject());
            expectLastCall().asStub();
            init(log);
            expect(requestHandler.dispatchRequest(EasyMock.<JmxRequest>anyObject())).andThrow(e);
            replay(requestHandler,log);
            JSONObject resp = (JSONObject) handler.handleGetRequest("/jolokia",
                                                                    "/read/java.lang:type=Memory/HeapMemoryUsage",null);
            assertEquals(resp.get("status"),exceptions[i+1]);

            resp = handler.handleThrowable(e);
            assertEquals(resp.get("status"),exceptions[i+2],e.getClass().getName());
            ctx.destroy();
            ctx = null;
        }
    }

    // ======================================================================================================


    private void init() throws JMException {
        init(new AllowAllRestrictor(),new StdoutLogHandler());
    }

    private void init(LogHandler pLogHandler) throws JMException {
        init(new AllowAllRestrictor(),pLogHandler);
    }

    private void init(Restrictor pRestrictor) throws JMException {
        init(pRestrictor,new StdoutLogHandler());
    }

    private void init(Restrictor pRestrictor, LogHandler pLogHandler) throws JMException {
        requestHandler = createMock(RequestHandler.class);
        requestHandler.destroy();
        expectLastCall().asStub();
        expect(requestHandler.canHandle((JmxRequest) anyObject())).andStubReturn(true);
        expect(requestHandler.useReturnValueWithPath((JmxRequest) anyObject())).andStubReturn(false);
        ctx = new TestJolokiaContext.Builder()
                .restrictor(pRestrictor)
                .logHandler(pLogHandler)
                .build();
        RequestDispatcher dispatcher = new RequestDispatcherImpl(Arrays.asList(requestHandler));
        handler = new HttpRequestHandler(ctx, dispatcher, false);
    }




    private void prepareDispatcher() throws JMException, NotChangedException, IOException {
        prepareDispatcher(1,JmxReadRequest.class);
    }

    private void prepareDispatcher(Object pJmxRequest) throws JMException, NotChangedException, IOException {
        prepareDispatcher(1,pJmxRequest);
    }

    private void prepareDispatcher(int i, Object pRequest) throws JMException, IOException, NotChangedException {
        init();
        if (pRequest instanceof JmxRequest) {
            expect(requestHandler.dispatchRequest((JmxRequest) pRequest)).andReturn("hello").times(i);
        }
        else if (pRequest instanceof String[]) {
            String a[] = (String[]) pRequest;
            expect(requestHandler.dispatchRequest(eqReadRequest(a[0],a[1]))).andReturn("hello").times(i);
        } else {
            expect(requestHandler.dispatchRequest(isA((Class<JmxRequest>) pRequest))).andReturn("hello").times(i);
        }
        replay(requestHandler);
    }

    private void verifyDispatcher(JSONObject pResponse) {
        verifyDispatcher(1,pResponse);
    }

    private void verifyDispatcher(int i, JSONAware response) {
        if (i == 1) {
            assertEquals(((JSONObject) response).get("value"),"hello");
        } else {
            JSONArray ret = (JSONArray) response;
            assertEquals(ret.size(),i);
            for (int j = 0; j < i; j++) {
                JSONObject val = (JSONObject) ret.get(j);
                assertEquals(val.get("value"),"hello");
            }
        }
        verify(requestHandler);
    }


    private JmxRequest eqReadRequest(final String pMBean, final String pAttribute) {
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



}
