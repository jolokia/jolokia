package org.jolokia.backend;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.management.*;

import org.jolokia.*;
import org.jolokia.ConfigKey;
import org.jolokia.config.Restrictor;
import org.jolokia.converter.StringToObjectConverter;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.LogHandler;
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
    public void simplRead() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        backendManager = new BackendManager(new HashMap(),this);
        JmxRequest req = new JmxRequestBuilder(JmxRequest.Type.READ,"java.lang:type=Memory")
                .attribute("HeapMemoryUsage")
                .build();
        JSONObject ret = backendManager.handleRequest(req);
        assertTrue(Long.parseLong( (String) ((Map) ret.get("value")).get("used")) > 0);
        backendManager.destroy();
    }


    @Test
    public void requestDispatcher() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        Map<ConfigKey,String> config = new HashMap<ConfigKey, String>();
        config.put(ConfigKey.DISPATCHER_CLASSES,RequestDispatcherTest.class.getName());
        backendManager = new BackendManager(config,this);
        JmxRequest req = new JmxRequestBuilder(JmxRequest.Type.READ,"java.lang:type=Memory").build();
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

        public RequestDispatcherTest(ObjectToJsonConverter pObjectToJsonConverter,StringToObjectConverter pStringConverter,Restrictor pRestrictor) {
            assertNotNull(pObjectToJsonConverter);
            assertNotNull(pStringConverter);
            assertNotNull(pRestrictor);
        }

        public Object dispatchRequest(JmxRequest pJmxReq) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
            called = true;
            if (pJmxReq.getType() == JmxRequest.Type.READ) {
                return new JSONObject();
            } else if (pJmxReq.getType() == JmxRequest.Type.WRITE) {
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
