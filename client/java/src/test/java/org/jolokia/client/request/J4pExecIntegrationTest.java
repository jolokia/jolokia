package org.jolokia.client.request;

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
            J4pExecResponse resp = j4pClient.execute(request);
            fail();
        } catch (J4pRemoteException exp) {
            assertEquals(400,exp.getStatus());
            assertTrue(exp.getMessage().contains("IllegalArgumentException"));
            assertTrue(exp.getRemoteStackTrace().contains("IllegalArgumentException"));
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
