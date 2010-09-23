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

import javax.management.MalformedObjectNameException;

import org.jolokia.client.exception.J4pException;
import org.jolokia.client.exception.J4pRemoteException;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
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
        assertEquals("0",resp.getValue());
        resp = j4pClient.execute(request);
        assertEquals("1",resp.getValue());
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
        assertEquals("true",resp.getValue());
    }

    @Test
    public void emptyStringArgumentCheck() throws MalformedObjectNameException, J4pException {
        J4pExecRequest request = new J4pExecRequest(itSetup.getOperationMBean(),"emptyStringArgumentCheck","");
        J4pExecResponse resp = j4pClient.execute(request);
        assertEquals("true",resp.getValue());
    }

    @Test
    public void collectionArg() throws MalformedObjectNameException, J4pException {
        String args[] = new String[] { "roland","tanja","forever" };
        J4pExecRequest request = new J4pExecRequest(itSetup.getOperationMBean(),"arrayArguments",args,"myExtra");
        J4pExecResponse resp = j4pClient.execute(request);
        assertEquals("roland",resp.getValue());

        // Same for post
        request = new J4pExecRequest(itSetup.getOperationMBean(),"arrayArguments",args,"myExtra");
        resp = j4pClient.execute(request,"POST");
        assertEquals("roland",resp.getValue());

        // Check request params
        assertEquals("arrayArguments",request.getOperation());
        assertEquals(2,request.getArguments().size());
    }
}
