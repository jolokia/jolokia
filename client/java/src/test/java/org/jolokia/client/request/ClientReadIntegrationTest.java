package org.jolokia.client.request;

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

import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.client.JolokiaClient;
import org.jolokia.client.JolokiaQueryParameter;
import org.jolokia.client.exception.*;
import org.jolokia.client.response.JolokiaReadResponse;
import org.jolokia.json.JSONObject;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Integration test for reading attributes
 *
 * @author roland
 * @since Apr 27, 2010
 */
public class ClientReadIntegrationTest extends AbstractClientIntegrationTest {

    @Test
    public void nameTest() throws MalformedObjectNameException, JolokiaException {
        checkNames(HttpMethod.GET,itSetup.getStrangeNames(),itSetup.getEscapedNames());
        checkNames(HttpMethod.POST,itSetup.getStrangeNames(),itSetup.getEscapedNames());
    }

    @Test
    public void errorTest() throws MalformedObjectNameException, JolokiaException {
        JolokiaReadRequest req = new JolokiaReadRequest("no.domain:name=vacuum","oxygen");
        try {
            jolokiaClient.execute(req);
            fail();
        } catch (JolokiaRemoteException exp) {
            assertEquals(exp.getStatus(), 404);
            assertTrue(exp.getMessage().contains("InstanceNotFoundException"));
            assertTrue(exp.getRemoteStackTrace().contains("InstanceNotFoundException"));
        }
    }

    @Test
    public void errorConnectionRefusedTest() throws JolokiaException, MalformedObjectNameException {
        try {
            final JolokiaReadRequest req = new JolokiaReadRequest(itSetup.getAttributeMBean(),"LongSeconds");
            JolokiaClient anotherClient = new JolokiaClient(URI.create("http://localhost:27654/jolokia"));
            anotherClient.execute(req);
            fail();
        } catch (JolokiaConnectException exp) {
            // all fine
        }
    }
    @Test
    public void error404ConnectionTest() throws Exception {
        final JolokiaReadRequest req = new JolokiaReadRequest(itSetup.getAttributeMBean(),"LongSeconds");
        try {
            stop();
            startWithoutAgent();
            jolokiaClient.execute(req);
            fail();
        } catch (JolokiaRemoteException exp) {
            assertEquals(exp.getStatus(), 404);
        }
        stop();
        start();

        final CyclicBarrier barrier = new CyclicBarrier(10);
        final Queue<Integer> errors = new ConcurrentLinkedQueue<>();
        Runnable run = () -> {
            try {
                jolokiaClient.execute(req);
            } catch (Exception e) {
                errors.add(1);
            }
            try {
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException ignored) {
            }
        };

        for (int i = 0;i < 10; i++) {
            new Thread(run).start();
        }
        if (barrier.await() == 0) {
            //System.err.println("Finished");
            assertEquals(errors.size(), 0, "Concurrent calls should work");
        }
    }

    @Test
    public void nameWithSpace() throws MalformedObjectNameException, JolokiaException {
        for (JolokiaReadRequest req : readRequests("jolokia.it:type=naming/,name=name with space","Ok")) {
            JolokiaReadResponse resp = jolokiaClient.execute(req);
            assertNotNull(resp);
        }
    }

    private JolokiaReadRequest[] readRequests(String mbean, String ... attributes) throws MalformedObjectNameException {
        return new JolokiaReadRequest[] {
                new JolokiaReadRequest(mbean,attributes),
                new JolokiaReadRequest(getTargetProxyConfig(),mbean,attributes)
        };
    }

    @Test
    public void multipleAttributes() throws MalformedObjectNameException, JolokiaException {

        for (JolokiaReadRequest req : readRequests(itSetup.getAttributeMBean(),"LongSeconds","SmallMinutes")) {
            JolokiaReadResponse resp = jolokiaClient.execute(req);
            assertFalse(req.hasSingleAttribute());
            assertEquals(req.getAttributes().size(), 2);
            Map<?, ?> respVal = resp.getValue();
            assertTrue(respVal.containsKey("LongSeconds"));
            assertTrue(respVal.containsKey("SmallMinutes"));

            Collection<String> attrs = resp.getAttributes(new ObjectName(itSetup.getAttributeMBean()));
            Set<String> attrSet = new HashSet<>(attrs);
            assertTrue(attrSet.contains("LongSeconds"));
            assertTrue(attrSet.contains("SmallMinutes"));

            try {
                resp.getAttributes(new ObjectName("blub:type=bla"));
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains(itSetup.getAttributeMBean()));
            }

            Set<String> allAttrs = new HashSet<>(resp.getAttributes());
            assertEquals(allAttrs.size(), 2);
            assertTrue(allAttrs.contains("LongSeconds"));
            assertTrue(allAttrs.contains("SmallMinutes"));

            BigDecimal val = resp.getValue(new ObjectName(itSetup.getAttributeMBean()),"SmallMinutes");
            assertNotNull(val);

            try {
                resp.getValue(new ObjectName(itSetup.getAttributeMBean()),"Aufsteiger");
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("Aufsteiger"));
            }

            BigDecimal decimalVal = resp.getValue("LongSeconds");
            assertNotNull(decimalVal);

            try {
                resp.getValue("Pinola bleibt");
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("Pinola"));
            }

            try {
                resp.getValue((String) null);
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("null"));
            }

            try {
                req.getAttribute();
                fail();
            } catch (IllegalStateException exp) {
                assertTrue(exp.getMessage().contains("than one"));
            }
        }
    }

    @Test
    public void allAttributes() throws MalformedObjectNameException, JolokiaException {
        for (JolokiaReadRequest req : readRequests(itSetup.getAttributeMBean())) {
            JolokiaReadResponse resp = jolokiaClient.execute(req);
            assertFalse(req.hasSingleAttribute());
            assertTrue(req.hasAllAttributes());
            assertEquals(req.getAttributes().size(), 0);
            Map<?, ?> respVal = resp.getValue();
            assertTrue(respVal.containsKey("LongSeconds"));
            assertTrue(respVal.containsKey("SmallMinutes"));
            assertTrue(respVal.size() > 20);

            Collection<String> attrs = resp.getAttributes(new ObjectName(itSetup.getAttributeMBean()));
            Set<String> attrSet = new HashSet<>(attrs);
            assertTrue(attrSet.contains("LongSeconds"));
            assertTrue(attrSet.contains("SmallMinutes"));

            try {
                resp.getAttributes(new ObjectName("blub:type=bla"));
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains(itSetup.getAttributeMBean()));
            }

            Set<String> allAttrs = new HashSet<>(resp.getAttributes());
            assertTrue(allAttrs.size() > 20);
            assertTrue(allAttrs.contains("Name"));
            assertTrue(allAttrs.contains("Bytes"));

            Long val = resp.getValue(new ObjectName(itSetup.getAttributeMBean()),"MemoryUsed");
            assertNotNull(val);

            try {
                resp.getValue(new ObjectName(itSetup.getAttributeMBean()),"Aufsteiger");
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("Aufsteiger"));
            }

            Long bytes = resp.getValue("Bytes");
            assertNotNull(bytes);

            try {
                req.getAttribute();
                fail();
            } catch (IllegalStateException exp) {
                assertTrue(exp.getMessage().contains("than one"));
            }
        }
    }

    @Test
    public void mbeanPattern() throws MalformedObjectNameException, JolokiaException {
        for (JolokiaReadRequest req : readRequests("*:type=attribute","LongSeconds")) {
            JolokiaReadResponse resp = jolokiaClient.execute(req);
            assertEquals(resp.getObjectNames().size(), 1);
            Map<?, ?> respVal = resp.getValue();
            assertTrue(respVal.containsKey(itSetup.getAttributeMBean()));
            Map<?, ?> attrs = (Map<?, ?>) respVal.get(itSetup.getAttributeMBean());
            assertEquals(attrs.size(), 1);
            assertTrue(attrs.containsKey("LongSeconds"));

            Set<String> attrSet = new HashSet<>(resp.getAttributes(new ObjectName(itSetup.getAttributeMBean())));
            assertEquals(attrSet.size(), 1);
            assertTrue(attrSet.contains("LongSeconds"));

            try {
                resp.getAttributes(new ObjectName("blub:type=bla"));
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("blub:type=bla"));
            }

            try {
                resp.getAttributes();
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("*:type=attribute"));
            }

            try {
                resp.getValue("LongSeconds");
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("non-pattern"));
            }
        }
    }

    @Test
    public void mbeanPatternWithAttributes() throws MalformedObjectNameException, JolokiaException {
        for (JolokiaReadRequest req : readRequests("*:type=attribute","LongSeconds","List")) {
            assertNull(req.getPath());
            JolokiaReadResponse resp = jolokiaClient.execute(req);
            assertEquals(resp.getObjectNames().size(), 1);
            Map<?, ?> respVal = resp.getValue();
            Map<?, ?> attrs = (Map<?, ?>) respVal.get(itSetup.getAttributeMBean());
            assertEquals(attrs.size(), 2);
            assertTrue(attrs.containsKey("LongSeconds"));
            assertTrue(attrs.containsKey("List"));

            BigDecimal decimalVal = resp.getValue(new ObjectName(itSetup.getAttributeMBean()),"LongSeconds");
            assertNotNull(decimalVal);

            try {
                resp.getValue(new ObjectName(itSetup.getAttributeMBean()),"FCN");
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("FCN"));
            }
        }
    }

    @Test
    public void mxBeanReadTest() throws MalformedObjectNameException, JolokiaException {
        for (JolokiaReadRequest request  : readRequests("jolokia.it:type=mxbean","ComplexTestData")) {
            JolokiaReadResponse response = jolokiaClient.execute(request);
            JSONObject value = response.getValue();
            assertEquals(value.get("number"),1968L);
            assertEquals(value.get("string"),"late");

            List<?> set = (List<?>) value.get("set");
            assertEquals(set.size(),2);
            assertTrue(set.contains(12L));
            assertTrue(set.contains(14L));

            Map<?, ?> map = (Map<?, ?>) value.get("map");
            assertEquals(map.size(),2);
            assertEquals(map.get("kill"), true);
            assertEquals(map.get("bill"), false);

            List<?> array = (List<?>) value.get("stringArray");
            assertEquals(array.size(),2);
            assertTrue(array.contains("toy"));
            assertTrue(array.contains("story"));

            //noinspection unchecked
            List<Boolean> list = (List<Boolean>) value.get("list");
            assertEquals(list.size(),3);
            assertTrue(list.get(0));
            assertFalse(list.get(1));
            assertTrue(list.get(2));

            Map<?, ?> complex = (Map<?, ?>) value.get("complex");
            List<?> innerList = (List<?>) complex.get("hidden");
            Map<?, ?> innerInnerMap = (Map<?, ?>) innerList.get(0);
            assertEquals(innerInnerMap.get("deep"), "inside");
        }
    }

    @Test
    public void processingOptionsTest() throws JolokiaException, MalformedObjectNameException {
        for (JolokiaReadRequest request : readRequests("jolokia.it:type=mxbean","ComplexTestData")) {
            Map<JolokiaQueryParameter,String> params = new HashMap<>();
            params.put(JolokiaQueryParameter.MAX_DEPTH,"1");
            params.put(JolokiaQueryParameter.IGNORE_ERRORS,"true");
            for (HttpMethod method : new HttpMethod[] { HttpMethod.GET, HttpMethod.POST }) {
                if (request.getTargetConfig() != null && method.equals(HttpMethod.GET)) {
                    continue;
                }
                JolokiaReadResponse response = jolokiaClient.execute(request,method,params);
                JSONObject value = response.getValue();
                Object complex = value.get("complex");
                assertTrue(complex instanceof String);
                assertTrue(complex.toString().contains("TabularData"));
            }
        }
    }

    @SafeVarargs
    private void checkNames(HttpMethod pMethod, List<String> ... pNames) throws MalformedObjectNameException, JolokiaException {
        for (List<String> pName : pNames) {
            for (String name : pName) {
                System.out.println(name);
                ObjectName oName = new ObjectName(name);
                JolokiaReadRequest req = new JolokiaReadRequest(oName, "Ok");
                req.setPreferredHttpMethod(pMethod);
                JolokiaReadResponse resp = jolokiaClient.execute(req);
                Collection<ObjectName> names = resp.getObjectNames();
                assertEquals(names.size(), 1);
                assertEquals(oName, names.iterator().next());
                assertEquals(resp.getValue(), "OK");
                Collection<String> attrs = resp.getAttributes();
                assertEquals(attrs.size(), 1);

                assertNotNull(resp.getValue("Ok"));
                try {
                    resp.getValue("Koepke");
                    fail();
                } catch (IllegalArgumentException exp) {
                    assertTrue(exp.getMessage().contains("Koepke"));
                }
            }
        }
    }

    @Test
    public void nonStringKeyInAMap() throws JolokiaException, MalformedObjectNameException {
        JolokiaReadRequest request = new JolokiaReadRequest(itSetup.getAttributeMBean(), "NonStringKeyMap");
        JolokiaReadResponse response = jolokiaClient.execute(request, HttpMethod.POST, Collections.emptyMap());
        JSONObject value = response.getValue();
        assertEquals(value.values().iterator().next(), new BigDecimal("0.9"));
    }

}
