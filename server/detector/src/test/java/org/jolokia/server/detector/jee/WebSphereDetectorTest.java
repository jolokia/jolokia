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

import java.util.HashSet;
import java.util.List;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.jolokia.server.core.detector.ServerDetector;
import org.jolokia.server.core.service.api.ServerHandle;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 29.11.10
 */
public class WebSphereDetectorTest extends BaseDetectorTest {

    @Test
    public void detect() throws MalformedObjectNameException, InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException {
        ServerDetector detector = new WebsphereDetector(1);
        ObjectName serverMbean = new ObjectName(SERVER_MBEAN);
        MBeanServer mockServer = createMock(MBeanServer.class);
        expect(mockServer.queryNames(new ObjectName("*:j2eeType=J2EEServer,type=Server,*"),null)).
                andStubReturn(new HashSet<>(List.of(serverMbean)));
        expect(mockServer.isRegistered(serverMbean)).andStubReturn(true);
        expect(mockServer.getAttribute(serverMbean,"platformName")).andReturn("IBM WebSphere Application Server");
        expect(mockServer.getAttribute(serverMbean,"serverVersion")).andReturn(SERVER_VERSION_V6);
        replay(mockServer);

        ServerHandle info = detector.detect(getMBeanServerManager(mockServer));
        assertEquals(info.getVendor(),"IBM");
        assertEquals(info.getProduct(),"websphere");
    }



    private static final String SERVER_MBEAN = "WebSphere:cell=bhutNode02Cell,j2eeType=J2EEServer," +
                                               "mbeanIdentifier=cells/bhutNode02Cell/nodes/bhutNode02/servers/server1/server.xml#Server_1245012281417," +
                                               "name=server1,node=bhutNode02,platform=proxy,process=server1,processType=UnManagedProcess," +
                                               "spec=1.0,type=Server,version=6.1.0.33";

    private static final String SERVER_VERSION_V6 =
            "--------------------------------------------------------------------------------\n" +
            "IBM WebSphere Application Server Product Installation Status Report\n" +
            "--------------------------------------------------------------------------------\n" +
            "\n" +
            "Report at date and time November 29, 2010 11:46:41 AM CET\n" +
            "\n" +
            "Installation\n" +
            "--------------------------------------------------------------------------------\n" +
            "Product Directory        /opt/websphere/was61\n" +
            "Version Directory        /opt/websphere/was61/properties/version\n" +
            "DTD Directory            /opt/websphere/was61/properties/version/dtd\n" +
            "Log Directory            /opt/websphere/was61/logs\n" +
            "Backup Directory         /opt/websphere/was61/properties/version/nif/backup\n" +
            "TMP Directory            /tmp\n" +
            "\n" +
            "Product List\n" +
            "--------------------------------------------------------------------------------\n" +
            "ND                       installed\n" +
            "\n" +
            "Installed Product\n" +
            "--------------------------------------------------------------------------------\n" +
            "Name                     IBM WebSphere Application Server - ND\n" +
            "Version                  6.1.0.33\n" +
            "ID                       ND\n" +
            "Build Level              cf331032.09\n" +
            "Build Date               8/14/10\n" +
            "\n" +
            "--------------------------------------------------------------------------------\n" +
            "End Installation Status Report\n" +
            "--------------------------------------------------------------------------------";
}
