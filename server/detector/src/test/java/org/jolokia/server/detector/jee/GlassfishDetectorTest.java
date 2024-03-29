package org.jolokia.server.detector.jee;

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

import java.util.*;

import javax.management.*;

import org.jolokia.server.core.service.api.ServerHandle;
import org.jolokia.server.core.service.api.LogHandler;
import org.jolokia.server.core.service.request.RequestInterceptor;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author roland
 * @since 06.06.12
 */
public class GlassfishDetectorTest extends BaseDetectorTest {

    @Test
    public void noDetect() throws MalformedObjectNameException {
        detectDeep(null,null);
    }

    @Test
    public void detectFromSystemProperty() throws MalformedObjectNameException {
        detectDeep(" GlassFish v2 ","2");
    }

    @Test
    public void detectWrongVersion() throws MalformedObjectNameException {
        detectDeep(" Blub ",null);
    }

    @Test
    public void detectFromSystemPropertyWithOracle() throws MalformedObjectNameException {
        detectDeep("Oracle Glassfish v3.1.2","3.1.2");
    }

    private void detectDeep(String property,String version) throws MalformedObjectNameException {
        GlassfishDetector detector = createDetector();
        MBeanServer mockServer = createMock(MBeanServer.class);
        expect(mockServer.queryNames(new ObjectName("com.sun.appserv:j2eeType=J2EEServer,*"), null)).
                andReturn(Collections.emptySet()).anyTimes();
        expect(mockServer.queryNames(new ObjectName("amx:type=domain-root,*"),null)).
                andReturn(Collections.emptySet()).anyTimes();
        replay(mockServer);
        if (property != null) {
            System.setProperty("glassfish.version",property);
            if (version == null) {
                assertNull(detector.detect(getMBeanServerManager(mockServer)));
            } else {
                assertEquals(detector.detect(getMBeanServerManager(mockServer)).getVersion(),version);
            }
            System.clearProperty("glassfish.version");
        } else {
            assertNull(detector.detect(getMBeanServerManager(mockServer)));
        }
        verify(mockServer);
    }

    private GlassfishDetector createDetector() {
        return new GlassfishDetector(0);
    }

    @Test
    public void detectFallback() throws InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException, MalformedObjectNameException {
        ObjectName serverMbean = new ObjectName(SERVER_MBEAN);
        MBeanServer mockServer = createMock(MBeanServer.class);

        expect(mockServer.queryNames(new ObjectName("com.sun.appserv:j2eeType=J2EEServer,*"),null)).
                andReturn(new HashSet<>(List.of(serverMbean))).anyTimes();
        expect(mockServer.isRegistered(serverMbean)).andStubReturn(true);
        expect(mockServer.getAttribute(serverMbean, "serverVersion")).andReturn("GlassFish 3x");
        expect(mockServer.queryNames(new ObjectName("com.sun.appserver:type=Host,*"),null)).
                andReturn(new HashSet<>(List.of(serverMbean))).anyTimes();
        expect(mockServer.queryNames(new ObjectName("amx:type=domain-root,*"),null)).andReturn(Collections.emptySet());

        replay(mockServer);

        GlassfishDetector detector = createDetector();
        ServerHandle info = detector.detect(getMBeanServerManager(mockServer));
        assertEquals(info.getVersion(), "3");
        assertEquals(info.getProduct(),"glassfish");
    }

    @Test
    public void detect() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException {
        doPlainDetect(false,null);
    }

    private RequestInterceptor doPlainDetect(boolean booted, Map<String,Object> config) throws MalformedObjectNameException, MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException {
        GlassfishDetector detector = new GlassfishDetector(0);
        detector.init(config);

        ObjectName serverMbean = new ObjectName(SERVER_MBEAN);
        MBeanServer mockServer = createMock(MBeanServer.class);

        expect(mockServer.queryNames(new ObjectName("com.sun.appserv:j2eeType=J2EEServer,*"),null)).
                andReturn(new HashSet<>(List.of(serverMbean))).anyTimes();
        expect(mockServer.isRegistered(serverMbean)).andStubReturn(true);
        expect(mockServer.getAttribute(serverMbean, "serverVersion")).andReturn("GlassFish v3");
        if (booted) {
            expect(mockServer.queryNames(new ObjectName("amx:type=domain-root,*"),null)).andReturn(new HashSet<>(List.of(serverMbean)));
            expect(mockServer.getAttribute(serverMbean,"ApplicationServerFullVersion")).andReturn(" GlassFish v3.1 ");
            replay(mockServer);
            MBeanServerAccess mbeanServers = getMBeanServerManager(mockServer);
            ServerHandle info = detector.detect(mbeanServers);
            assertEquals(info.getVersion(), "3.1");
            assertEquals(info.getProduct(),"glassfish");
            return detector.getRequestInterceptor(mbeanServers);
        } else {
            expect(mockServer.queryNames(new ObjectName("amx:type=domain-root,*"),null)).andReturn(new HashSet<>()).anyTimes();
            expect(mockServer.queryNames(new ObjectName("amx:type=domain-root,*"),null)).andReturn(Collections.emptySet()).anyTimes();
            replay(mockServer);
            MBeanServerAccess mbeanServers = getMBeanServerManager(mockServer);
            ServerHandle info = detector.detect(mbeanServers);
            return detector.getRequestInterceptor(mbeanServers);
        }
    }


    @Test(enabled = false)
    public void postDetectWithPositiveConfig() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException {
        postDetectPositive("{\"glassfish\": {\"bootAmx\" : true}}");
    }

    @Test(enabled = false)
    public void postDetectWithNullConfig() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException {
        postDetectPositive(null);
    }

    @Test
    public void postDetectWithNegativConfig() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException {
        doPlainDetect(false, Collections.singletonMap("bootAmx", Boolean.FALSE));
    }

    private void postDetectPositive(String opts) throws MalformedObjectNameException, MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException {
        RequestInterceptor interceptor = doPlainDetect(false,null);
        MBeanServer mockServer = createMock(MBeanServer.class);
        expect(mockServer.queryNames(new ObjectName("amx:type=domain-root,*"),null)).andReturn(Collections.emptySet()).anyTimes();
        ObjectName bootAmxName = new ObjectName("amx-support:type=boot-amx");
        expect(mockServer.isRegistered(bootAmxName)).andStubReturn(true);
        expect(mockServer.invoke(bootAmxName,"bootAMX",null,null)).andReturn(null);
        replay(mockServer);
        MBeanServerAccess servers = getMBeanServerManager(mockServer);
        interceptor.intercept(null,null);
        verify(mockServer);
    }

    @Test(enabled = false)
    public void detectInstanceNotFoundException() throws MalformedObjectNameException, MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException {
        detectExceptionDuringPostProcess("^.*No bootAmx.*$",new InstanceNotFoundException("Negative"));
    }

    @Test(enabled = false)
    public void detectOtherException() throws MalformedObjectNameException, MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException {
        detectExceptionDuringPostProcess("^.*bootAmx.*$",new MBeanException(new Exception("Negative")));
    }

    private void detectExceptionDuringPostProcess(String regexp,Exception exp) throws MalformedObjectNameException, MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException {
        RequestInterceptor interceptor = doPlainDetect(false,null);
        MBeanServer mockServer = createMock(MBeanServer.class);
        expect(mockServer.queryNames(new ObjectName("amx:type=domain-root,*"),null)).andReturn(Collections.emptySet()).anyTimes();
        ObjectName bootAmxName = new ObjectName("amx-support:type=boot-amx");
        expect(mockServer.isRegistered(bootAmxName)).andStubReturn(true);
        expect(mockServer.invoke(bootAmxName, "bootAMX", null, null)).andThrow(exp);
        LogHandler log = createMock(LogHandler.class);
        log.error(matches(regexp),isA(exp.getClass()));
        replay(mockServer,log);
        MBeanServerAccess servers = getMBeanServerManager(mockServer);
        interceptor.intercept(null,null);
        verify(mockServer);
    }


    private static final String SERVER_MBEAN = "com.sun.appserv:j2eeType=J2EEServer,type=bla";

}
