package org.jolokia.detector;

import java.lang.instrument.Instrumentation;
import java.util.Arrays;
import java.util.HashSet;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

public class LightstreamerDetectorTest extends BaseDetectorTest {

    LightstreamerDetector detector = new LightstreamerDetector();

    @Test
    public void testDetect() throws MalformedObjectNameException, AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException {
        String version = "6.0.1";
        MBeanServer mockServer = createMock(MBeanServer.class);
        ObjectName oName = new ObjectName("com.lightstreamer:type=Server");
        expect(mockServer.queryNames(new ObjectName("com.lightstreamer:type=Server"), null)).
                andReturn(new HashSet<ObjectName>(Arrays.asList(oName))).anyTimes();
        expect(mockServer.isRegistered(oName)).andStubReturn(true);
        expect(mockServer.getAttribute(oName,"LSVersion")).andStubReturn(version);
        replay(mockServer);

        ServerHandle handle = detector.detect(getMBeanServerManager(mockServer));
        assertNotNull(handle);
        assertEquals(handle.getVersion(),version);
    }
    
    @Test
    public void testIsLightStreamer_NotFound() {
        Instrumentation mockInstrumentation = createMock(Instrumentation.class);
        expect(mockInstrumentation.getAllLoadedClasses()).andReturn(new Class[0]);
        replay(mockInstrumentation);
        assertFalse(detector.isLightStreamer(mockInstrumentation));
    }
    
    @Test
    public void testIsLightStreamer_SystemPropertyFound() {
        String systemPropertyName = "com.lightstreamer.kernel_lib_path";
        System.setProperty(systemPropertyName, "path");
        try {
            Instrumentation mockInstrumentation = createMock(Instrumentation.class);
            assertTrue(detector.isLightStreamer(mockInstrumentation));
        } finally {
            System.clearProperty(systemPropertyName);
        }
    }
}
