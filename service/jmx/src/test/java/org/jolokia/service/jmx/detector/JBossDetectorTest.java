package org.jolokia.service.jmx.detector;

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

import java.util.*;

import javax.management.*;

import org.jolokia.core.detector.ServerHandle;
import org.jolokia.core.util.jmx.MBeanServerAccess;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 02.09.11
 */
public class JBossDetectorTest extends BaseDetectorTest {


    private JBossDetector detector;
    private MBeanServer         server;
    private MBeanServerAccess servers;

    @BeforeMethod
    public void setup() {
        detector = new JBossDetector(1);

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
        verify(server);
    }

    @Test
    public void version71() throws MalformedObjectNameException, IntrospectionException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException {

        expect(server.queryNames(new ObjectName("jboss.system:type=Server"),null)).andReturn(Collections.<ObjectName>emptySet());
        prepareQuery("jboss.as:*");
        ObjectName oName = new ObjectName("jboss.as:management-root=server");
        expect(server.getAttribute(oName,"releaseVersion")).andReturn("7.1.1.Final");
        replay(server);
        ServerHandle handle = detector.detect(servers);
        assertEquals(handle.getVersion(),"7.1.1.Final");
        assertEquals(handle.getVendor(),"RedHat");
        assertEquals(handle.getProduct(),"jboss");
        verifyNoWorkaround(handle);


    }

    private void verifyNoWorkaround(ServerHandle pHandle) throws MalformedObjectNameException {
        // Verify that no workaround is active
        reset(server);
        ObjectName memoryBean = new ObjectName("java.lang:type=Memory");
        replay(server);
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

}
