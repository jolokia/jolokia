/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.service.jmx.handler;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;

import javax.management.*;

import org.jolokia.server.core.request.*;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.testng.annotations.*;

import static org.jolokia.server.core.util.RequestType.EXEC;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 19.04.11
 */
public class ExecHandlerTest {

    private ExecHandler handler;

    private ObjectName oName;

    @BeforeMethod
    public void createHandler() {
        TestJolokiaContext ctx = new TestJolokiaContext();
        handler = new ExecHandler();
        handler.init(ctx, null);
    }

    @BeforeClass
    public void registerMBean() throws MalformedObjectNameException, MBeanException, InstanceAlreadyExistsException, IOException, NotCompliantMBeanException, ReflectionException {
        oName = new ObjectName("jolokia:test=exec");

        MBeanServerConnection conn = getMBeanServer();
        conn.createMBean(ExecData.class.getName(),oName);
    }

    @AfterClass
    public void unregisterMBean() throws InstanceNotFoundException, MBeanRegistrationException, IOException {
        MBeanServerConnection conn = getMBeanServer();
        conn.unregisterMBean(oName);
    }

    @Test
    public void simple() throws JMException, IOException, NotChangedException, EmptyResponseException, BadRequestException {
        JolokiaExecRequest request = new JolokiaRequestBuilder(EXEC, oName).
                operation("simple").
                build();
        assertEquals(handler.getType(), EXEC);
        Object res = handler.handleSingleServerRequest(getMBeanServer(), request);
        assertNull(res);
    }

    @Test
    public void simpleWithArguments() throws JMException, IOException, NotChangedException, EmptyResponseException, BadRequestException {
        JolokiaExecRequest request = new JolokiaRequestBuilder(EXEC, oName).
                operation("simpleWithArguments").
                arguments("2025-09-26T13:36:42").
                build();
        assertEquals(handler.getType(), EXEC);
        Object res = handler.handleSingleServerRequest(getMBeanServer(), request);
        assertTrue(res instanceof Date);
    }

    @Test
    public void simpleWithNullable() throws JMException, IOException, NotChangedException, EmptyResponseException, BadRequestException {
        JolokiaExecRequest request = new JolokiaRequestBuilder(EXEC, oName).
                operation("simpleWithNullable").
                arguments("v1").
                build();
        Object res = handler.handleSingleServerRequest(getMBeanServer(), request);
        assertEquals(res, "v1");

        request = new JolokiaRequestBuilder(EXEC, oName).
                operation("simpleWithNullable").
                arguments("null").
                build();
        res = handler.handleSingleServerRequest(getMBeanServer(), request);
        assertEquals(res, "null");

        request = new JolokiaRequestBuilder(EXEC, oName).
                operation("simpleWithNullable").
                arguments(new Object[] { null }).
                build();
        res = handler.handleSingleServerRequest(getMBeanServer(), request);
        assertEquals(res, "<null>");
    }

    @Test
    public void simpleWithWrongArgumentsThatWillBeTreatedAsPath() throws JMException, IOException, NotChangedException, EmptyResponseException, BadRequestException {
        JolokiaExecRequest request = new JolokiaRequestBuilder(EXEC, oName).
                operation("simple").
                arguments("blub","bla").
                build();
        handler.handleSingleServerRequest(getMBeanServer(), request);
    }

    @Test(expectedExceptions = { BadRequestException.class })
    public void illegalRequest() throws JMException, IOException, NotChangedException, EmptyResponseException, BadRequestException {
        JolokiaExecRequest request = new JolokiaRequestBuilder(EXEC, oName).build();
        handler.handleSingleServerRequest(getMBeanServer(), request);
    }

    @Test(expectedExceptions = { ReflectionException.class })
    public void illegalOperationName() throws JMException, IOException, NotChangedException, EmptyResponseException, BadRequestException {
        JolokiaExecRequest request = new JolokiaRequestBuilder(EXEC, oName).operation("koan titel").build();
        handler.handleSingleServerRequest(getMBeanServer(), request);
    }

    @Test
    public void execWithArgumentsAndReturn() throws Exception {
        List<String> list = new ArrayList<>();
        list.add("wollscheid");

        JolokiaExecRequest request = new JolokiaRequestBuilder(EXEC,oName).
                operation("withArgs").
                arguments(10L,list,Boolean.TRUE)
                .build();
        @SuppressWarnings("unchecked")
        Map<String, ?> result = (Map<String, ?>) handler.handleSingleServerRequest(getMBeanServer(), request);
        assertEquals(result.get("long"),10L);
        assertTrue(result.get("list") instanceof List);
        assertEquals(((List<?>) result.get("list")).get(0), "wollscheid");
        assertTrue((Boolean) result.get("boolean"));
    }

    @Test(expectedExceptions = { BadRequestException.class })
    public void overloadedFailed() throws JMException, IOException, NotChangedException, EmptyResponseException, BadRequestException {
        JolokiaExecRequest request = new JolokiaRequestBuilder(EXEC,oName).
                operation("overloaded").
                build();
        handler.handleSingleServerRequest(getMBeanServer(), request);
    }

    @Test
    public void overloaded() throws JMException, IOException, NotChangedException, EmptyResponseException, BadRequestException {
        JolokiaExecRequest request = new JolokiaRequestBuilder(EXEC,oName).
                operation("overloaded(int)").
                arguments(10).
                build();
        Integer res = (Integer) handler.handleSingleServerRequest(getMBeanServer(), request);
        assertEquals(res,Integer.valueOf(1));

        request = new JolokiaRequestBuilder(EXEC,oName).
                operation("overloaded(int,java.lang.String)").
                arguments(10,"bla").
                build();
        res = (Integer) handler.handleSingleServerRequest(getMBeanServer(), request);
        assertEquals(res,Integer.valueOf(2));

        request = new JolokiaRequestBuilder(EXEC,oName).
                operation("overloaded(boolean)").
                arguments(true).
                build();
        res = (Integer) handler.handleSingleServerRequest(getMBeanServer(), request);
        assertEquals(res,Integer.valueOf(3));
    }

    @Test(expectedExceptions = { BadRequestException.class })
    public void overloadedWrongSignature() throws JMException, IOException, NotChangedException, EmptyResponseException, BadRequestException {
        JolokiaExecRequest request = new JolokiaRequestBuilder(EXEC,oName).
                operation("overloaded(java.lang.Integer)").
                arguments(1).
                build();
        handler.handleSingleServerRequest(getMBeanServer(), request);
    }

    private MBeanServerConnection getMBeanServer() {
        return ManagementFactory.getPlatformMBeanServer();
    }

}
