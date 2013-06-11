package org.jolokia.backend;

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
import java.util.Map;

import javax.management.*;

import org.jolokia.backend.dispatcher.RequestDispatcher;
import org.jolokia.backend.dispatcher.RequestHandler;
import org.jolokia.config.ConfigKey;
import org.jolokia.converter.Converters;
import org.jolokia.detector.ServerHandle;
import org.jolokia.request.JmxRequest;
import org.jolokia.request.JmxRequestBuilder;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.service.JolokiaContext;
import org.jolokia.util.*;
import org.json.simple.JSONObject;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since Jun 15, 2010
 */
public class BackendManagerTest {

    //ConfigurationImpl config;

    private TestJolokiaContext ctx;

    @AfterMethod
    public void destroy() throws JMException {
        if (ctx != null) {
            ctx.destroy();
        }
    }

    private TestJolokiaContext createContext(Object ... configKeysAndValues) {
        TestJolokiaContext.Builder builder = new TestJolokiaContext.Builder();
        if (configKeysAndValues.length > 0) {
                builder.config(ConfigKey.DEBUG, "true");
        }
        ctx = builder.build();
        return ctx;
    }

    @Test
    public void simpleRead() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        BackendManager backendManager = createBackendManager(new Object[] { ConfigKey.DEBUG,"true"}, false);
        JmxRequest req = new JmxRequestBuilder(RequestType.READ,"java.lang:type=Memory")
                .attribute("HeapMemoryUsage")
                .build();
        JSONObject ret = backendManager.handleRequest(req);
        assertTrue((Long) ((Map) ret.get("value")).get("used") > 0);
    }

    private BackendManager createBackendManager(Object[] pContextParams,boolean pLazy) {
        JolokiaContext ctx = createContext(pContextParams);
        return new BackendManager(ctx, new TestRequestDispatcher(ctx), pLazy);
    }


    @Test
    public void lazyInit() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        BackendManager backendManager = createBackendManager(new Object[0],true);

        JmxRequest req = new JmxRequestBuilder(RequestType.READ,"java.lang:type=Memory")
                .attribute("HeapMemoryUsage")
                .build();
        JSONObject ret = backendManager.handleRequest(req);
        assertTrue((Long) ((Map) ret.get("value")).get("used") > 0);
    }

    /* TODO CTX - Request dispatcher lookup test must move elsewhere, this is not the proper place

    @Test
    public void requestDispatcher() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        BackendManager backendManager = new BackendManager(
                createContext(ConfigKey.DISPATCHER_CLASSES,RequestDispatcherTest.class.getName()),
                false);
        JmxRequest req = new JmxRequestBuilder(RequestType.READ,"java.lang:type=Memory").build();
        backendManager.handleRequest(req);
        assertTrue(RequestDispatcherTest.called);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*invalid constructor.*")
    public void requestDispatcherWithWrongDispatcher() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        BackendManager backendManager = new BackendManager(
                createContext(ConfigKey.DISPATCHER_CLASSES,RequestDispatcherWrong.class.getName()),
                false);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*blub.bla.Dispatcher.*")
    public void requestDispatcherWithUnkownDispatcher() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        BackendManager backendManager = new BackendManager(
                createContext(ConfigKey.DISPATCHER_CLASSES,"blub.bla.Dispatcher"),
                false);
    }

    */

    @Test
    public void defaultConfig() {
        BackendManager backendManager = createBackendManager(new Object[] {  ConfigKey.DEBUG_MAX_ENTRIES,"blabal" },false);
    }

    @Test
    public void doubleInit() {
        JolokiaContext ctx = createContext();
        RequestDispatcher dispatcher = new TestRequestDispatcher(ctx);
        BackendManager b1 = new BackendManager(ctx, dispatcher, false);
        BackendManager b2 = new BackendManager(ctx, dispatcher, false);
    }

    @Test
    public void remoteAccessCheck() {
        ctx = new TestJolokiaContext.Builder().restrictor(new AllowAllRestrictor()).build();
        BackendManager backendManager = new BackendManager(ctx, new TestRequestDispatcher(ctx), false);
        assertTrue(backendManager.isRemoteAccessAllowed("localhost", "127.0.0.1"));
    }

    @Test
    public void corsAccessCheck() {
        ctx = new TestJolokiaContext.Builder().restrictor(new AllowAllRestrictor()).build();
        BackendManager backendManager = new BackendManager(ctx, new TestRequestDispatcher(ctx), false);
        assertTrue(backendManager.isCorsAccessAllowed("http://bla.com"));
    }

    @Test
    public void convertError() throws MalformedObjectNameException {
        BackendManager backendManager = createBackendManager(new Object[0], false);
        Exception exp = new IllegalArgumentException("Hans",new IllegalStateException("Kalb"));
        JmxRequest req = new JmxRequestBuilder(RequestType.READ,"java.lang:type=Memory").build();
        JSONObject jsonError = (JSONObject) backendManager.convertExceptionToJson(exp,req);
        assertTrue(!jsonError.containsKey("stackTrace"));
        assertEquals(jsonError.get("message"), "Hans");
        assertEquals(((JSONObject) jsonError.get("cause")).get("message"), "Kalb");
    }

    // =========================================================================================

    static class RequestHandlerTest implements RequestHandler {

        static boolean called = false;

        public RequestHandlerTest(Converters pConverters, ServerHandle pServerHandle, Restrictor pRestrictor) {
            assertNotNull(pConverters);
            assertNotNull(pRestrictor);
        }

        public Object dispatchRequest(JmxRequest pJmxReq) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
            called = true;
            if (pJmxReq.getType() == RequestType.READ) {
                return new JSONObject();
            } else if (pJmxReq.getType() == RequestType.WRITE) {
                return "faultyFormat";
            }
            return null;
        }

        public boolean canHandle(JmxRequest pJmxRequest) {
            return true;
        }

        public boolean useReturnValueWithPath(JmxRequest pJmxRequest) {
            return false;
        }

        public void destroy() throws JMException {
        }
    }

    // ========================================================

    static class RequestHandlerWrong implements RequestHandler {

        // No special constructor --> fail

        public Object dispatchRequest(JmxRequest pJmxReq) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
            return null;
        }

        public boolean canHandle(JmxRequest pJmxRequest) {
            return false;
        }

        public boolean useReturnValueWithPath(JmxRequest pJmxRequest) {
            return false;
        }

        public void destroy() throws JMException {
        }
    }
}
