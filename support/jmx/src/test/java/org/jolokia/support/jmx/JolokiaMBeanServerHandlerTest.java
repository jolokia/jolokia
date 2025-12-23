package org.jolokia.support.jmx;

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
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanConstructorInfo;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.ModelMBeanInfoSupport;
import javax.management.modelmbean.ModelMBeanNotificationInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;
import javax.management.modelmbean.RequiredModelMBean;
import javax.management.openmbean.CompositeData;

import org.jolokia.service.serializer.JolokiaSerializer;
import org.jolokia.json.JSONObject;
import org.jolokia.json.parser.JSONParser;
import org.jolokia.json.parser.ParseException;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 27.01.13
 */
public class JolokiaMBeanServerHandlerTest {

    @Test
    public void simple() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, MalformedObjectNameException, AttributeNotFoundException, ReflectionException, InstanceNotFoundException, ParseException, InvalidTargetObjectTypeException, NoSuchMethodException, IntrospectionException, IOException {

        MBeanServer server = createJolokiaMBeanServer();

        ObjectName oName = new ObjectName("test:type=jsonMBean");
        server.registerMBean(new JsonAnnoTest(),oName);

            CompositeData chiliCD = (CompositeData) server.getAttribute(oName,"Chili");
            assertEquals((String) chiliCD.get("name"), "Bhut Jolokia");
            assertEquals(chiliCD.get("scoville"), 1000000L);

            MBeanServer pServer = ManagementFactory.getPlatformMBeanServer();
            String chiliS = (String) pServer.getAttribute(oName,"Chili");
            JSONObject chiliJ = new JSONParser().parse(chiliS, JSONObject.class);
            assertEquals(chiliJ.get("name"), "Bhut Jolokia");
            assertEquals(chiliJ.get("scoville"), 1000000L);

            server.unregisterMBean(oName);
            Assert.assertFalse(pServer.isRegistered(oName));
            Assert.assertFalse(server.isRegistered(oName));
    }

    @Test
    public void withConstraint() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, AttributeNotFoundException, ReflectionException, InstanceNotFoundException, ParseException, IOException {
        MBeanServer server = createJolokiaMBeanServer();

        ObjectName oName = new ObjectName("test:type=jsonMBean");

        server.registerMBean(new JsonAnnoPlainTest(),oName);

        MBeanServer platformServer = ManagementFactory.getPlatformMBeanServer();

        String deepDive = (String) platformServer.getAttribute(oName,"DeepDive");
        JSONObject deepDiveS = new JSONParser().parse(deepDive, JSONObject.class);
        assertEquals(deepDiveS.size(),1);
        // Serialization is truncated because of maxDepth = 1
        assertTrue(deepDiveS.get("map") instanceof String);
        assertTrue(deepDiveS.get("map").toString().matches(".*hot=.*Chili.*"));
        server.unregisterMBean(oName);
        Assert.assertFalse(platformServer.isRegistered(oName));
        Assert.assertFalse(server.isRegistered(oName));
    }

    @Test
    public void withModelMBean() throws MBeanException, InvalidTargetObjectTypeException, InstanceNotFoundException, InstanceAlreadyExistsException, NotCompliantMBeanException, MalformedObjectNameException, NoSuchMethodException, IntrospectionException {

        RequiredModelMBean modelMBean = new RequiredModelMBean();

        ModelMBeanInfo mbi = new ModelMBeanInfoSupport(
                JsonAnnoPlainTest.class.getName(),
                "JsonMBean Test",
                new ModelMBeanAttributeInfo[]{
                        new ModelMBeanAttributeInfo("DeepDive","description",JsonAnnoPlainTest.class.getDeclaredMethod("getDeepDive"),null)
                },
                new ModelMBeanConstructorInfo[]{},
                new ModelMBeanOperationInfo[]{},
                new ModelMBeanNotificationInfo[]{}
        );
        modelMBean.setModelMBeanInfo(mbi);
        modelMBean.setManagedResource(new JsonAnnoPlainTest(), "ObjectReference");

        MBeanServer server = createJolokiaMBeanServer();

        ObjectName oName = new ObjectName("test:type=jsonMBean");

        server.registerMBean(modelMBean,oName);
        MBeanServer platformServer = ManagementFactory.getPlatformMBeanServer();

        Assert.assertTrue(platformServer.isRegistered(oName));
        Assert.assertTrue(server.isRegistered(oName));

        server.unregisterMBean(oName);
        Assert.assertFalse(platformServer.isRegistered(oName));
        Assert.assertFalse(server.isRegistered(oName));

    }

    // ============================================================================================

    private MBeanServer createJolokiaMBeanServer() {
        return (MBeanServer) Proxy.newProxyInstance(JolokiaMBeanServerHolder.class.getClassLoader(), new Class[]{MBeanServer.class},
                                                    new JolokiaMBeanServerHandler(new JolokiaSerializer()));
    }

    @JsonMBean(maxDepth = 1)
    public static class JsonAnnoPlainTest implements JsonAnnoPlainTestMBean {
        public DeepDive getDeepDive() {
            return new DeepDive();
        }

    }

    public interface JsonAnnoPlainTestMBean {
        DeepDive getDeepDive();
    }

    @JsonMBean()
    public static class JsonAnnoTest implements JsonAnnoTestMXBean {

        public Chili getChili() {
            return new Chili("Bhut Jolokia",1000000);
        }

    }
    public interface JsonAnnoTestMXBean {
        Chili getChili();
    }

    public static class DeepDive {
        @SuppressWarnings("FieldCanBeLocal")
        private final Chili chili;
        private final Map<String, Chili> map;
        public DeepDive() {
            chili = new Chili("Aji",700000);
            map = new HashMap<>();
            map.put("hot",chili);
        }

        public Map<String, Chili> getMap() {
            return map;
        }
    }

    public static class Chili {
        private String name;
        private long scoville;

        public Chili() {
        }

        public Chili(String pName, long pScoville) {
            name = pName;
            scoville = pScoville;
        }

        public String getName() {
            return name;
        }

        public void setName(String pName) {
            name = pName;
        }

        public long getScoville() {
            return scoville;
        }

        public void setScoville(long pScoville) {
            scoville = pScoville;
        }
    }


}
