package org.jolokia.service.jsr160;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.management.*;
import javax.naming.CommunicationException;
import javax.naming.NamingException;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.Configuration;
import org.jolokia.server.core.config.StaticConfiguration;
import org.jolokia.server.core.request.*;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.jolokia.json.JSONObject;
import org.testng.annotations.*;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 02.09.11
 */
@Test(singleThreaded = true)
public class Jsr160RequestHandlerTest {

    private Jsr160RequestHandler dispatcher;

    @BeforeMethod
    private void setup() {
        //private ProcessingParameters procParams;
        TestJolokiaContext ctx = new TestJolokiaContext.Builder().build();
        dispatcher = new Jsr160RequestHandler(0) {
            @Override
            protected Map<String, Object> prepareEnv(Map<String, String> pTargetConfig) {
                Map<String, Object> ret = super.prepareEnv(pTargetConfig);
                if (ret == null) {
                    ret = new HashMap<>();
                }
                ret.put("jmx.remote.protocol.provider.pkgs", "org.jolokia.service.jsr160");
                return ret;
            }
        };
        dispatcher.init(ctx);
    }

    @AfterMethod
    private void destroy() throws JMException {
        dispatcher.destroy();
    }

    @Test
    public void canHandle() {
        assertFalse(dispatcher.canHandle(JolokiaRequestFactory.createGetRequest("/read/java.lang:type=Memory", new TestProcessingParameters())));
        JolokiaRequest req = preparePostReadRequest(null);
        assertTrue(dispatcher.canHandle(req));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void illegalDispatch() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException, EmptyResponseException {
        dispatcher.handleRequest(JolokiaRequestFactory.createGetRequest("/read/java.lang:type=Memory/HeapMemoryUsage", new TestProcessingParameters()), null);
    }

    @Test(expectedExceptions = IOException.class)
    public void simpleDispatchFail() throws Exception {
        JolokiaRequest req = preparePostReadRequest(null);
        destroy();
        TestJolokiaContext testCtx;
        testCtx = new TestJolokiaContext.Builder().build();
        Jsr160RequestHandler handler = new Jsr160RequestHandler(0);
        handler.init(testCtx);
        handler.handleRequest(req,null);
        setup();
    }

    @Test
    public void simpleDispatch() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException, EmptyResponseException {
        JolokiaReadRequest req =  preparePostReadRequest(null);
        @SuppressWarnings("unchecked")
        Map<String, ?> result = (Map<String, ?>) dispatcher.handleRequest(req, null);
        assertTrue(result.containsKey("HeapMemoryUsage"));
    }

    @Test
    public void simpleDispatchForSingleAttribute() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException, EmptyResponseException {
        JolokiaReadRequest req = preparePostReadRequest(null, "HeapMemoryUsage");
        assertNotNull(dispatcher.handleRequest(req,null));
    }

    @Test
    public void simpleDispatchWithUser() throws InstanceNotFoundException, IOException, ReflectionException, AttributeNotFoundException, MBeanException, NotChangedException, EmptyResponseException {
        System.setProperty("TEST_WITH_USER","roland");
        try {
            JolokiaRequest req = preparePostReadRequest("roland");
            @SuppressWarnings("unchecked")
            Map<String, ?> result = (Map<String, ?>) dispatcher.handleRequest(req, null);
            assertTrue(result.containsKey("HeapMemoryUsage"));
        } finally {
            System.clearProperty("TEST_WITH_USER");
        }
    }


    // =========================================================================================================

    private JolokiaReadRequest preparePostReadRequest(String pUser, String... pAttribute) {
        return preparePostReadRequestWithServiceUrl("service:jmx:test:///jndi/rmi://localhost:9999/jmxrmi", pUser, pAttribute);
    }

    private JolokiaReadRequest preparePostReadRequestWithServiceUrl(String pJmxServiceUrl, String pUser, String... pAttribute) {
        JSONObject params = new JSONObject();
        JSONObject target = new JSONObject();
        target.put("url",pJmxServiceUrl);
        if (pUser != null) {
            target.put("user","roland");
            target.put("password","s!cr!et");
        }
        if (pAttribute != null && pAttribute.length > 0) {
            params.put("attribute",pAttribute[0]);
        }
        params.put("target",target);
        params.put("type","read");
        params.put("mbean","java.lang:type=Memory");

        return JolokiaRequestFactory.createPostRequest(params, new TestProcessingParameters());
    }

    private Jsr160RequestHandler createDispatcherPointingToLocalMBeanServer(Configuration pConfig) {
        TestJolokiaContext ctx = new TestJolokiaContext.Builder().config(pConfig).build();
        Jsr160RequestHandler handler = new Jsr160RequestHandler(0) {
            @Override
            protected Map<String, Object> prepareEnv(Map<String, String> pTargetConfig) {
                Map<String, Object> ret = super.prepareEnv(pTargetConfig);
                if (ret == null) {
                    ret = new HashMap<>();
                }
                ret.put("jmx.remote.protocol.provider.pkgs", "org.jolokia.service.jsr160");
                return ret;
            }
        };
        handler.init(ctx);

        return handler;
    }

    // === ??? ===

//    @Test
//    public void useReturnValue() {
//        assertTrue(dispatcher.useReturnValueWithPath(JmxRequestFactory.createGetRequest("/read/java.lang:type=Memory", procParams)));
//    }

    @Test
    public void simpleWhiteListWithConfig() throws Exception {

        String whiteListPath = getFilePathFor("/pattern-whitelist.txt");
        Configuration config = new StaticConfiguration(
                ConfigKey.JSR160_PROXY_ALLOWED_TARGETS, whiteListPath);
        runWhiteListTest(config);
    }

    @Test
    public void simpleWhiteListWithSysProp() throws Exception {
        String whiteListPath = getFilePathFor("/pattern-whitelist.txt");
        try {
            System.setProperty(Jsr160RequestHandler.ALLOWED_TARGETS_SYSPROP, whiteListPath);
            runWhiteListTest(null);
        } finally {
            System.getProperties().remove(Jsr160RequestHandler.ALLOWED_TARGETS_SYSPROP);
        }
    }

    @Test
    public void whiteListWithIllegalPath() {
        String invalidPath = "/very/unlikely/path";
        Configuration config = new StaticConfiguration(
                ConfigKey.JSR160_PROXY_ALLOWED_TARGETS, invalidPath);
        try {
            createDispatcherPointingToLocalMBeanServer(config);
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains(invalidPath));
        }
    }

    @Test
    public void defaultBlackList() throws Exception {
        String blackListedUrl = "service:jmx:rmi:///jndi/ldap://localhost:9092/jmxrmi";
        JolokiaRequest req = preparePostReadRequestWithServiceUrl(blackListedUrl, null);
        try {
            dispatcher.handleRequest(req,null);
            fail("Exception should have been thrown for " + blackListedUrl);
        } catch (SecurityException exp) {
            assertTrue(exp.getMessage().contains(blackListedUrl));
        }
    }

    private void runWhiteListTest(Configuration config) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, NotChangedException, EmptyResponseException {
        Jsr160RequestHandler dispatcher = createDispatcherPointingToLocalMBeanServer(config);

        Object[] testData = new Object[] {
                "service:jmx:test:///jndi/rmi://devil.com:6666/jmxrmi", false,
                "service:jmx:test:///jndi/rmi://localhost:9999/jmxrmi", true,
                "service:jmx:test:///jndi/rmi://jolokia.org:8888/jmxrmi", true,
                "service:jmx:rmi:///jndi/ldap://localhost:9999/jmxrmi", true,
                "service:jmx:test:///jndi/ad://localhost:9999/jmxrmi", false,
                "service:jmx:rmi:///jndi/ldap://localhost:9092/jmxrmi", true
        };

        for (int i = 0; i < testData.length; i +=2) {
            JolokiaReadRequest req = preparePostReadRequestWithServiceUrl((String) testData[i], null);
            try {
                dispatcher.handleRequest(req,null);
                if (!(Boolean) testData[i+1]) {
                    fail("Exception should have been thrown for " + testData[i]);
                }
            } catch (SecurityException exp) {
                if ((Boolean) testData[i+1]) {
                    fail("Security exception for pattern " + testData[i]);
                }
            } catch (IOException exp) {
                // That's fine if allowed to pass
                assertTrue(exp.getCause() instanceof CommunicationException || exp.getCause() instanceof NamingException);
                if (!(Boolean) testData[i+1]) {
                    fail("Should not come that far " + testData[i]);
                }
            }
        }
    }

    private String getFilePathFor(String resource) {
        return Objects.requireNonNull(this.getClass().getResource(resource)).getFile();
    }


}
