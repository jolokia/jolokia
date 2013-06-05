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

import java.util.*;
import java.util.concurrent.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.jolokia.client.J4pClient;
import org.jolokia.client.exception.*;
import org.json.simple.JSONObject;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Integration test for reading attributes
 *
 * @author roland
 * @since Apr 27, 2010
 */
public class J4pReadIntegrationTest extends AbstractJ4pIntegrationTest {

    @Test
    public void nameTest() throws MalformedObjectNameException, J4pException {
        checkNames(HttpGet.METHOD_NAME,itSetup.getStrangeNames(),itSetup.getEscapedNames());
        checkNames(HttpPost.METHOD_NAME,itSetup.getStrangeNames(),itSetup.getEscapedNames());
    }

    @Test
    public void errorTest() throws MalformedObjectNameException, J4pException {
        J4pReadRequest req = new J4pReadRequest("no.domain:name=vacuum","oxygen");
        try {
            j4pClient.execute(req);
            fail();
        } catch (J4pRemoteException exp) {
            assertEquals(404,exp.getStatus());
            assertTrue(exp.getMessage().contains("InstanceNotFoundException"));
            assertTrue(exp.getRemoteStackTrace().contains("InstanceNotFoundException"));
        }
    }

    @Test void errorConnectionRefusedTest() throws J4pException, MalformedObjectNameException {
        try {
            final J4pReadRequest req = new J4pReadRequest(itSetup.getAttributeMBean(),"LongSeconds");
            J4pClient anotherClient = new J4pClient("http://localhost:27654/jolokia");
            anotherClient.execute(req);
            fail();
        } catch (J4pConnectException exp) {
            // all fine
        }
    }
    @Test
    public void error404ConnectionTest() throws Exception {
        final J4pReadRequest req = new J4pReadRequest(itSetup.getAttributeMBean(),"LongSeconds");
        try {
            stop();
            startWithoutAgent();
            j4pClient.execute(req);
            fail();
        } catch (J4pRemoteException exp) {
            assertEquals(404,exp.getStatus());
        }
        stop();
        start();

        final CyclicBarrier barrier = new CyclicBarrier(10);
        final Queue errors = new ConcurrentLinkedQueue();
        Runnable run = new Runnable() {
            public void run() {
                try {
                    j4pClient.execute(req);
                } catch (Exception e) {
                    errors.add(1);
                    System.err.println(e);
                }
                try {
                    barrier.await();
                } catch (InterruptedException ex) {
                    return;
                } catch (BrokenBarrierException ex) {
                    return;
                }
            }
        };

        for (int i = 0;i < 10; i++) {
            new Thread(run).start();
        }
        if (barrier.await() == 0) {
            //System.err.println("Finished");
            assertEquals(0, errors.size(),"Concurrent calls should work");
        }
    }

    @Test
    public void nameWithSpace() throws MalformedObjectNameException, J4pException {
        for (J4pReadRequest req : readRequests("jolokia.it:type=naming/,name=name with space","Ok")) {
            J4pReadResponse resp = j4pClient.execute(req);
            assertNotNull(resp);
        }
    }

    private J4pReadRequest[] readRequests(String mbean, String ... attributes) throws MalformedObjectNameException {
        return new J4pReadRequest[] {
                new J4pReadRequest(mbean,attributes),
                new J4pReadRequest(getTargetProxyConfig(),mbean,attributes)
        };
    }

    @Test
    public void multipleAttributes() throws MalformedObjectNameException, J4pException {

        for (J4pReadRequest req : readRequests(itSetup.getAttributeMBean(),"LongSeconds","SmallMinutes")) {
            J4pReadResponse resp = j4pClient.execute(req);
            assertFalse(req.hasSingleAttribute());
            assertEquals(2,req.getAttributes().size());
            Map respVal = resp.getValue();
            assertTrue(respVal.containsKey("LongSeconds"));
            assertTrue(respVal.containsKey("SmallMinutes"));

            Collection<String> attrs = resp.getAttributes(new ObjectName(itSetup.getAttributeMBean()));
            Set<String> attrSet = new HashSet<String>(attrs);
            assertTrue(attrSet.contains("LongSeconds"));
            assertTrue(attrSet.contains("SmallMinutes"));

            try {
                resp.getAttributes(new ObjectName("blub:type=bla"));
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains(itSetup.getAttributeMBean()));
            }

            Set<String> allAttrs = new HashSet<String>(resp.getAttributes());
            assertEquals(2,allAttrs.size());
            assertTrue(allAttrs.contains("LongSeconds"));
            assertTrue(allAttrs.contains("SmallMinutes"));

            Double val = resp.getValue(new ObjectName(itSetup.getAttributeMBean()),"SmallMinutes");
            assertNotNull(val);

            try {
                resp.getValue(new ObjectName(itSetup.getAttributeMBean()),"Aufsteiger");
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("Aufsteiger"));
            }

            Double longVal = resp.getValue("LongSeconds");
            assertNotNull(longVal);

            try {
                resp.getValue("Pinola bleibt");
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("Pinola"));
            }

            try {
                resp.getValue(null);
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("null"));
            }

            try {
                req.getAttribute();
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("than one"));
            }
        }
    }

    @Test
    public void allAttributes() throws MalformedObjectNameException, J4pException {
        for (J4pReadRequest req : readRequests(itSetup.getAttributeMBean())) {
            J4pReadResponse resp = j4pClient.execute(req);
            assertFalse(req.hasSingleAttribute());
            assertTrue(req.hasAllAttributes());
            assertEquals(0,req.getAttributes().size());
            Map respVal = resp.getValue();
            assertTrue(respVal.containsKey("LongSeconds"));
            assertTrue(respVal.containsKey("SmallMinutes"));
            assertTrue(respVal.size() > 20);

            Collection<String> attrs = resp.getAttributes(new ObjectName(itSetup.getAttributeMBean()));
            Set<String> attrSet = new HashSet<String>(attrs);
            assertTrue(attrSet.contains("LongSeconds"));
            assertTrue(attrSet.contains("SmallMinutes"));

            try {
                resp.getAttributes(new ObjectName("blub:type=bla"));
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains(itSetup.getAttributeMBean()));
            }

            Set<String> allAttrs = new HashSet<String>(resp.getAttributes());
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
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("than one"));
            }
        }
    }

    @Test
    public void mbeanPattern() throws MalformedObjectNameException, J4pException {
        for (J4pReadRequest req : readRequests("*:type=attribute","LongSeconds")) {
            J4pReadResponse resp = j4pClient.execute(req);
            assertEquals(1,resp.getObjectNames().size());
            Map respVal = resp.getValue();
            assertTrue(respVal.containsKey(itSetup.getAttributeMBean()));
            Map attrs = (Map) respVal.get(itSetup.getAttributeMBean());
            assertEquals(1,attrs.size());
            assertTrue(attrs.containsKey("LongSeconds"));

            Set<String> attrSet = new HashSet<String>(resp.getAttributes(new ObjectName(itSetup.getAttributeMBean())));
            assertEquals(1,attrSet.size());
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
    public void mbeanPatternWithAttributes() throws MalformedObjectNameException, J4pException {
        for (J4pReadRequest req : readRequests("*:type=attribute","LongSeconds","List")) {
            assertNull(req.getPath());
            J4pReadResponse resp = j4pClient.execute(req);
            assertEquals(1,resp.getObjectNames().size());
            Map respVal = resp.getValue();
            Map attrs = (Map) respVal.get(itSetup.getAttributeMBean());
            assertEquals(2,attrs.size());
            assertTrue(attrs.containsKey("LongSeconds"));
            assertTrue(attrs.containsKey("List"));

            Double longVal = resp.getValue(new ObjectName(itSetup.getAttributeMBean()),"LongSeconds");
            assertNotNull(longVal);

            try {
                resp.getValue(new ObjectName(itSetup.getAttributeMBean()),"FCN");
                fail();
            } catch (IllegalArgumentException exp) {
                assertTrue(exp.getMessage().contains("FCN"));
            }
        }
    }

    @Test
    public void mxBeanReadTest() throws MalformedObjectNameException, J4pException {
        for (J4pReadRequest request  : readRequests("jolokia.it:type=mxbean","ComplexTestData")) {
            J4pReadResponse response = j4pClient.execute(request);
            JSONObject value = response.getValue();
            assertEquals(value.get("number"),1968L);
            assertEquals(value.get("string"),"late");

            List set = (List) value.get("set");
            assertEquals(set.size(),2);
            assertTrue(set.contains(12L));
            assertTrue(set.contains(14L));

            Map map = (Map) value.get("map");
            assertEquals(map.size(),2);
            assertEquals(map.get("kill"), true);
            assertEquals(map.get("bill"), false);

            List array = (List) value.get("stringArray");
            assertEquals(array.size(),2);
            assertTrue(array.contains("toy"));
            assertTrue(array.contains("story"));

            List<Boolean> list = (List<Boolean>) value.get("list");
            assertEquals(list.size(),3);
            assertTrue(list.get(0));
            assertFalse(list.get(1));
            assertTrue(list.get(2));

            Map complex = (Map) value.get("complex");
            List innerList = (List) complex.get("hidden");
            Map innerInnerMap = (Map) innerList.get(0);
            assertEquals(innerInnerMap.get("deep"), "inside");
        }
    }

    @Test
    public void processingOptionsTest() throws J4pException, MalformedObjectNameException {
        for (J4pReadRequest request : readRequests("jolokia.it:type=mxbean","ComplexTestData")) {
            Map<J4pQueryParameter,String> params = new HashMap<J4pQueryParameter, String>();
            params.put(J4pQueryParameter.MAX_DEPTH,"1");
            params.put(J4pQueryParameter.IGNORE_ERRORS,"true");
            for (String method : new String[] { "GET", "POST" }) {
                if (request.getTargetConfig() != null && method.equals("GET")) {
                    continue;
                }
                J4pReadResponse response = j4pClient.execute(request,method,params);
                JSONObject value = response.getValue();
                Object complex = value.get("complex");
                assertTrue(complex instanceof String);
                assertTrue(complex.toString().contains("TabularData"));
            }
        }
    }

    private void checkNames(String pMethod, List<String> ... pNames) throws MalformedObjectNameException, J4pException {
        for (int i = 0;i<pNames.length;i++) {
            for (String name : pNames[i]) {
                System.out.println(name);
                ObjectName oName =  new ObjectName(name);
                J4pReadRequest req = new J4pReadRequest(oName,"Ok");
                req.setPreferredHttpMethod(pMethod);
                J4pReadResponse resp = j4pClient.execute(req);
                Collection names = resp.getObjectNames();
                assertEquals(1,names.size());
                assertEquals(oName,names.iterator().next());
                assertEquals("OK",resp.getValue());
                Collection<String> attrs = resp.getAttributes();
                assertEquals(1,attrs.size());

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

}
