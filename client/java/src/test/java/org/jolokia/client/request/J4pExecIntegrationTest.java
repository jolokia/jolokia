package org.jolokia.client.request;

/*
 *  Copyright 2009-2010 Roland Huss
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

import java.util.*;

import javax.management.MalformedObjectNameException;

import org.jolokia.client.exception.J4pException;
import org.jolokia.client.exception.J4pRemoteException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author roland
 * @since May 18, 2010
 */
public class J4pExecIntegrationTest extends AbstractJ4pIntegrationTest {


    @Test
    public void simpleOperation() throws MalformedObjectNameException, J4pException {
        J4pExecRequest request = new J4pExecRequest(itSetup.getOperationMBean(),"reset");
        j4pClient.execute(request);
        request = new J4pExecRequest(itSetup.getOperationMBean(),"fetchNumber","inc");
        J4pExecResponse resp = j4pClient.execute(request);
        assertEquals(0L,resp.getValue());
        resp = j4pClient.execute(request);
        assertEquals(1L,resp.getValue());
    }

    @Test
    public void failedOperation() throws MalformedObjectNameException, J4pException {
        J4pExecRequest request = new J4pExecRequest(itSetup.getOperationMBean(),"fetchNumber","bla");
        try {
            j4pClient.execute(request);
            fail();
        } catch (J4pRemoteException exp) {
            assertEquals(400,exp.getStatus());
            assertTrue(exp.getMessage().contains("IllegalArgumentException"));
            assertTrue(exp.getRemoteStackTrace().contains("IllegalArgumentException"));
        }
    }

    @Test
    public void checkedException() throws MalformedObjectNameException, J4pException {
        J4pExecRequest request = new J4pExecRequest(itSetup.getOperationMBean(),"throwCheckedException");
        try {
            j4pClient.execute(request);
            fail();
        } catch (J4pRemoteException exp) {
            assertEquals(500,exp.getStatus());
            assertTrue(exp.getMessage().contains("Inner exception"));
            assertTrue(exp.getRemoteStackTrace().contains("MBeanException"));
        }
    }

    @Test
    public void nullArgumentCheck() throws MalformedObjectNameException, J4pException {
        J4pExecRequest request = new J4pExecRequest(itSetup.getOperationMBean(),"nullArgumentCheck",null,null);
        J4pExecResponse resp = j4pClient.execute(request);
        assertEquals(true,resp.getValue());
    }

    @Test
    public void emptyStringArgumentCheck() throws MalformedObjectNameException, J4pException {
        J4pExecRequest request = new J4pExecRequest(itSetup.getOperationMBean(),"emptyStringArgumentCheck","");
        J4pExecResponse resp = j4pClient.execute(request);
        assertEquals(true,resp.getValue());
    }

    @Test
    public void collectionArg() throws MalformedObjectNameException, J4pException {
        J4pExecRequest request;
        for (String type : new String[] { "GET", "POST" }) {
            String args[] = new String[] { "roland","tanja","forever" };
            request = new J4pExecRequest(itSetup.getOperationMBean(),"arrayArguments",args,"myExtra");
            J4pExecResponse resp = j4pClient.execute(request,type);
            assertEquals("roland",resp.getValue());

            // Check request params
            assertEquals("arrayArguments",request.getOperation());
            assertEquals(2,request.getArguments().size());

            // With null
            request = new J4pExecRequest(itSetup.getOperationMBean(),"arrayArguments",new String[] { null, "bla", null },"myExtra");
            resp = j4pClient.execute(request);
            assertNull(resp.getValue());

            // With ints
            request = new J4pExecRequest(itSetup.getOperationMBean(),"arrayArguments",new Integer[] { 1,2,3 },"myExtra");
            resp = j4pClient.execute(request);
            assertEquals("1",resp.getValue());
        }
    }

    // =====================================================================================================
    // Post only checks

    @Test
    public void objectArray() throws MalformedObjectNameException, J4pException {
        J4pExecRequest request;
        Object args[] = new Object[] { 12,true,null, "Bla" };
        request = new J4pExecRequest(itSetup.getOperationMBean(),"objectArrayArg",new Object[] { args });
        J4pExecResponse resp = j4pClient.execute(request,"POST");
        assertEquals(12L,resp.getValue());
    }

    @Test
    // Lists are only supported for POST requests
    public void listArg() throws MalformedObjectNameException, J4pException {
        J4pExecRequest request;
        List args = Arrays.asList("roland",new Integer(12),true);
        request = new J4pExecRequest(itSetup.getOperationMBean(),"listArgument",args);
        J4pExecResponse resp;
        resp = j4pClient.execute(request,"POST");
        assertEquals("roland",resp.getValue());
    }

    @Test
    public void booleanArgs() throws MalformedObjectNameException, J4pException {
        J4pExecRequest request;
        J4pExecResponse resp;
        for (String type : new String[] { "GET", "POST" }) {
            request = new J4pExecRequest(itSetup.getOperationMBean(),"booleanArguments",true,Boolean.TRUE);
            resp = j4pClient.execute(request,type);
            assertTrue((Boolean) resp.getValue());

            request = new J4pExecRequest(itSetup.getOperationMBean(),"booleanArguments",Boolean.TRUE,false);
            resp = j4pClient.execute(request,type);
            assertFalse((Boolean) resp.getValue());

            request = new J4pExecRequest(itSetup.getOperationMBean(),"booleanArguments",true,null);
            resp = j4pClient.execute(request,type);
            assertNull(resp.getValue());


            try {
                request = new J4pExecRequest(itSetup.getOperationMBean(),"booleanArguments",null,null);
                j4pClient.execute(request,type);
                fail();
            } catch (J4pRemoteException exp) {
                assertEquals("java.lang.IllegalArgumentException",exp.getErrorType());
            }
        }
    }

    @Test
    public void intArgs() throws MalformedObjectNameException, J4pException {
        J4pExecRequest request;
        J4pExecResponse resp;
        for (String type : new String[] { "GET", "POST" }) {
            request = new J4pExecRequest(itSetup.getOperationMBean(),"intArguments",10,20);
            resp = j4pClient.execute(request,type);
            assertEquals(30L, resp.getValue());

            request = new J4pExecRequest(itSetup.getOperationMBean(),"intArguments",10,null);
            resp = j4pClient.execute(request,type);
            assertEquals(-1L,resp.getValue());

            try {
                request = new J4pExecRequest(itSetup.getOperationMBean(),"intArguments",null,null);
                j4pClient.execute(request,type);
                fail();
            } catch (J4pRemoteException exp) {
                assertEquals("java.lang.IllegalArgumentException",exp.getErrorType());
            }
        }
    }

    @Test
    public void doubleArgs() throws MalformedObjectNameException, J4pException {
        J4pExecRequest request;
        J4pExecResponse resp;
        for (String type : new String[] { "GET", "POST" }) {
            request = new J4pExecRequest(itSetup.getOperationMBean(),"doubleArguments",1.5,1.5);
            resp = j4pClient.execute(request,type);
            assertEquals(3.0, resp.getValue());

            request = new J4pExecRequest(itSetup.getOperationMBean(),"doubleArguments",1.5,null);
            resp = j4pClient.execute(request,type);
            assertEquals(-1.0,resp.getValue());

            try {
                request = new J4pExecRequest(itSetup.getOperationMBean(),"doubleArguments",null,null);
                j4pClient.execute(request,type);
                fail();
            } catch (J4pRemoteException exp) {
                assertEquals("java.lang.IllegalArgumentException",exp.getErrorType());
            }
        }
    }


    @Test
    public void mapArg() throws MalformedObjectNameException, J4pException {
        J4pExecRequest request;
        J4pExecResponse resp;

        JSONObject map = new JSONObject();
        map.put("eins","fcn");
        JSONArray arr = new JSONArray();
        arr.add("fcb");
        arr.add("svw");
        map.put("zwei",arr);

        request = new J4pExecRequest(itSetup.getOperationMBean(),"mapArgument",map);
        resp = j4pClient.execute(request,"POST");
        Map res = resp.getValue();
        assertEquals(res.get("eins"),"fcn");
        assertEquals(((List) res.get("zwei")).get(1),"svw");

        request = new J4pExecRequest(itSetup.getOperationMBean(),"mapArgument",null);
        resp = j4pClient.execute(request,"POST");
        assertNull(resp.getValue());
    }
}
