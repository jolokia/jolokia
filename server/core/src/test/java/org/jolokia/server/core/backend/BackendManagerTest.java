package org.jolokia.server.core.backend;

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

import org.jolokia.server.core.config.*;
import org.jolokia.server.core.request.*;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.core.api.LogHandler;
import org.jolokia.server.core.service.impl.StdoutLogHandler;
import org.jolokia.server.core.service.request.AbstractRequestHandler;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.server.core.util.*;
import org.jolokia.json.JSONObject;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since Jun 15, 2010
 */
public class BackendManagerTest {

    private final LogHandler log = new StdoutLogHandler(false);

    private TestJolokiaContext createContext(Object ... configKeysAndValues) {
        TestJolokiaContext.Builder builder = new TestJolokiaContext.Builder().services(Serializer.class,new TestSerializer());
        if (configKeysAndValues.length > 0) {
            builder.config(ConfigKey.DEBUG, "true");
        }
        builder.config(ConfigKey.AGENT_ID,"test");
        builder.logHandler(log);
        return builder.build();
    }

    @Test
    public void simpleRead() throws JMException, IOException, EmptyResponseException, BadRequestException {
        JolokiaRequest req = new JolokiaRequestBuilder(RequestType.READ,"java.lang:type=Memory")
                .attribute("HeapMemoryUsage")
                .build();

        BackendManager backendManager = createBackendManager(new Object[]{ConfigKey.DEBUG, "true"},
                                                             createDispatcher(req,"used",123456L));
        JSONObject ret = backendManager.handleRequest(req);
        assertTrue((Long) ((Map<?, ?>) ret.get("value")).get("used") > 0);
    }

    private TestRequestDispatcher createDispatcher(Object ... pReq) {
        TestRequestDispatcher.Builder builder = new TestRequestDispatcher.Builder();
        for (int i = 0; i < pReq.length; i+=3) {
            builder.request((JolokiaRequest) pReq[i]).andReturnMapValue(pReq[i+1],pReq[i+2]);
        }
        return builder.build();
    }

    private BackendManager createBackendManager(Object[] pContextParams, RequestDispatcher dispatcher) {
        JolokiaContext ctx = createContext(pContextParams);
        return new BackendManager(ctx, dispatcher);
    }

    @Test
    public void lazyInit() throws JMException, IOException, EmptyResponseException, BadRequestException {
        JolokiaRequest req = new JolokiaRequestBuilder(RequestType.READ,"java.lang:type=Memory")
                .attribute("HeapMemoryUsage")
                .build();

        BackendManager backendManager = createBackendManager(new Object[0], createDispatcher(req, "used", 123456L));

        JSONObject ret = backendManager.handleRequest(req);
        assertTrue((Long) ((Map<?, ?>) ret.get("value")).get("used") > 0);
    }

   @Test
    public void defaultConfig() {
        BackendManager backendManager = createBackendManager(new Object[] {  ConfigKey.DEBUG_MAX_ENTRIES,"blabal" },createDispatcher());
    }

    @Test
    public void doubleInit() {
        JolokiaContext ctx = createContext();
        RequestDispatcher dispatcher = createDispatcher();
        BackendManager b1 = new BackendManager(ctx, dispatcher);
        BackendManager b2 = new BackendManager(ctx, dispatcher);
    }


    @Test
    public void convertError() throws BadRequestException {
        BackendManager backendManager = createBackendManager(new Object[0],createDispatcher());
        Exception exp = new IllegalArgumentException("Hans",new IllegalStateException("Kalb"));
        JolokiaRequest req = new JolokiaRequestBuilder(RequestType.READ,"java.lang:type=Memory").build();
        JSONObject jsonError = (JSONObject) backendManager.convertExceptionToJson(exp,req);
        assertEquals(jsonError.get("testClass"), IllegalArgumentException.class.toString());
        assertTrue(((String) jsonError.get("testString")).contains("Hans"));
    }

    // =========================================================================================

    static class RequestHandlerTest extends AbstractRequestHandler {

        static boolean called = false;

        public RequestHandlerTest() {
            super("test", 1);
        }

        public <R extends JolokiaRequest> Object handleRequest(R pJmxReq,Object pPrevious) {
            called = true;
            if (pJmxReq.getType() == RequestType.READ) {
                return new JSONObject();
            } else if (pJmxReq.getType() == RequestType.WRITE) {
                return "faultyFormat";
            }
            return null;
        }

        public boolean canHandle(JolokiaRequest pJolokiaRequest) {
            return true;
        }

        public boolean useReturnValueWithPath(JolokiaRequest pJolokiaRequest) {
            return false;
        }
    }

    // ========================================================

    static class RequestHandlerWrong extends AbstractRequestHandler {

        protected RequestHandlerWrong() {
            super("wrong",1);
        }

        // No special constructor --> fail

        public Object handleRequest(JolokiaRequest pJmxReq, Object pPrevious) {
            return null;
        }

        public boolean canHandle(JolokiaRequest pJolokiaRequest) {
            return false;
        }

        public boolean useReturnValueWithPath(JolokiaRequest pJolokiaRequest) {
            return false;
        }
    }

}
