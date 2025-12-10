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
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.client.JolokiaOperation;
import org.jolokia.client.exception.JolokiaException;
import org.jolokia.client.exception.JolokiaRemoteException;
import org.jolokia.client.response.JolokiaReadResponse;
import org.jolokia.client.response.JolokiaResponse;
import org.jolokia.client.response.JolokiaWriteResponse;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.server.core.config.ConfigKey;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;

/**
 * Integration test for writing attributes
 *
 * @author roland
 * @since Jun 5, 2010
 */
public class ClientWriteIntegrationTest extends AbstractClientIntegrationTest {

    public static final String IT_MXBEAN = "jolokia.it:type=mxbean";
    public static final String IT_ATTRIBUTE_MBEAN = "jolokia.it:type=attribute";

    @Test
    public void simple() throws MalformedObjectNameException, JolokiaException {
        checkWrite("IntValue",null,42L);
    }

    @Test
    public void withPath() throws MalformedObjectNameException, JolokiaException {
        checkWrite("ComplexNestedValue","Blub/1/numbers/0",13L);
    }

    @Test
    public void withBeanPath() throws MalformedObjectNameException, JolokiaException {
        checkWrite("Bean","value",41L);
    }

    @Test
    public void nullValue() throws MalformedObjectNameException, JolokiaException {
        checkWrite("Bean","name",null);
    }

    @Test
    public void emptyString() throws MalformedObjectNameException, JolokiaException {
        checkWrite("Bean","name","");
    }

    @Test
    void map() throws MalformedObjectNameException, JolokiaException {
        Map<String, Object> map = createTestMap();
        checkWrite(new HttpMethod[]{HttpMethod.POST}, "Map", null, map);
        checkWrite("Map","fcn","svw");
        checkWrite("Map","zahl",20L);

        // Write an not yet known key
        JolokiaWriteRequest wReq = new JolokiaWriteRequest(IT_ATTRIBUTE_MBEAN,"Map","hofstadter","douglas");
        JolokiaWriteResponse wResp = jolokiaClient.execute(wReq);
        assertNull(wResp.getValue());
        JolokiaReadRequest rReq = new JolokiaReadRequest(IT_ATTRIBUTE_MBEAN,"Map");
        rReq.setPath("douglas");
        JolokiaReadResponse rResp = jolokiaClient.execute(rReq);
        assertEquals("hofstadter", rResp.getValue());
    }

    private Map<String, Object> createTestMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("eins", "fcn");
        map.put("zwei", "bvb");
        map.put("drei", true);
        map.put("vier", null);
        map.put("fuenf", 12L);
        return map;
    }

    @Test
    void list() throws MalformedObjectNameException, JolokiaException {
        List<Object> list = new ArrayList<>();
        list.add("fcn");
        list.add(42L);
        list.add(createTestMap());
        list.add(null);
        list.add(new BigDecimal("23.2"));
        checkWrite(new HttpMethod[] { HttpMethod.POST }, "List",null,list);
        checkWrite(new HttpMethod[] { HttpMethod.POST }, "List","0",null);
        checkWrite(new HttpMethod[] { HttpMethod.POST }, "List","0","");
        checkWrite(new HttpMethod[] { HttpMethod.POST }, "List","2",42L);
    }


    @Test
    public void stringArray() throws MalformedObjectNameException, JolokiaException {
        final String[] input = new String[] { "eins", "zwei", null, "drei,cztery" };
        checkWrite(new HttpMethod[] { HttpMethod.POST }, "StringArray", null, input, resp -> {
            JSONArray val = resp.getValue();
            assertEquals(val.size(), input.length);
            for (int i = 0; i < input.length; i++) {
                assertEquals(val.get(i),input[i]);
            }
        });
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void stringArrayAsGET() throws MalformedObjectNameException, JolokiaException {
        final String[] input = new String[] { "eins", "zwei", null, "drei,cztery" };
        checkWrite(new HttpMethod[] { HttpMethod.GET }, "StringArray", null, input, resp -> {
        });
    }

    @Test
    public void array2D() throws MalformedObjectNameException, JolokiaException {
        final long[][] input = new long[][] { { 1, 2 }, { 3, 4 } };
        checkWrite(new HttpMethod[] { HttpMethod.POST }, "Array2D", null, input, resp -> {
            JSONArray val = resp.getValue();
            assertEquals(val.size(), input.length);
            for (int i = 0; i < input.length; i++) {
                assertEquals(((JSONArray) val.get(i)).get(0), input[i][0]);
                assertEquals(((JSONArray) val.get(i)).get(1), input[i][1]);
            }
        });
    }

    @Test
    public void instant() throws MalformedObjectNameException, JolokiaException {
        Instant input = Instant.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(ConfigKey.DATE_FORMAT.getDefaultValue()).withZone(TimeZone.getDefault().toZoneId());
        checkWrite(new HttpMethod[] { HttpMethod.POST }, "Instant", null, input, resp -> {
            String val = resp.getValue();
            assertEquals(val, formatter.format(input));
        });
    }

    @Test
    public void access() throws MalformedObjectNameException {

        for (JolokiaWriteRequest req : new JolokiaWriteRequest[] {
                new JolokiaWriteRequest(IT_ATTRIBUTE_MBEAN,"List","bla"),
                new JolokiaWriteRequest(getTargetProxyConfig(),IT_ATTRIBUTE_MBEAN,"List","bla")
        }) {
            assertEquals("List", req.getAttribute());
            assertEquals(new ObjectName(IT_ATTRIBUTE_MBEAN), req.getObjectName());
            assertEquals("bla", req.getValue());
            assertEquals(JolokiaOperation.WRITE, req.getType());
        }
    }


    @Test
    public void mxNumbers() throws MalformedObjectNameException, JolokiaException {
        final Long[] input = { 1L, 2L };
        checkMxWrite(new HttpMethod[] { HttpMethod.POST },"Numbers",null,input, resp -> {
            JSONArray val = resp.getValue();
            assertEquals(val.size(), input.length);
            for (int i = 0; i < input.length; i++) {
                assertEquals(val.get(i), input[i]);
            }
        });
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void mxNumbersAsGET() throws MalformedObjectNameException, JolokiaException {
        final Long[] input = { 1L, 2L };
        checkMxWrite(new HttpMethod[] { HttpMethod.GET },"Numbers",null,input, resp -> {
        });
    }

    @Test
    public void mxMap() throws MalformedObjectNameException, JolokiaException {
        final Map<String,Long> input = new HashMap<>();
        input.put("roland",13L);
        input.put("heino",19L);
        checkMxWrite(new HttpMethod[] { HttpMethod.POST },"Map", null, input, resp -> {
            JSONObject val = resp.getValue();
            assertEquals(val.size(), input.size());
            for (String key : input.keySet()) {
                assertEquals(input.get(key),val.get(key));
            }
        });
    }

    @Test(expectedExceptions = JolokiaRemoteException.class,expectedExceptionsMessageRegExp = ".*immutable.*")
    public void mxMapWithPath() throws MalformedObjectNameException, JolokiaException {
        JolokiaWriteRequest req = new JolokiaWriteRequest(IT_MXBEAN,"Map","hofstadter","douglas");
        jolokiaClient.execute(req,HttpMethod.POST);
    }

    // ==========================================================================================================

    private void checkWrite(String pAttribute,String pPath,Object pValue,ResponseAssertion ... pFinalAssert) throws MalformedObjectNameException, JolokiaException {
        checkWrite(new HttpMethod[] { HttpMethod.GET, HttpMethod.POST }, pAttribute, pPath, pValue, pFinalAssert);
    }

    private void checkMxWrite(String pAttribute,String pPath,Object pValue,ResponseAssertion ... pFinalAssert) throws MalformedObjectNameException, JolokiaException {
        checkMxWrite(new HttpMethod[] { HttpMethod.GET, HttpMethod.POST }, pAttribute, pPath, pValue, pFinalAssert);
    }

    private void checkWrite(HttpMethod[] methods,String pAttribute,String pPath,Object pValue,ResponseAssertion ... pFinalAssert) throws MalformedObjectNameException, JolokiaException {
        checkWrite(IT_ATTRIBUTE_MBEAN,methods,pAttribute,pPath,pValue,pFinalAssert);
    }

    private void checkMxWrite(HttpMethod[] methods,String pAttribute,String pPath,Object pValue,ResponseAssertion ... pFinalAssert) throws MalformedObjectNameException, JolokiaException {
        if (hasMxBeanSupport()) {
            checkWrite(IT_MXBEAN,methods,pAttribute,pPath,pValue,pFinalAssert);
        }
    }

    private void checkWrite(String mBean,HttpMethod[] methods,String pAttribute,String pPath,Object pValue,ResponseAssertion ... pFinalAssert) throws MalformedObjectNameException, JolokiaException {
        for (JolokiaTargetConfig cfg : new JolokiaTargetConfig[] { null, getTargetProxyConfig()  }) {
            for (HttpMethod method : methods) {
                if (method.equals(HttpMethod.GET) && cfg != null) {
                    // No proxy for GET
                    continue;
                }
                reset(cfg);
                JolokiaReadRequest readReq = new JolokiaReadRequest(cfg,mBean,pAttribute);
                if (pPath != null) {
                    readReq.setPath(pPath);
                }
                JolokiaReadResponse readResp = jolokiaClient.execute(readReq,method);
                Object oldValue = readResp.getValue();
                assertNotNull("Old value must not be null",oldValue);

                JolokiaWriteRequest req = new JolokiaWriteRequest(cfg,mBean,pAttribute,pValue,pPath);
                JolokiaWriteResponse resp = jolokiaClient.execute(req,method);
                assertEquals("Old value should be returned",oldValue,resp.getValue());

                readResp = jolokiaClient.execute(readReq);
                if (pFinalAssert != null && pFinalAssert.length > 0) {
                    pFinalAssert[0].assertResponse(readResp);
                } else {
                    assertEquals("New value should be set", pValue, readResp.getValue());
                }
            }
        }
    }

    private void reset(JolokiaTargetConfig cfg) throws MalformedObjectNameException, JolokiaException {
        jolokiaClient.execute(new JolokiaExecRequest(cfg,IT_ATTRIBUTE_MBEAN, "reset"));
    }

    private interface ResponseAssertion {
        void assertResponse(JolokiaResponse<?> resp);
    }


    private boolean hasMxBeanSupport() {
        try {
            Class.forName("javax.management.MXBean");
            return true;
        } catch (ClassNotFoundException exp) {
            return false;
        }
    }
}
