package org.jolokia.detector;
/*
 * 
 * Copyright 2016 Roland Huss
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

import java.util.Arrays;
import java.util.HashSet;

import javax.management.*;

import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 13/04/16
 */
public class TomcatDetectorTest extends BaseDetectorTest {

    @Test
    public void checkDebianTomcat_251() throws MalformedObjectNameException, AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException {
        detectAndVerify("Apache Tomcat/ 8.0.14 (Debian)", "8.0.14");
    }


    private void detectAndVerify(String property, String version) throws MalformedObjectNameException, AttributeNotFoundException, MBeanException, ReflectionException, InstanceNotFoundException {
        MBeanServer mockServer = createMock(MBeanServer.class);
        ObjectName oName = new ObjectName("catalina:type=Server");
        expect(mockServer.queryNames(new ObjectName("*:type=Server"), null)).
                andReturn(new HashSet<ObjectName>(Arrays.asList(oName))).anyTimes();
        expect(mockServer.isRegistered(oName)).andStubReturn(true);
        expect(mockServer.getAttribute(oName,"serverInfo")).andStubReturn(property);
        replay(mockServer);

        TomcatDetector detector = new TomcatDetector();
        ServerHandle handle = detector.detect(getMBeanServerManager(mockServer));
        assertNotNull(handle);
        assertEquals(handle.getVersion(),version);
    }

}
