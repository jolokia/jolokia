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
import javax.management.ObjectName;

import org.jolokia.client.exception.J4pException;
import org.jolokia.client.exception.J4pRemoteException;
import org.json.simple.JSONArray;
import org.testng.annotations.Test;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * Integration test for writing attributes
 *
 * @author roland
 * @since Jun 5, 2010
 */
public class J4pWriteIntegrationTest extends AbstractJ4pIntegrationTest {

    @Test
    public void simple() throws MalformedObjectNameException, J4pException {
        checkWrite("IntValue",null,42);
    }

    @Test
    public void withPath() throws MalformedObjectNameException, J4pException {
        checkWrite("ComplexNestedValue","Blub/1/numbers/0","13");
    }

    @Test
    public void withBeanPath() throws MalformedObjectNameException, J4pException {
        checkWrite("Bean","value","41");
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
    public void stringArray() throws MalformedObjectNameException, J4pException {
        try {
            final String input[] = new String[] { "eins", "zwei", null, "drei" };
            checkWrite("StringArray", null, input,new ResponseAssertion() {
                public void assertResponse(J4pResponse resp) {
                    JSONArray val = (JSONArray) resp.getValue();
                    assertEquals(val.size(), input.length);
                    for (int i = 0; i < input.length; i++) {
                        assertEquals(val.get(i),input[i]);
                    }
                }
            });
        } catch (J4pRemoteException exp) {
            exp.printStackTrace();
        }
    }

    @Test
    public void access() throws MalformedObjectNameException {
        J4pWriteRequest req = new J4pWriteRequest("jolokia.it:type=attribute","List","bla");
        req.setPath("0");
        assertEquals(req.getPath(),"0");
        assertEquals(req.getAttribute(),"List");
        assertEquals(req.getObjectName(),new ObjectName("jolokia.it:type=attribute"));
        assertEquals(req.getValue(),"bla");
        assertEquals(req.getType(),J4pType.WRITE);
    }

    private void checkWrite(String pAttribute,String pPath,Object pValue,ResponseAssertion ... pFinalAssert) throws MalformedObjectNameException, J4pException {
        for (String method : new String[] { "GET", "POST" }) {
            reset();
            J4pReadRequest readReq = new J4pReadRequest("jolokia.it:type=attribute",pAttribute);
            if (pPath != null) {
                readReq.setPath(pPath);
            }
            J4pReadResponse readResp = j4pClient.execute(readReq,method);
            Object oldValue = readResp.getValue();
            assertNotNull("Old value must not be null",oldValue);

            J4pWriteRequest req = new J4pWriteRequest("jolokia.it:type=attribute",pAttribute,pValue,pPath);
            J4pWriteResponse resp = j4pClient.execute(req,method);
            assertEquals("Old value should be returned",oldValue,resp.getValue());

            readResp = j4pClient.execute(readReq);
            if (pFinalAssert != null && pFinalAssert.length > 0) {
                pFinalAssert[0].assertResponse(readResp);
            } else {
                assertEquals("New value should be set",pValue != null ? pValue.toString() : null,readResp.getValue());
            }
        }
    }


    private void reset() throws MalformedObjectNameException, J4pException {
        j4pClient.execute(new J4pExecRequest("jolokia.it:type=attribute", "reset"));
    }

    private interface ResponseAssertion {
        void assertResponse(J4pResponse resp);
    }
}
