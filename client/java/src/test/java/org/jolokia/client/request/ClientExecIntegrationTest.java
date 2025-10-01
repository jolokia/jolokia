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

import org.jolokia.client.JolokiaTargetConfig;
import org.jolokia.client.exception.JolokiaException;
import org.jolokia.client.exception.JolokiaRemoteException;
import org.jolokia.client.response.JolokiaExecResponse;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author roland
 * @since May 18, 2010
 */
public class ClientExecIntegrationTest extends AbstractClientIntegrationTest {


    @Test
    public void simpleOperation() throws MalformedObjectNameException, JolokiaException {
        for (JolokiaTargetConfig cfg : new JolokiaTargetConfig[] { null, getTargetProxyConfig()}) {
            JolokiaExecRequest request = new JolokiaExecRequest(cfg,itSetup.getOperationMBean(),"reset");
            jolokiaClient.execute(request);
            request = new JolokiaExecRequest(cfg,itSetup.getOperationMBean(),"fetchNumber","inc");
            JolokiaExecResponse resp = jolokiaClient.execute(request);
            assertEquals(0, (long) resp.getValue());
            resp = jolokiaClient.execute(request);
            assertEquals(1, (long) resp.getValue());
        }
    }

    @Test
    public void beanSerialization() throws MalformedObjectNameException, JolokiaException {
        JolokiaExecRequest request = new JolokiaExecRequest(itSetup.getMxBean(),
                                                    "echoBean",
                                                    "{\"name\": \"hello\", \"value\": \"world\"}");
        JolokiaExecResponse resp = jolokiaClient.execute(request);
        JSONObject bean = resp.getValue();
        Assert.assertEquals(bean.get("name"),"hello");
        Assert.assertEquals(bean.get("value"),"world");
    }

    @Test
    public void failedOperation() throws MalformedObjectNameException, JolokiaException {
        for (JolokiaExecRequest request : execRequests("fetchNumber","bla")) {
            try {
                jolokiaClient.execute(request);
                fail();
            } catch (JolokiaRemoteException exp) {
                assertEquals(400L,exp.getStatus());
                assertTrue(exp.getMessage().contains("IllegalArgumentException"));
                assertTrue(exp.getRemoteStackTrace().contains("IllegalArgumentException"));
            }
        }
    }

    private JolokiaExecRequest[] execRequests(String pOperation, Object... pArgs) throws MalformedObjectNameException {
        return new JolokiaExecRequest[] {
                new JolokiaExecRequest(itSetup.getOperationMBean(),pOperation,pArgs),
                new JolokiaExecRequest(getTargetProxyConfig(),itSetup.getOperationMBean(),pOperation,pArgs)
        };
    }

    @Test
    public void checkedException() throws MalformedObjectNameException, JolokiaException {
        for (JolokiaExecRequest request : execRequests("throwCheckedException")) {
            try {
                jolokiaClient.execute(request);
                fail();
            } catch (JolokiaRemoteException exp) {
                assertEquals(500,exp.getStatus());
                assertTrue(exp.getMessage().contains("Inner exception"));
                assertTrue(exp.getRemoteStackTrace().contains("java.lang.Exception"));
            }
        }
    }

    @Test
    public void nullArgumentCheck() throws MalformedObjectNameException, JolokiaException {
        for (JolokiaExecRequest request : execRequests("nullArgumentCheck",null,null))  {
            JolokiaExecResponse resp = jolokiaClient.execute(request);
            assertTrue(resp.getValue());
        }
    }

    @Test
    public void emptyStringArgumentCheck() throws MalformedObjectNameException, JolokiaException {
        for (JolokiaExecRequest request : execRequests("emptyStringArgumentCheck","")) {
            JolokiaExecResponse resp = jolokiaClient.execute(request);
            assertTrue(resp.getValue());
        }
    }

    @Test
    public void collectionArg() throws MalformedObjectNameException, JolokiaException {
        for (HttpMethod type : new HttpMethod[] { HttpMethod.GET, HttpMethod.POST }) {
            for (Object args : new Object[] {
                    new String[] { "roland","tanja","forever" },
                    Arrays.asList("roland", "tanja","forever")
            }) {
                for (JolokiaExecRequest request : execRequests("arrayArguments",args,"myExtra")) {
                    if (type.equals(HttpMethod.GET) && request.getTargetConfig() != null) {
                        continue;
                    }
                    JolokiaExecResponse resp = jolokiaClient.execute(request,type);
                    assertEquals("roland",resp.getValue());

                    // Check request params
                    assertEquals("arrayArguments",request.getOperation());
                    assertEquals(2,request.getArguments().size());

                    // With null
                    request = new JolokiaExecRequest(itSetup.getOperationMBean(),"arrayArguments",new String[] { null, "bla", null },"myExtra");
                    resp = jolokiaClient.execute(request);
                    assertNull(resp.getValue());

                    // With ints
                    request = new JolokiaExecRequest(itSetup.getOperationMBean(),"arrayArguments",new Integer[] { 1,2,3 },"myExtra");
                    resp = jolokiaClient.execute(request);
                    assertEquals("1",resp.getValue());
                }
            }
        }
    }

    // =====================================================================================================
    // Post only checks

    @Test
    public void objectArray() throws MalformedObjectNameException, JolokiaException {
        Object[] args = new Object[] { 12,true,null, "Bla" };
        for (JolokiaExecRequest request : execRequests("objectArrayArg",new Object[] { args })) {
            JolokiaExecResponse resp = jolokiaClient.execute(request,HttpMethod.POST);
            assertEquals(12, (long) resp.getValue());
        }
    }

    @Test
    // Lists are only supported for POST requests
    public void listArg() throws MalformedObjectNameException, JolokiaException {
        List<?> args = Arrays.asList("roland", 12, true);
        for (JolokiaExecRequest request : execRequests("listArgument", args)) {
            JolokiaExecResponse resp;
            resp = jolokiaClient.execute(request, HttpMethod.POST);
            assertEquals("roland", resp.getValue());
        }
    }

    @Test
    public void booleanArgs() throws MalformedObjectNameException, JolokiaException {
        JolokiaExecRequest request;
        JolokiaExecResponse resp;
        for (JolokiaTargetConfig cfg : new JolokiaTargetConfig[] { null, getTargetProxyConfig()}) {
            for (HttpMethod type : new HttpMethod[] { HttpMethod.GET, HttpMethod.POST }) {
                if (type.equals(HttpMethod.GET) && cfg != null) {
                    continue;
                }
                request = new JolokiaExecRequest(cfg,itSetup.getOperationMBean(),"booleanArguments",true,Boolean.TRUE);
                resp = jolokiaClient.execute(request,type);
                assertTrue(resp.getValue());

                request = new JolokiaExecRequest(cfg,itSetup.getOperationMBean(),"booleanArguments",Boolean.TRUE,false);
                resp = jolokiaClient.execute(request,type);
                assertFalse(resp.getValue());

                request = new JolokiaExecRequest(cfg,itSetup.getOperationMBean(),"booleanArguments",true,null);
                resp = jolokiaClient.execute(request,type);
                assertNull(resp.getValue());


                try {
                    request = new JolokiaExecRequest(cfg,itSetup.getOperationMBean(),"booleanArguments",null,null);
                    jolokiaClient.execute(request,type);
                    fail();
                } catch (JolokiaRemoteException exp) {
                    assertEquals("java.lang.IllegalArgumentException", exp.getErrorType());
                }
            }
        }
    }

    @Test
    public void intArgs() throws MalformedObjectNameException, JolokiaException {
        JolokiaExecRequest request;
        JolokiaExecResponse resp;
        for (JolokiaTargetConfig cfg : new JolokiaTargetConfig[] { null, getTargetProxyConfig()}) {
            for (HttpMethod type : new HttpMethod[] { HttpMethod.GET, HttpMethod.POST }) {
                if (type.equals(HttpMethod.GET) && cfg != null) {
                    continue;
                }
                request = new JolokiaExecRequest(cfg,itSetup.getOperationMBean(),"intArguments",10,20);
                resp = jolokiaClient.execute(request,type);
                assertEquals(30, (long) resp.getValue());

                request = new JolokiaExecRequest(cfg,itSetup.getOperationMBean(),"intArguments",10,null);
                resp = jolokiaClient.execute(request,type);
                assertEquals(-1, (long) resp.getValue());

                try {
                    request = new JolokiaExecRequest(cfg,itSetup.getOperationMBean(),"intArguments",null,null);
                    jolokiaClient.execute(request,type);
                    fail();
                } catch (JolokiaRemoteException exp) {
                    assertEquals("java.lang.IllegalArgumentException", exp.getErrorType());
                }
            }
        }
    }
    @Test
    public void doubleArgs() throws MalformedObjectNameException, JolokiaException {
        JolokiaExecRequest request;
        JolokiaExecResponse resp;
        for (JolokiaTargetConfig cfg : new JolokiaTargetConfig[] { null, getTargetProxyConfig()}) {
            for (HttpMethod type : new HttpMethod[] { HttpMethod.GET, HttpMethod.POST }) {
                if (type.equals(HttpMethod.GET) && cfg != null) {
                    continue;
                }
                request = new JolokiaExecRequest(cfg,itSetup.getOperationMBean(),"doubleArguments",1.5,1.5);
                resp = jolokiaClient.execute(request,type);
                assertEquals(new BigDecimal("3.0"), resp.getValue());

                request = new JolokiaExecRequest(cfg,itSetup.getOperationMBean(),"doubleArguments",1.5,null);
                resp = jolokiaClient.execute(request,type);
                assertEquals(new BigDecimal("-1.0"),resp.getValue());

                try {
                    request = new JolokiaExecRequest(cfg,itSetup.getOperationMBean(),"doubleArguments",null,null);
                    jolokiaClient.execute(request,type);
                    fail();
                } catch (JolokiaRemoteException exp) {
                    assertEquals("java.lang.IllegalArgumentException", exp.getErrorType());
                }
            }
        }
    }


    @Test
    public void mapArg() throws MalformedObjectNameException, JolokiaException {
        JolokiaExecRequest request;
        JolokiaExecResponse resp;

        JSONObject map = new JSONObject();
        map.put("eins","fcn");
        JSONArray arr = new JSONArray();
        arr.add("fcb");
        arr.add("svw");
        map.put("zwei",arr);
        map.put("drei",10L);
        map.put("vier",true);

        for (JolokiaTargetConfig cfg : new JolokiaTargetConfig[] { null, getTargetProxyConfig()}) {
            request = new JolokiaExecRequest(cfg,itSetup.getOperationMBean(),"mapArgument",map);
            for (HttpMethod method : new HttpMethod[] { HttpMethod.GET, HttpMethod.POST }) {
                if (method.equals(HttpMethod.GET) && cfg != null) {
                    continue;
                }
                resp = jolokiaClient.execute(request,method);
                Map<?, ?> res = resp.getValue();
                assertEquals("fcn", res.get("eins"));
                assertEquals("svw", ((List<?>) res.get("zwei")).get(1));
                assertEquals(10L, res.get("drei"));
                assertEquals(true, res.get("vier"));
            }

            request = new JolokiaExecRequest(itSetup.getOperationMBean(),"mapArgument",new Object[]{null});
            resp = jolokiaClient.execute(request,HttpMethod.POST);
            assertNull(resp.getValue());
        }
    }

    @Test
    public void dateArgs() throws MalformedObjectNameException, JolokiaException {
        JolokiaExecRequest request;
        JolokiaExecResponse resp;
        for (JolokiaTargetConfig cfg : new JolokiaTargetConfig[] { null, getTargetProxyConfig()}) {
            for (HttpMethod type : new HttpMethod[] { HttpMethod.GET, HttpMethod.POST }) {
                if (type.equals(HttpMethod.GET) && cfg != null) {
                    continue;
                }
                request = new JolokiaExecRequest(cfg,itSetup.getOperationMBean(),"withDates",new Date());
                resp = jolokiaClient.execute(request,type);
                assertNotNull(resp);
            }
        }
    }

}
