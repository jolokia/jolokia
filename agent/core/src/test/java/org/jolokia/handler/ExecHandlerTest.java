/*
 * Copyright 2009-2011 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.handler;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import javax.management.*;

import org.jolokia.converter.StringToObjectConverter;
import org.jolokia.request.JmxExecRequest;
import org.jolokia.request.JmxRequestBuilder;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;
import static org.jolokia.util.RequestType.EXEC;
import static org.testng.Assert.assertNull;

/**
 * @author roland
 * @since 19.04.11
 */
public class ExecHandlerTest {

    private ExecHandler handler;

    private ObjectName oName;

    @BeforeMethod
    public void createHandler() throws MalformedObjectNameException {
        StringToObjectConverter converter = new StringToObjectConverter();
        handler = new ExecHandler(new AllowAllRestrictor(),converter);
    }

    @BeforeTest
    public void registerMbean() throws MalformedObjectNameException, MBeanException, InstanceAlreadyExistsException, IOException, NotCompliantMBeanException, ReflectionException {
        oName = new ObjectName("jolokia:test=exec");
;
        MBeanServerConnection conn = getMBeanServer();
        conn.createMBean(ExecData.class.getName(),oName);
        System.out.println("Registering");
    }


    @AfterTest
    public void unregisterMBean() throws InstanceNotFoundException, MBeanRegistrationException, IOException {
        MBeanServerConnection conn = getMBeanServer();
        conn.unregisterMBean(oName);
        System.out.println("Unregistering");
    }

    @Test
    public void simple() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, MBeanException, AttributeNotFoundException {
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
                operation("simple").
                build();

        Object res = handler.handleRequest(getMBeanServer(),request);
        assertNull(res);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void simpleWithWrongArguments() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, MalformedObjectNameException {
        JmxExecRequest request = new JmxRequestBuilder(EXEC, oName).
                operation("simple").
                arguments("blub","bla").
                build();
        handler.handleRequest(getMBeanServer(),request);
    }

    private MBeanServerConnection getMBeanServer() {
        return ManagementFactory.getPlatformMBeanServer();
    }

}

