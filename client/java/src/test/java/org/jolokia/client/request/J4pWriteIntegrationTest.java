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
import java.util.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.client.exception.J4pException;
import org.jolokia.client.exception.J4pRemoteException;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.*;

/**
 * Integration test for writing attributes
 *
 * @author roland
 * @since Jun 5, 2010
 */
public class J4pWriteIntegrationTest extends AbstractJ4pIntegrationTest {

    public static final String IT_MXBEAN = "jolokia.it:type=mxbean";
    public static final String IT_ATTRIBUTE_MBEAN = "jolokia.it:type=attribute";

    @Test
    public void simple() throws MalformedObjectNameException, J4pException {
        checkWrite("IntValue",null,42);
    }

    @Test
    public void withPath() throws MalformedObjectNameException, J4pException {
        checkWrite("ComplexNestedValue","Blub/1/numbers/0",13);
    }

    @Test
    public void withBeanPath() throws MalformedObjectNameException, J4pException {
        checkWrite("Bean","value",41);
    }

    @Test
    public void nullValue() throws MalformedObjectNameException, J4pException {
        checkWrite("Bean","name",null);
    }

    @Test
    public void emptyString() throws MalformedObjectNameException, J4pException {
        checkWrite("Bean","name","");
    }

    @Test
    void map() throws MalformedObjectNameException, J4pException {
        Map<String, Object> map = createTestMap();
        checkWrite(new String[]{"POST"}, "Map", null, map);
        checkWrite("Map","fcn","svw");
        checkWrite("Map","zahl",20);

        // Write an not yet known key
        J4pWriteRequest wReq = new J4pWriteRequest(IT_ATTRIBUTE_MBEAN,"Map","hofstadter","douglas");
        J4pWriteResponse wResp = j4pClient.execute(wReq);
        assertNull(wResp.getValue());
        J4pReadRequest  rReq = new J4pReadRequest(IT_ATTRIBUTE_MBEAN,"Map");
        rReq.setPath("douglas");
        J4pReadResponse rResp = j4pClient.execute(rReq);
        assertEquals(rResp.getValue(),"hofstadter");
    }

    private Map<String, Object> createTestMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("eins", "fcn");
        map.put("zwei", "bvb");
        map.put("drei", true);
        map.put("vier", null);
        map.put("fuenf", 12);
        return map;
    }

    @Test
    void list() throws MalformedObjectNameException, J4pException {
        List<Object> list = new ArrayList<>();
        list.add("fcn");
        list.add(42);
        list.add(createTestMap());
        list.add(null);
        list.add(new BigDecimal("23.2"));
        checkWrite(new String[] { "POST" }, "List",null,list);
        checkWrite("List","0",null);
        checkWrite("List","0","");
        checkWrite("List","2",42);
    }


    @Test
    public void stringArray() throws MalformedObjectNameException, J4pException {
        try {
            final String[] input = new String[] { "eins", "zwei", null, "drei" };
            checkWrite("StringArray", null, input, resp -> {
                JSONArray val = resp.getValue();
                assertEquals(val.size(), input.length);
                for (int i = 0; i < input.length; i++) {
                    assertEquals(val.get(i),input[i]);
                }
            });
        } catch (J4pRemoteException exp) {
            exp.printStackTrace();
        }
    }

    @Test
    public void array2D() throws MalformedObjectNameException, J4pException {
        final int[][] input = new int[][] { { 1, 2 }, { 3, 4 } };
        checkWrite(new String[] { "POST" }, "Array2D", null, input, resp -> {
            JSONArray val = resp.getValue();
            assertEquals(val.size(), input.length);
            for (int i = 0; i < input.length; i++) {
                assertEquals(((JSONArray) val.get(i)).get(0), input[i][0]);
                assertEquals(((JSONArray) val.get(i)).get(1), input[i][1]);
            }
        });
    }

    @Test
    public void access() throws MalformedObjectNameException {

        for (J4pWriteRequest req : new J4pWriteRequest[] {
                new J4pWriteRequest(IT_ATTRIBUTE_MBEAN,"List","bla"),
                new J4pWriteRequest(getTargetProxyConfig(),IT_ATTRIBUTE_MBEAN,"List","bla")
        }) {
            req.setPath("0");
            assertEquals(req.getPath(),"0");
            assertEquals(req.getAttribute(),"List");
            assertEquals(req.getObjectName(),new ObjectName(IT_ATTRIBUTE_MBEAN));
            assertEquals(req.getValue(),"bla");
            assertEquals(req.getType(),J4pType.WRITE);
        }
    }


    @Test
    public void mxNumbers() throws MalformedObjectNameException, J4pException {
        final Integer[] input = { 1,2 };
        checkMxWrite("Numbers",null,input, resp -> {
            JSONArray val = resp.getValue();
            assertEquals(val.size(), input.length);
            for (int i = 0; i < input.length; i++) {
                assertEquals(val.get(i), input[i]);
            }
        });
    }

    @Test
    public void mxMap() throws MalformedObjectNameException, J4pException {
        final Map<String,Integer> input = new HashMap<>();
        input.put("roland",13);
        input.put("heino",19);
        checkMxWrite(new String[] {"POST"},"Map", null, input, resp -> {
            JSONObject val = resp.getValue();
            assertEquals(val.size(), input.size());
            for (String key : input.keySet()) {
                assertEquals(input.get(key),val.get(key));
            }
        });
    }

    @Test(expectedExceptions = J4pRemoteException.class,expectedExceptionsMessageRegExp = ".*immutable.*")
    public void mxMapWithPath() throws MalformedObjectNameException, J4pException {
        J4pWriteRequest req = new J4pWriteRequest(IT_MXBEAN,"Map","hofstadter","douglas");
        j4pClient.execute(req,"POST");
    }

    // ==========================================================================================================

    private void checkWrite(String pAttribute,String pPath,Object pValue,ResponseAssertion ... pFinalAssert) throws MalformedObjectNameException, J4pException {
        checkWrite(new String[]{"GET", "POST"}, pAttribute, pPath, pValue, pFinalAssert);
    }

    private void checkMxWrite(String pAttribute,String pPath,Object pValue,ResponseAssertion ... pFinalAssert) throws MalformedObjectNameException, J4pException {
        checkMxWrite(new String[]{"GET", "POST"}, pAttribute, pPath, pValue, pFinalAssert);
    }

    private void checkWrite(String[] methods,String pAttribute,String pPath,Object pValue,ResponseAssertion ... pFinalAssert) throws MalformedObjectNameException, J4pException {
        checkWrite(IT_ATTRIBUTE_MBEAN,methods,pAttribute,pPath,pValue,pFinalAssert);
    }

    private void checkMxWrite(String[] methods,String pAttribute,String pPath,Object pValue,ResponseAssertion ... pFinalAssert) throws MalformedObjectNameException, J4pException {
        if (hasMxBeanSupport()) {
            checkWrite(IT_MXBEAN,methods,pAttribute,pPath,pValue,pFinalAssert);
        }
    }

    private void checkWrite(String mBean,String[] methods,String pAttribute,String pPath,Object pValue,ResponseAssertion ... pFinalAssert) throws MalformedObjectNameException, J4pException {
        for (J4pTargetConfig cfg : new J4pTargetConfig[] { null, getTargetProxyConfig()  }) {
            for (String method : methods) {
                if (method.equals("GET") && cfg != null) {
                    // No proxy for GET
                    continue;
                }
                reset(cfg);
                J4pReadRequest readReq = new J4pReadRequest(cfg,mBean,pAttribute);
                if (pPath != null) {
                    readReq.setPath(pPath);
                }
                J4pReadResponse readResp = j4pClient.execute(readReq,method);
                Object oldValue = readResp.getValue();
                assertNotNull("Old value must not be null",oldValue);

                J4pWriteRequest req = new J4pWriteRequest(cfg,mBean,pAttribute,pValue,pPath);
                J4pWriteResponse resp = j4pClient.execute(req,method);
                assertEquals("Old value should be returned",oldValue,resp.getValue());

                readResp = j4pClient.execute(readReq);
                if (pFinalAssert != null && pFinalAssert.length > 0) {
                    pFinalAssert[0].assertResponse(readResp);
                } else {
                    assertEquals("New value should be set", pValue, readResp.getValue());
                }
            }
        }
    }

    private void reset(J4pTargetConfig cfg) throws MalformedObjectNameException, J4pException {
        j4pClient.execute(new J4pExecRequest(cfg,IT_ATTRIBUTE_MBEAN, "reset"));
    }

    private interface ResponseAssertion {
        void assertResponse(J4pResponse<?> resp);
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
