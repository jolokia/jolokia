package org.jolokia.jsr160;

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
import java.util.HashMap;
import java.util.Map;

import javax.management.*;

import org.jolokia.backend.NotChangedException;
import org.jolokia.request.*;
import org.jolokia.util.TestJolokiaContext;
import org.json.simple.JSONObject;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 02.09.11
 */
@Test(singleThreaded = true)
public class Jsr160RequestHandlerTest {

    private Jsr160RequestHandler dispatcher;
    //private ProcessingParameters procParams;
    private TestJolokiaContext ctx;

    @BeforeMethod
    private void setup() {
        ctx = new TestJolokiaContext.Builder().build();
        dispatcher = new Jsr160RequestHandler(0) {
            @Override
            protected Map<String, Object> prepareEnv(Map<String, String> pTargetConfig) {
                Map ret = super.prepareEnv(pTargetConfig);
                if (ret == null) {
                    ret = new HashMap();
                }
                ret.put("jmx.remote.protocol.provider.pkgs", "org.jolokia.jsr160");
                return ret;
            }
        };
        dispatcher.init(ctx);
    }

    @AfterMethod
    private void destroy() throws JMException {
        dispatcher.destroy();
    }

    @Test
    public void canHandle() {
        assertFalse(dispatcher.canHandle(JmxRequestFactory.createGetRequest("/read/java.lang:type=Memory", new TestProcessingParameters())));
        JolokiaRequest req = preparePostReadRequest(null);
        assertTrue(dispatcher.canHandle(req));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void illegalDispatch() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        dispatcher.handleRequest(JmxRequestFactory.createGetRequest("/read/java.lang:type=Memory/HeapMemoryUsage", new TestProcessingParameters()),null);
    }

    @Test(expectedExceptions = IOException.class)
    public void simpleDispatchFail() throws Exception {
        JolokiaRequest req = preparePostReadRequest(null);
        destroy();
        TestJolokiaContext testCtx;
        testCtx = new TestJolokiaContext.Builder().build();
        Jsr160RequestHandler handler = new Jsr160RequestHandler(0);
        handler.init(testCtx);
        handler.handleRequest(req,null);
        setup();
    }

    @Test
    public void simpleDispatch() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        JolokiaReadRequest req =  preparePostReadRequest(null);
        Map result = (Map) dispatcher.handleRequest(req,null);
        assertTrue(result.containsKey("HeapMemoryUsage"));
    }

    @Test
    public void simpleDispatchForSingleAttribute() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        JolokiaReadRequest req = preparePostReadRequest(null, "HeapMemoryUsage");
        assertNotNull(dispatcher.handleRequest(req,null));
    }

    @Test
    public void simpleDispatchWithUser() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        System.setProperty("TEST_WITH_USER","roland");
        try {
            JolokiaRequest req = preparePostReadRequest("roland");
            Map result = (Map) dispatcher.handleRequest(req,null);
            assertTrue(result.containsKey("HeapMemoryUsage"));
        } finally {
            System.clearProperty("TEST_WITH_USER");
        }
    }


    // =========================================================================================================

    private JolokiaReadRequest preparePostReadRequest(String pUser, String... pAttribute) {
        JSONObject params = new JSONObject();
        JSONObject target = new JSONObject();
        target.put("url","service:jmx:test:///jndi/rmi://localhost:9999/jmxrmi");
        if (pUser != null) {
            target.put("user","roland");
            target.put("password","s!cr!et");
        }
        if (pAttribute != null && pAttribute.length > 0) {
            params.put("attribute",pAttribute[0]);
        }
        params.put("target",target);
        params.put("type","read");
        params.put("mbean","java.lang:type=Memory");

        return (JolokiaReadRequest) JmxRequestFactory.createPostRequest(params, new TestProcessingParameters());
    }

}
