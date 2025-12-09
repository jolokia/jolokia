package org.jolokia.server.core.restrictor;

import org.jolokia.server.core.config.*;
import org.jolokia.server.core.restrictor.policy.PolicyRestrictor;
import org.jolokia.core.api.LogHandler;
import org.jolokia.server.core.service.api.Restrictor;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by nevenr on 11/1/15.
 */
public class RestrictorFactoryTest {


    private final LogHandler dummyLogHandler = createDummyLogHandler();

    @Test
    public void testAllowAllRestrictor() {

        Configuration config = getConfig();
        Restrictor restrictor = RestrictorFactory.createRestrictor(config, dummyLogHandler);
        assertTrue(restrictor.getClass().isAssignableFrom(AllowAllRestrictor.class));

    }

    @Test
    public void testDenyAllRestrictor() {

        Configuration config = getConfig(ConfigKey.POLICY_LOCATION, "file:///some_non_existing_file.xml");
        Restrictor restrictor = RestrictorFactory.createRestrictor(config, dummyLogHandler);
        assertTrue(restrictor.getClass().isAssignableFrom(DenyAllRestrictor.class));

    }

    @Test
    public void testPolicyRestrictor() {

        Configuration config = getConfig(ConfigKey.POLICY_LOCATION, "classpath:/access-restrictor-factory-test.xml");
        Restrictor restrictor = RestrictorFactory.createRestrictor(config, dummyLogHandler);
        assertTrue(restrictor.getClass().isAssignableFrom(PolicyRestrictor.class));

    }

    @Test
    public void testCustomRestrictor() {

        Configuration config = getConfig(ConfigKey.RESTRICTOR_CLASS, "org.jolokia.server.core.restrictor.TestRestrictor");
        Restrictor restrictor = RestrictorFactory.createRestrictor(config, dummyLogHandler);
        assertTrue(restrictor.getClass().isAssignableFrom(TestRestrictor.class));

    }

    @Test
    public void policyRestrictor() {
        System.setProperty("jolokia.test1.policy.location","access-restrictor-factory-test.xml");
        System.setProperty("jolokia.test2.policy.location","access-restrictor-factory-test");
        for (String[] params : new String[][] {
                {"classpath:/access-restrictor-factory-test.xml", "true"},
                {"file:///not-existing.xml","false"},
                {"classpath:/${prop:jolokia.test1.policy.location}", "true"},
                {"classpath:/${prop:jolokia.test2.policy.location}.xml", "true"}
        }) {
            Restrictor restrictor = RestrictorFactory.createRestrictor(getConfig(ConfigKey.POLICY_LOCATION,params[0]),dummyLogHandler);
            if (Boolean.parseBoolean(params[1])) {
                assertNotEquals(restrictor.getClass(),DenyAllRestrictor.class);
            } else {
                assertEquals(restrictor.getClass(),DenyAllRestrictor.class);
            }
        }
    }


    private Configuration getConfig(Object... extra) {
        List<Object> list = new ArrayList<>();
        Collections.addAll(list, extra);
        return new StaticConfiguration(list.toArray());
    }

    private LogHandler createDummyLogHandler() {
        return new LogHandler() {
            public void debug(String message) {
                System.out.printf("DEBUG> %s%n", message);
            }

            public void info(String message) {
                System.out.printf("INFO> %s%n", message);
            }

            public void error(String message, Throwable t) {
                System.out.printf("ERROR> %s%n", message);
                System.out.printf("ERROR exception> %s%n", t.getMessage());
            }

            public boolean isDebug() {
                return false;
            }
        };
    }

}
