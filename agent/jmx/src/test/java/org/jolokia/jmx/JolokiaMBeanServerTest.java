package org.jolokia.jmx;

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

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

import javax.management.*;
import javax.management.modelmbean.*;
import javax.management.openmbean.CompositeData;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 27.01.13
 */
public class JolokiaMBeanServerTest {

    @Test
    public void simple() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, MalformedObjectNameException, AttributeNotFoundException, ReflectionException, InstanceNotFoundException, ParseException, InvalidTargetObjectTypeException, NoSuchMethodException, IntrospectionException {
        JolokiaMBeanServer server = new JolokiaMBeanServer();

        ObjectName oName = new ObjectName("test:type=jsonMBean");
            server.registerMBean(new JsonAnnoTest(),oName);

            CompositeData chiliCD = (CompositeData) server.getAttribute(oName,"Chili");
            assertEquals((String) chiliCD.get("name"), "Bhut Jolokia");
            assertEquals(chiliCD.get("scoville"), 1000000L);

            MBeanServer pServer = ManagementFactory.getPlatformMBeanServer();
            String chiliS = (String) pServer.getAttribute(oName,"Chili");
            JSONObject chiliJ = (JSONObject) new JSONParser().parse(chiliS);
            assertEquals(chiliJ.get("name"), "Bhut Jolokia");
            assertEquals(chiliJ.get("scoville"), 1000000L);

            server.unregisterMBean(oName);
            Assert.assertFalse(pServer.isRegistered(oName));
            Assert.assertFalse(server.isRegistered(oName));
    }

    @Test
    public void withConstraint() throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, AttributeNotFoundException, ReflectionException, InstanceNotFoundException, ParseException, InvalidTargetObjectTypeException, NoSuchMethodException, IntrospectionException {
        JolokiaMBeanServer server = new JolokiaMBeanServer();

        ObjectName oName = new ObjectName("test:type=jsonMBean");

        server.registerMBean(new JsonAnnoPlainTest(),oName);

        MBeanServer plattformServer = ManagementFactory.getPlatformMBeanServer();

        String deepDive = (String) plattformServer.getAttribute(oName,"DeepDive");
        JSONObject deepDiveS = (JSONObject) new JSONParser().parse(deepDive);
        assertEquals(deepDiveS.size(),1);
        // Serialization is truncated because of maxDepth = 1
        assertTrue(deepDiveS.get("map") instanceof String);
        assertTrue(deepDiveS.get("map").toString().matches(".*hot=.*Chili.*"));
        server.unregisterMBean(oName);
        Assert.assertFalse(plattformServer.isRegistered(oName));
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

        JolokiaMBeanServer server = new JolokiaMBeanServer();

        ObjectName oName = new ObjectName("test:type=jsonMBean");

        server.registerMBean(modelMBean,oName);
        MBeanServer plattformServer = ManagementFactory.getPlatformMBeanServer();

        Assert.assertTrue(plattformServer.isRegistered(oName));
        Assert.assertTrue(server.isRegistered(oName));

        server.unregisterMBean(oName);
        Assert.assertFalse(plattformServer.isRegistered(oName));
        Assert.assertFalse(server.isRegistered(oName));

    }

    // ============================================================================================

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
        private Chili chili;
        private Map map;
        public DeepDive() {
            chili = new Chili("Aji",700000);
            map = new HashMap();
            map.put("hot",chili);
        }

        public Map getMap() {
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
