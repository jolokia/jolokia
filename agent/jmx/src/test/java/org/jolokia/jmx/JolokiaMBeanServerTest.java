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

import javax.management.*;
import javax.management.openmbean.CompositeData;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 27.01.13
 */
public class JolokiaMBeanServerTest {

    @Test
    public void simple() throws NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanException, MalformedObjectNameException, AttributeNotFoundException, ReflectionException, InstanceNotFoundException, ParseException {
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


    // ============================================================================================

    @JsonMBean
    public static class JsonAnnoTest implements JsonAnnoTestMXBean {

        public Chili getChili() {
            return new Chili("Bhut Jolokia",1000000);
        }
    }
    public interface JsonAnnoTestMXBean {
        Chili getChili();
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
