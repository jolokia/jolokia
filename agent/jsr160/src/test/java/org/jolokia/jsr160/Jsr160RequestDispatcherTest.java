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

import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.config.Configuration;
import org.jolokia.config.ProcessingParameters;
import org.jolokia.converter.Converters;
import org.jolokia.detector.ServerHandle;
import org.jolokia.request.*;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.json.simple.JSONObject;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 02.09.11
 */
public class Jsr160RequestDispatcherTest {

    private Jsr160RequestDispatcher dispatcher;
    private ProcessingParameters procParams;

    @BeforeTest
    private void setup() {
        dispatcher = createDispatcherPointingToLocalMBeanServer();
        procParams = new Configuration().getProcessingParameters(new HashMap<String, String>());
    }

    @Test
    public void canHandle() {
        assertFalse(dispatcher.canHandle(JmxRequestFactory.createGetRequest("/read/java.lang:type=Memory", procParams)));
        JmxRequest req = preparePostReadRequest(null);
        assertTrue(dispatcher.canHandle(req));
    }

    @Test
    public void useReturnValue() {
        assertTrue(dispatcher.useReturnValueWithPath(JmxRequestFactory.createGetRequest("/read/java.lang:type=Memory", procParams)));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void illegalDispatch() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        dispatcher.dispatchRequest(JmxRequestFactory.createGetRequest("/read/java.lang:type=Memory/HeapMemoryUsage", procParams));
    }

    @Test(expectedExceptions = IOException.class)
    public void simpleDispatchFail() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        JmxRequest req = preparePostReadRequest(null);
        getOriginalDispatcher().dispatchRequest(req);
    }

    @Test
    public void simpleDispatch() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        JmxReadRequest req = (JmxReadRequest) preparePostReadRequest(null);
        Map result = (Map) dispatcher.dispatchRequest(req);
        assertTrue(result.containsKey("HeapMemoryUsage"));
    }

    @Test
    public void simpleDispatchForSingleAttribute() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        JmxReadRequest req = preparePostReadRequest(null, "HeapMemoryUsage");
        assertNotNull(dispatcher.dispatchRequest(req));
    }

    @Test
    public void simpleDispatchWithUser() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        System.setProperty("TEST_WITH_USER","roland");
        try {
            JmxRequest req = preparePostReadRequest("roland");
            Map result = (Map) dispatcher.dispatchRequest(req);
            assertTrue(result.containsKey("HeapMemoryUsage"));
        } finally {
            System.clearProperty("TEST_WITH_USER");
        }
    }


    // =========================================================================================================

    private JmxReadRequest preparePostReadRequest(String pUser, String... pAttribute) {
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

        return (JmxReadRequest) JmxRequestFactory.createPostRequest(params, procParams);
    }

    private Jsr160RequestDispatcher createDispatcherPointingToLocalMBeanServer() {
        Converters converters = new Converters();
        ServerHandle handle = new ServerHandle(null,null,null,null,null);
        return  new Jsr160RequestDispatcher(converters,handle,new AllowAllRestrictor()) {
            @Override
            protected Map<String, Object> prepareEnv(Map<String, String> pTargetConfig) {
                Map ret = super.prepareEnv(pTargetConfig);
                if (ret == null) {
                    ret = new HashMap();
                }
                ret.put("jmx.remote.protocol.provider.pkgs","org.jolokia.jsr160");
                return ret;
            }
        };
    }

    private Jsr160RequestDispatcher getOriginalDispatcher() {
        return new Jsr160RequestDispatcher(new Converters(),
                                           new ServerHandle(null,null,null,null,null),
                                           new AllowAllRestrictor());
    }

}
