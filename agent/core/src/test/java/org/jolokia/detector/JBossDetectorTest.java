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

import java.util.*;

import javax.management.*;

import org.jolokia.request.JmxRequest;
import org.jolokia.request.JmxRequestBuilder;
import org.jolokia.util.RequestType;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * @author roland
 * @since 02.09.11
 */
public class JBossDetectorTest {


    private JBossDetector detector;
    private MBeanServer server;
    private HashSet<MBeanServer> servers;

    @BeforeMethod
    public void setup() {
        detector = new JBossDetector();

        server = createMock(MBeanServer.class);
        servers = new HashSet<MBeanServer>(Arrays.asList(server));

    }

    @Test
    public void simpleNotFound() throws MalformedObjectNameException {

        expect(server.queryNames(new ObjectName("jboss.system:type=Server"),null)).andReturn(null);
        expect(server.queryNames(new ObjectName("jboss.modules:*"),null)).andReturn(null);
        replay(server);
        assertNull(detector.detect(servers));
        verify(server);
    }

    @Test
    public void simpleFound() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IntrospectionException {


        ObjectName oName = prepareQuery("jboss.system:type=Server");
        expect(server.getAttribute(oName,"Version")).andReturn("5.1.0");
        replay(server);
        ServerHandle handle = detector.detect(servers);
        assertEquals(handle.getVersion(),"5.1.0");
        assertEquals(handle.getVendor(),"RedHat");
        assertEquals(handle.getProduct(),"jboss");


        // Verify workaround
        reset(server);
        ObjectName memoryBean = new ObjectName("java.lang:type=Memory");
        expect(server.getMBeanInfo(memoryBean)).andReturn(null);
        replay(server);
        handle.preDispatch(servers, new JmxRequestBuilder(RequestType.READ, memoryBean).attribute("HeapMemoryUsage").<JmxRequest>build());
        verify(server);
    }

    @Test
    public void version7() throws MalformedObjectNameException, IntrospectionException, InstanceNotFoundException, ReflectionException {

        expect(server.queryNames(new ObjectName("jboss.system:type=Server"),null)).andReturn(null);
        prepareQuery("jboss.modules:*");
        replay(server);
        ServerHandle handle = detector.detect(servers);
        assertEquals(handle.getVersion(),"7");
        assertEquals(handle.getVendor(),"RedHat");
        assertEquals(handle.getProduct(),"jboss");

        // Verify that no workaround is active
        reset(server);
        ObjectName memoryBean = new ObjectName("java.lang:type=Memory");
        replay(server);
        handle.preDispatch(servers, new JmxRequestBuilder(RequestType.READ, memoryBean).attribute("HeapMemoryUsage").<JmxRequest>build());
        verify(server);
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
        detector.addMBeanServers(servers);
    }


}
