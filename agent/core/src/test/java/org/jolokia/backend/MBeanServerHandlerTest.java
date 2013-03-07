package org.jolokia.backend;

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
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import javax.management.*;

import org.easymock.EasyMock;
import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.config.*;
import org.jolokia.config.Configuration;
import org.jolokia.detector.ServerHandle;
import org.jolokia.handler.JsonRequestHandler;
import org.jolokia.request.JmxRequest;
import org.jolokia.request.JmxRequestBuilder;
import org.jolokia.util.LogHandler;
import org.jolokia.util.RequestType;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 02.09.11
 */
public class MBeanServerHandlerTest {

    private JmxRequest request;

    private MBeanServerHandler handler;

    @BeforeMethod
    public void setup() throws MalformedObjectNameException {
        TestDetector.reset();
        Configuration config = new Configuration(ConfigKey.MBEAN_QUALIFIER,"qualifier=test");
        handler = new MBeanServerHandler(config,getEmptyLogHandler());
        request = new JmxRequestBuilder(RequestType.READ,"java.lang:type=Memory").attribute("HeapMemoryUsage").build();
    }

    @AfterMethod
    public void tearDown() throws JMException {
        handler.destroy();
    }

    @Test
    public void dispatchRequest() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IOException, NotChangedException {
        JsonRequestHandler reqHandler = createMock(JsonRequestHandler.class);

        Object result = new Object();

        expect(reqHandler.handleAllServersAtOnce(request)).andReturn(false);
        expect(reqHandler.handleRequest(EasyMock.<MBeanServerConnection>anyObject(), eq(request))).andReturn(result);
        replay(reqHandler);
        assertEquals(handler.dispatchRequest(reqHandler, request),result);
    }


    @Test(expectedExceptions = InstanceNotFoundException.class)
    public void dispatchRequestInstanceNotFound() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IOException, NotChangedException {
        dispatchWithException(new InstanceNotFoundException());
    }


    @Test(expectedExceptions = AttributeNotFoundException.class)
    public void dispatchRequestAttributeNotFound() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IOException, NotChangedException {
        dispatchWithException(new AttributeNotFoundException());
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void dispatchRequestIOException() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IOException, NotChangedException {
        dispatchWithException(new IOException());
    }

    private void dispatchWithException(Exception e) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        JsonRequestHandler reqHandler = createMock(JsonRequestHandler.class);

        expect(reqHandler.handleAllServersAtOnce(request)).andReturn(false);
        expect(reqHandler.handleRequest(EasyMock.<MBeanServerConnection>anyObject(), eq(request))).andThrow(e).anyTimes();
        replay(reqHandler);
        handler.dispatchRequest(reqHandler, request);
    }

    @Test
    public void dispatchAtOnce() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        JsonRequestHandler reqHandler = createMock(JsonRequestHandler.class);

        Object result = new Object();

        expect(reqHandler.handleAllServersAtOnce(request)).andReturn(true);
        expect(reqHandler.handleRequest(isA(MBeanServerExecutor.class), eq(request))).andReturn(result);
        replay(reqHandler);
        assertEquals(handler.dispatchRequest(reqHandler, request),result);
    }

    @Test(expectedExceptions = IllegalStateException.class,expectedExceptionsMessageRegExp = ".*Internal.*")
    public void dispatchAtWithException() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException {
        JsonRequestHandler reqHandler = createMock(JsonRequestHandler.class);

        expect(reqHandler.handleAllServersAtOnce(request)).andReturn(true);
        expect(reqHandler.handleRequest(isA(MBeanServerExecutor.class), eq(request))).andThrow(new IOException());
        replay(reqHandler);
        handler.dispatchRequest(reqHandler, request);
    }


    @Test
    public void mbeanServers() throws MBeanException, IOException, ReflectionException, MalformedObjectNameException {
        checkMBeans(new ObjectName("java.lang:type=Memory"));

        String info = handler.mBeanServersInfo();
        assertTrue(info.contains("Platform MBeanServer"));
        assertTrue(info.contains("type=Memory"));
    }

    @Test
    public void mbeanRegistration() throws JMException, IOException {
        checkMBeans(new ObjectName(handler.getObjectName()));
    }

    private void checkMBeans(ObjectName oName) throws MBeanException, IOException, ReflectionException {
        MBeanServerExecutor servers = handler.getMBeanServerManager();
        final List<Boolean> result = new ArrayList<Boolean>();
        servers.each(oName, new MBeanServerExecutor.MBeanEachCallback() {
            public void callback(MBeanServerConnection pConn, ObjectName pName)
                    throws ReflectionException, InstanceNotFoundException, IOException, MBeanException {
                // Throws an InstanceNotFoundException
                pConn.getObjectInstance(pName);
                result.add(pConn.isRegistered(pName));
            }
        });
        assertTrue(result.contains(Boolean.TRUE), "MBean not registered");
    }

    @Test(expectedExceptions = InstanceNotFoundException.class)
    public void mbeanUnregistrationFailed1() throws JMException {
        handler.registerMBean(new Dummy(false, "test:type=dummy"));
        ManagementFactory.getPlatformMBeanServer().unregisterMBean(new ObjectName("test:type=dummy"));
        handler.destroy();
    }

    @Test(expectedExceptions = JMException.class,expectedExceptionsMessageRegExp = ".*(dummy[12].*){2}.*")
    public void mbeanUnregistrationFailed2() throws JMException {
        handler.registerMBean(new Dummy(false, "test:type=dummy1"));
        handler.registerMBean(new Dummy(false,"test:type=dummy2"));
        ManagementFactory.getPlatformMBeanServer().unregisterMBean(new ObjectName("test:type=dummy1"));
        ManagementFactory.getPlatformMBeanServer().unregisterMBean(new ObjectName("test:type=dummy2"));
        handler.destroy();
    }

    @Test
    public void serverHandle() {
        ServerHandle handle = handler.getServerHandle();
        assertNotNull(handle);
    }

    @Test(expectedExceptions = IllegalStateException.class,expectedExceptionsMessageRegExp = ".*not register.*")
    public void mbeanRegistrationFailed() throws JMException {
        handler.registerMBean(new Dummy(true, "test:type=dummy"));
    }


    // ===================================================================================================


    private LogHandler getEmptyLogHandler() {
        return new LogHandler() {
            public void debug(String message) {
            }

            public void info(String message) {
            }

            public void error(String message, Throwable t) {
            }
        };
    }


    interface DummyMBean {

    }
    private class Dummy implements DummyMBean,MBeanRegistration {

        private boolean throwException;
        private String name;

        public Dummy(boolean b,String pName) {
            throwException = b;
            name = pName;
        }

        public ObjectName preRegister(MBeanServer server, ObjectName pName) throws Exception {
            if (throwException) {
                throw new RuntimeException();
            }
            return new ObjectName(name);
        }

        public void postRegister(Boolean registrationDone) {
        }

        public void preDeregister() throws Exception {
        }

        public void postDeregister() {
        }
    }
}
