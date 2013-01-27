package org.jolokia.jmx;

import java.lang.management.ManagementFactory;

import javax.management.*;
import javax.management.openmbean.CompositeData;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

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
        assertEquals((String) chiliCD.get("name"),"Bhut Jolokia");
        assertEquals(chiliCD.get("scoville"),1000000L);

        MBeanServer pServer = ManagementFactory.getPlatformMBeanServer();
        String chiliS = (String) pServer.getAttribute(oName,"Chili");
        JSONObject chiliJ = (JSONObject) new JSONParser().parse(chiliS);
        assertEquals(chiliJ.get("name"),"Bhut Jolokia");
        assertEquals(chiliJ.get("scoville"),1000000L);

        server.unregisterMBean(oName);
        assertFalse(pServer.isRegistered(oName));
        assertFalse(server.isRegistered(oName));
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
