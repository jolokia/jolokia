package org.jolokia.detector;

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

import java.lang.instrument.Instrumentation;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import javax.management.*;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.request.JmxRequest;
import org.jolokia.request.JmxRequestBuilder;
import org.jolokia.util.RequestType;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 02.09.11
 */
public class JBossDetectorTest extends BaseDetectorTest {


    private JBossDetector       detector;
    private MBeanServer         server;
    private MBeanServerExecutor servers;

    @BeforeMethod
    public void setup() {
        detector = new JBossDetector();

        server = createMock(MBeanServer.class);
        servers = getMBeanServerManager(server);
    }

    @Test
    public void simpleNotFound() throws MalformedObjectNameException {

        for (String name : new String[]{
                "jboss.system:type=Server",
                "jboss.as:*",
                "jboss.modules:*"
        }) {
            expect(server.queryNames(new ObjectName(name), null)).andReturn(Collections.<ObjectName>emptySet()).anyTimes();
        }
        replay(server);
        assertNull(detector.detect(servers));
        verify(server);
    }

    @Test
    public void simpleFound() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IntrospectionException {

        expect(server.queryNames(new ObjectName("jboss.as:management-root=server"),null)).andReturn(Collections.EMPTY_SET);
        ObjectName oName = prepareQuery("jboss.system:type=Server");
        expect(server.isRegistered(oName)).andStubReturn(true);
        expect(server.getAttribute(oName, "Version")).andReturn("5.1.0");
        replay(server);
        ServerHandle handle = detector.detect(servers);
        assertEquals(handle.getVersion(),"5.1.0");
        assertEquals(handle.getVendor(),"RedHat");
        assertEquals(handle.getProduct(),"jboss");


        // Verify workaround
        reset(server);
        ObjectName memoryBean = new ObjectName("java.lang:type=Memory");
        expect(server.isRegistered(memoryBean)).andStubReturn(true);
        replay(server);
        handle.preDispatch(servers, new JmxRequestBuilder(RequestType.READ, memoryBean).attribute("HeapMemoryUsage").<JmxRequest>build());
        verify(server);
    }

    @Test
    public void version71() throws MalformedObjectNameException, IntrospectionException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException {

        expect(server.queryNames(new ObjectName("jboss.system:type=Server"),null)).andReturn(Collections.<ObjectName>emptySet());
        prepareQuery("jboss.as:*");
        ObjectName oName = new ObjectName("jboss.as:management-root=server");
        expect(server.getAttribute(oName,"productVersion")).andReturn(null);
        expect(server.getAttribute(oName,"releaseVersion")).andReturn("7.1.1.Final");
        expect(server.getAttribute(oName,"productName")).andReturn(null);
        replay(server);
        ServerHandle handle = detector.detect(servers);
        assertEquals(handle.getVersion(),"7.1.1.Final");
        assertEquals(handle.getVendor(),"RedHat");
        assertEquals(handle.getProduct(),"jboss");
        verifyNoWorkaround(handle);

    }

    @Test
    public void version101() throws MalformedObjectNameException, IntrospectionException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException {

        expect(server.queryNames(new ObjectName("jboss.system:type=Server"),null)).andReturn(Collections.<ObjectName>emptySet());
        prepareQuery("jboss.as:*");
        ObjectName oName = new ObjectName("jboss.as:management-root=server");
        expect(server.getAttribute(oName,"productVersion")).andReturn("10.1.0.Final");
        expect(server.getAttribute(oName,"productName")).andReturn("WildFly Full");
        replay(server);
        ServerHandle handle = detector.detect(servers);
        assertEquals(handle.getVersion(),"10.1.0.Final");
        assertEquals(handle.getVendor(),"RedHat");
        assertEquals(handle.getProduct(),"WildFly Full");
        verifyNoWorkaround(handle);


    }

    private void verifyNoWorkaround(ServerHandle pHandle) throws MalformedObjectNameException {
        // Verify that no workaround is active
        reset(server);
        ObjectName memoryBean = new ObjectName("java.lang:type=Memory");
        replay(server);
        pHandle.preDispatch(servers, new JmxRequestBuilder(RequestType.READ, memoryBean).attribute("HeapMemoryUsage").<JmxRequest>build());
        verify(server);
    }

    @Test
    public void version7() throws MalformedObjectNameException, IntrospectionException, InstanceNotFoundException, ReflectionException {

        expect(server.queryNames(new ObjectName("jboss.system:type=Server"),null)).andReturn(Collections.<ObjectName>emptySet());
        expect(server.queryNames(new ObjectName("jboss.as:*"),null)).andReturn(Collections.<ObjectName>emptySet());
        prepareQuery("jboss.modules:*");
        replay(server);
        ServerHandle handle = detector.detect(servers);
        assertEquals(handle.getVersion(),"7");
        assertEquals(handle.getVendor(),"RedHat");
        assertEquals(handle.getProduct(),"jboss");

        // Verify that no workaround is active
        verifyNoWorkaround(handle);
    }

    private ObjectName prepareQuery(String pName) throws MalformedObjectNameException {
        ObjectName oName = new ObjectName(pName);
        Set<ObjectName> oNames = new HashSet<ObjectName>(Arrays.asList(oName));
        expect(server.queryNames(oName,null)).andReturn(oNames);
        return oName;
    }

    @Test
    public void addMBeanServers() {
        replay(server);
        detector.addMBeanServers(new HashSet<MBeanServerConnection>());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void verifyIsClassLoadedArgumentChecksNullInstrumentation() {
        detector.isClassLoaded("xx", null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void verifyIsClassLoadedArgumentChecks2NullClassname() {
        Instrumentation inst = mock(Instrumentation.class);
        detector.isClassLoaded(null, inst);
    }

    @Test
    public void verifyIsClassLoadedNotLoaded() {
        Instrumentation inst = createMock(Instrumentation.class);
        expect(inst.getAllLoadedClasses()).andReturn(new Class[] {}).once();
        replay(inst);
        assertFalse(detector.isClassLoaded("org.Dummy", inst));
        verify(inst);
    }

    @Test
    public void verifyIsClassLoadedLoaded() {
        Instrumentation inst = createMock(Instrumentation.class);
        expect(inst.getAllLoadedClasses()).andReturn(new Class[] {JBossDetectorTest.class}).once();
        replay(inst);
        assertTrue(detector.isClassLoaded(JBossDetectorTest.class.getName(), inst));
        verify(inst);
    }

    @Test
    public void verifyJvmAgentStartup() throws MalformedURLException {
        Instrumentation inst = createMock(Instrumentation.class);
        expect(inst.getAllLoadedClasses()).andReturn(new Class[] {}).times(3);
        expect(inst.getAllLoadedClasses()).andReturn(new Class[] {JBossDetectorTest.class}).atLeastOnce();
        ClassLoader cl = createMock(ClassLoader.class);
        expect(cl.getResource("org/jboss/modules/Main.class")).andReturn(new URL("http", "dummy", "")).anyTimes();
        String prevPkgValue = System.setProperty("jboss.modules.system.pkgs", "blah");
        String prevLogValue = System.setProperty("java.util.logging.manager", JBossDetectorTest.class.getName());
        replay(inst,cl);

        try {
            detector.jvmAgentStartup(inst, cl);
        } finally {
            resetSysProp(prevLogValue, "java.util.logging.manager");
            resetSysProp(prevPkgValue, "jboss.modules.system.pkgs");
        }
        verify(inst);
    }

    protected void resetSysProp(String prevValue, String key) {
        if (prevValue == null) {
            System.getProperties().remove(key);
        } else {
            System.setProperty(key, prevValue);
        }
    }

}
