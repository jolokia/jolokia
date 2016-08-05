package org.jolokia.restrictor;

import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.restrictor.RestrictorFactory.URLConnectionResult;
import org.jolokia.util.LogHandler;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Created by nevenr on 11/1/15.
 */
public class RestrictorFactoryTest {


    private final LogHandler dummyLogHandler = createDummyLogHandler();

    @Test
    public void testAllowAllRestrictor() throws Exception {

        Configuration config = getConfig();
        Restrictor restrictor = RestrictorFactory.createRestrictor(config, dummyLogHandler);
        assertTrue(restrictor.getClass().isAssignableFrom(AllowAllRestrictor.class));

    }

    @Test
    public void testDenyAllRestrictor() throws Exception {

        Configuration config = getConfig(ConfigKey.POLICY_LOCATION, "file:///some_non_existing_file.xml");
        Restrictor restrictor = RestrictorFactory.createRestrictor(config, dummyLogHandler);
        assertTrue(restrictor.getClass().isAssignableFrom(DenyAllRestrictor.class));

    }

    @Test
    public void testPolicyRestrictor() throws Exception {

        Configuration config = getConfig(ConfigKey.POLICY_LOCATION, "classpath:/access-restrictor-factory-test.xml");
        Restrictor restrictor = RestrictorFactory.createRestrictor(config, dummyLogHandler);
        assertTrue(restrictor.getClass().isAssignableFrom(PolicyRestrictor.class));

    }

    @Test
    public void testCustomRestrictor() throws Exception {

        Configuration config = getConfig(ConfigKey.RESTRICTOR_CLASS, "org.jolokia.restrictor.TestRestrictor");
        Restrictor restrictor = RestrictorFactory.createRestrictor(config, dummyLogHandler);
        assertTrue(restrictor.getClass().isAssignableFrom(TestRestrictor.class));

    }

    @DataProvider(name = "basicAuthTestDataProvider")
    private static Object[][] provideBasicAuthData() {

        return new Object[][] {
                { "file:///some.file", "file:///some.file", null }, //
                { "http://host", "http://host", null }, //
                { "http://some.host", "http://some.host", null }, //
                { "http://some.host.com", "http://some.host.com", null }, //
                { "https://some.ho-st.com", "https://some.ho-st.com", null }, //
                { "http://user:@some.host.com", "http://user:@some.host.com", null }, //
                { "http://user:pass@some.host.com", "http://some.host.com", "Basic dXNlcjpwYXNz" }, //
                { "http://:pass@some.host.com", "http://:pass@some.host.com", null }, //
                { "http://user:pass@host", "http://host", "Basic dXNlcjpwYXNz" }, //
                { "https://user:pass@host", "https://host", "Basic dXNlcjpwYXNz" }, //
                { "https://user:pass@host:1234", "https://host:1234", "Basic dXNlcjpwYXNz" } //
        };

    }

    @Test(dataProvider = "basicAuthTestDataProvider")
    public void testUrlConnectionBuilder(String pLocation,
            String expectedUrl, String expectedBasicAuthHeaderValue) throws Exception {

        URLConnectionResult result = RestrictorFactory.buildUrlConnection(pLocation);
        assertNotNull(result.urlConnection);
        assertEquals(expectedUrl, result.url);
        assertEquals(expectedBasicAuthHeaderValue, result.basicAuthHeaderValue);

    }

    private Configuration getConfig(Object... extra) {
        ArrayList list = new ArrayList();
        Collections.addAll(list, extra);
        return new Configuration(list.toArray());
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
        };
    }

}