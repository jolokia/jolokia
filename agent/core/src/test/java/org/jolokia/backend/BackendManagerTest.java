package org.jolokia.backend;

/*
 *  Copyright 2009-2010 Roland Huss
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
import java.util.*;

import javax.management.*;

import org.jolokia.converter.Converters;
import org.jolokia.util.*;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.detector.ServerHandle;
import org.jolokia.request.*;
import org.json.simple.JSONObject;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since Jun 15, 2010
 */
public class BackendManagerTest implements LogHandler {

    BackendManager backendManager;

    @Test
    public void simpleRead() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        backendManager = new BackendManager(new HashMap(),this);
        JmxRequest req = new JmxRequestBuilder(RequestType.READ,"java.lang:type=Memory")
                .attribute("HeapMemoryUsage")
                .build();
        JSONObject ret = backendManager.handleRequest(req);
        assertTrue((Long) ((Map) ret.get("value")).get("used") > 0);
        backendManager.destroy();
    }


    @Test
    public void requestDispatcher() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        Map<ConfigKey,String> config = new HashMap<ConfigKey, String>();
        config.put(ConfigKey.DISPATCHER_CLASSES,RequestDispatcherTest.class.getName());
        backendManager = new BackendManager(config,this);
        JmxRequest req = new JmxRequestBuilder(RequestType.READ,"java.lang:type=Memory").build();
        JSONObject ret = backendManager.handleRequest(req);
        assertTrue(RequestDispatcherTest.called);
        backendManager.destroy();
    }

    @Test
    public void requestDispatcherWithWrongDispatcher() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        try {
            Map<ConfigKey,String> config = new HashMap<ConfigKey, String>();
            config.put(ConfigKey.DISPATCHER_CLASSES,RequestDispatcherWrong.class.getName());
            backendManager = new BackendManager(config,this);
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("invalid constructor"));
        }
    }

    public void debug(String message) {
        System.out.println("D> " + message);
    }

    public void info(String message) {
        System.out.println("I> " + message);
    }

    public void error(String message, Throwable t) {
        System.out.println("E> " + message);
        t.printStackTrace(System.out);
    }

    // =========================================================================================

    static class RequestDispatcherTest implements RequestDispatcher {

        static boolean called = false;

        public RequestDispatcherTest(Converters pConverters,ServerHandle pServerHandle,Restrictor pRestrictor) {
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
    }

    // ========================================================

    static class RequestDispatcherWrong implements RequestDispatcher {

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
    }

}
