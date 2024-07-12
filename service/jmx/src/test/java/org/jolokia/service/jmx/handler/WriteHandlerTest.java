package org.jolokia.service.jmx.handler;

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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.management.*;

import org.jolokia.server.core.request.JolokiaRequestFactory;
import org.jolokia.server.core.request.ProcessingParameters;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.service.serializer.JolokiaSerializer;
import org.jolokia.server.core.request.JolokiaRequestBuilder;
import org.jolokia.server.core.request.JolokiaWriteRequest;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.jolokia.json.JSONObject;
import org.jolokia.json.parser.JSONParser;
import org.testng.annotations.*;

import static org.jolokia.server.core.util.RequestType.WRITE;
import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author roland
 * @since 21.04.11
 */
public class WriteHandlerTest {

    private WriteHandler handler;

    private ObjectName oName;

    @BeforeClass
    public void setup() throws MalformedObjectNameException, MBeanException, InstanceAlreadyExistsException, IOException, NotCompliantMBeanException, ReflectionException {
        oName = new ObjectName("jolokia:test=write");
        MBeanServer server = getMBeanServer();
        server.createMBean(WriteData.class.getName(), oName);
    }

    @BeforeMethod
    public void createHandler() {
        TestJolokiaContext ctx = new TestJolokiaContext.Builder().services(Serializer.class, new JolokiaSerializer()).build();
        handler = new WriteHandler();
        handler.init(ctx, null);
    }

    @AfterTest
    public void unregisterMBean() throws InstanceNotFoundException, MBeanRegistrationException, IOException {
        MBeanServerConnection conn = getMBeanServer();
        conn.unregisterMBean(oName);
    }

    private MBeanServer getMBeanServer() {
        return ManagementFactory.getPlatformMBeanServer();
    }

    @Test
    public void simple() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        JolokiaWriteRequest req = new JolokiaRequestBuilder(WRITE,oName).attribute("Simple").value("10").build();
        handler.doHandleSingleServerRequest(getMBeanServer(), req);
        req = new JolokiaRequestBuilder(WRITE,oName).attribute("Simple").value("20").build();
        Integer ret = (Integer) handler.doHandleSingleServerRequest(getMBeanServer(), req);
        assertEquals(ret, Integer.valueOf(10));
        assertEquals(handler.getType(),WRITE);
    }

    @Test
    public void writeOnly() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        JolokiaWriteRequest req = new JolokiaRequestBuilder(WRITE,oName).attribute("WriteOnly").value("Won't ever read it").build();
        handler.doHandleSingleServerRequest(getMBeanServer(), req);
        req = new JolokiaRequestBuilder(WRITE,oName).attribute("WriteOnly").value("Won't ever read it").build();
        Object ret = handler.doHandleSingleServerRequest(getMBeanServer(), req);
        assertNull(ret);
        assertEquals(handler.getType(),WRITE);
    }

    @Test
    public void map() throws Exception {
        Map<String, Integer> map = new HashMap<>();
        map.put("answer",42);
        JolokiaWriteRequest req = new JolokiaRequestBuilder(WRITE,oName).attribute("Map").value(map).build();
        handler.doHandleSingleServerRequest(getMBeanServer(), req);
        req = new JolokiaRequestBuilder(WRITE,oName).attribute("Map").value(null).build();
        @SuppressWarnings("unchecked")
        Map<String, ?> ret = (Map<String, ?>) handler.doHandleSingleServerRequest(getMBeanServer(), req);
        assertTrue(ret instanceof Map);
        assertEquals(ret.get("answer"), 42);

    }

    @Test(expectedExceptions = {AttributeNotFoundException.class})
    public void invalidAttribute() throws MalformedObjectNameException, InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException {
        JolokiaWriteRequest req = new JolokiaRequestBuilder(WRITE,oName).attribute("ReadOnly").value("Sommer").build();
        handler.doHandleSingleServerRequest(getMBeanServer(), req);
    }

    @Test
    public void invalidValue() throws Exception {
        JolokiaWriteRequest req = new JolokiaRequestBuilder(WRITE,oName).attribute("Boolean").value(10).build();
        handler.doHandleSingleServerRequest(getMBeanServer(), req);
    }

    @Test
    public void primitiveByteArrayValue() throws Exception {
        byte[] arg = new byte[] { (byte) 0x42, (byte) 0x2a };
        JolokiaWriteRequest req = new JolokiaRequestBuilder(WRITE,oName).attribute("PrimitiveBytes").value(arg).build();
        handler.doHandleSingleServerRequest(getMBeanServer(), req);
    }

    @Test
    public void byteArrayValue() throws Exception {
        Byte[] arg = new Byte[] { (byte) 0x42, (byte) 0x2a };
        JolokiaWriteRequest req = new JolokiaRequestBuilder(WRITE,oName).attribute("Bytes").value(arg).build();
        handler.doHandleSingleServerRequest(getMBeanServer(), req);
    }

    @Test
    public void byteArrayRequest() throws Exception {
        String json = "{\"type\":\"write\",\"mbean\":\"jolokia:test=write\",\"attribute\":\"Bytes\"," +
                "\"value\":[42,-42]}";
        JSONParser parser = new JSONParser();
        JSONObject data = parser.parse(json, JSONObject.class);
        JolokiaWriteRequest req = JolokiaRequestFactory.createPostRequest(data, new ProcessingParameters(Collections.emptyMap()));
        handler.doHandleSingleServerRequest(getMBeanServer(), req);
    }

    @Test
    public void primitiveByteArrayRequest() throws Exception {
        String json = "{\"type\":\"write\",\"mbean\":\"jolokia:test=write\",\"attribute\":\"PrimitiveBytes\"," +
                "\"value\":[42,-42]}";
        JSONParser parser = new JSONParser();
        JSONObject data = parser.parse(json, JSONObject.class);
        JolokiaWriteRequest req = JolokiaRequestFactory.createPostRequest(data, new ProcessingParameters(Collections.emptyMap()));
        handler.doHandleSingleServerRequest(getMBeanServer(), req);
    }
}
