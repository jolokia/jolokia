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
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * @author roland
 * @since 02.09.11
 */
public class JBossDetectorTest {


    @Test
    public void simpleNotFound() throws MalformedObjectNameException {
        JBossDetector detector = new JBossDetector();

        MBeanServer server = createMock(MBeanServer.class);
        Set<MBeanServer> servers = new HashSet<MBeanServer>(Arrays.asList(server));
        expect(server.queryNames(getJBossObjectName(),null)).andReturn(null);
        replay(server);
        assertNull(detector.detect(servers));
    }

    @Test
    public void simpleFound() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, IntrospectionException {
        JBossDetector detector = new JBossDetector();

        MBeanServer server = createMock(MBeanServer.class);
        Set<MBeanServer> servers = new HashSet<MBeanServer>(Arrays.asList(server));

        ObjectName oName = getJBossObjectName();
        Set<ObjectName> oNames = new HashSet<ObjectName>(Arrays.asList(oName));
        expect(server.queryNames(oName,null)).andReturn(oNames);
        expect(server.getAttribute(oName,"Version")).andReturn("5.1.0");
        replay(server);
        ServerHandle handle = detector.detect(servers);
        assertEquals(handle.getVersion(),"5.1.0");
        assertEquals(handle.getVendor(),"JBoss");
        assertEquals(handle.getProduct(),"jboss");

        reset(server);
        ObjectName memoryBean = new ObjectName("java.lang:type=Memory");
        expect(server.getMBeanInfo(memoryBean)).andReturn(null);
        replay(server);
        handle.preDispatch(servers, new JmxRequestBuilder(RequestType.READ, memoryBean).attribute("HeapMemoryUsage").<JmxRequest>build());
    }

    @Test
    public void addMBeanServers() {
        JBossDetector detector = new JBossDetector();

        MBeanServer server = createMock(MBeanServer.class);
        Set<MBeanServer> servers = new HashSet<MBeanServer>(Arrays.asList(server));

        replay(server);
        detector.addMBeanServers(servers);
    }



    public ObjectName getJBossObjectName() throws MalformedObjectNameException {
        return new ObjectName("jboss.system:type=Server");
    }
}
