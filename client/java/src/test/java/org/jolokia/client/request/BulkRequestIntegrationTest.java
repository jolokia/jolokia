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

import javax.management.MalformedObjectNameException;

import org.jolokia.client.BasicAuthenticator;
import org.jolokia.client.J4pClient;
import org.jolokia.client.exception.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author roland
 * @since Jun 9, 2010
 */
public class BulkRequestIntegrationTest extends AbstractJ4pIntegrationTest {

    @Test
    public void simpleBulkRequest() throws MalformedObjectNameException, J4pException {
        J4pRequest req1 = new J4pExecRequest(itSetup.getOperationMBean(),"fetchNumber","inc");
        J4pVersionRequest req2 = new J4pVersionRequest();
        List resp = j4pClient.execute(req1,req2);
        assertEquals(resp.size(),2);
        assertTrue(resp.get(0) instanceof J4pExecResponse);
        assertTrue(resp.get(1) instanceof J4pVersionResponse);
        List<J4pResponse<J4pRequest>> typeSaveResp = j4pClient.execute(req1,req2);
        for (J4pResponse<?> r : typeSaveResp) {
            assertTrue(r instanceof J4pExecResponse || r instanceof J4pVersionResponse);
        }
    }

    @Test
    public void simpleBulkRequestWithOptions() throws MalformedObjectNameException, J4pException {
        J4pRequest req1 = new J4pReadRequest(itSetup.getAttributeMBean(),"ComplexNestedValue");
        J4pVersionRequest req2 = new J4pVersionRequest();
        Map<J4pQueryParameter,String> params = new HashMap<J4pQueryParameter, String>();
        params.put(J4pQueryParameter.MAX_DEPTH,"2");
        List resps = j4pClient.execute(Arrays.asList(req1,req2),params);
        assertEquals(resps.size(),2);
        J4pReadResponse resp = (J4pReadResponse) resps.get(0);
        JSONObject value = resp.getValue();
        JSONArray inner = (JSONArray) value.get("Blub");
        assertTrue(inner.get(1) instanceof String);
    }

    @Test
    public void bulkRequestWithErrors() throws MalformedObjectNameException, J4pException {

        List<J4pReadRequest> requests = createBulkRequests();
        try {
            j4pClient.execute(requests);
            fail();
        } catch (J4pBulkRemoteException e) {
            List results = e.getResults();
            assertEquals(3, results.size());
            results = e.getResponses();
            assertEquals(2, results.size());
            assertTrue(results.get(0) instanceof J4pReadResponse);
            assertEquals("Bla", ((J4pReadResponse) results.get(0)).<String>getValue());
            assertTrue(results.get(1) instanceof J4pReadResponse);

            results = e.getRemoteExceptions();
            assertEquals(1, results.size());
            assertTrue(results.get(0) instanceof J4pRemoteException);
            J4pRemoteException exp = (J4pRemoteException) results.get(0);
            assertEquals(404, exp.getStatus());
            assertTrue(exp.getMessage().contains("InstanceNotFoundException"));
            assertTrue(exp.getRemoteStackTrace().contains("InstanceNotFoundException"));
            assertEquals(exp.getRequest(), requests.get(1));
        }
    }

    private List<J4pReadRequest> createBulkRequests() throws MalformedObjectNameException {
        J4pReadRequest req1 = new J4pReadRequest(itSetup.getAttributeMBean(),"ComplexNestedValue");
        req1.setPath("Blub/0");
        J4pReadRequest req2 = new J4pReadRequest("bla:type=blue","Sucks");
        J4pReadRequest req3 = new J4pReadRequest("java.lang:type=Memory","HeapMemoryUsage");
        return Arrays.asList(req1,req2,req3);
    }

    @Test
    public void optionalBulkRequestsWithExtractorAsArgument() throws MalformedObjectNameException, J4pException {
        List<J4pReadResponse> resp = j4pClient.execute(createBulkRequests(),null, ValidatingResponseExtractor.OPTIONAL);

        verifyOptionalBulkResponses(resp);
    }

    @Test
    public void optionalBulkRequestsWithExtractorAsDefault() throws MalformedObjectNameException, J4pException {
        J4pClient c = J4pClient.url(j4pUrl)
                               .user("jolokia")
                               .password("jolokia")
                               .authenticator(new BasicAuthenticator().preemptive())
                               .responseExtractor(ValidatingResponseExtractor.OPTIONAL)
                               .build();

        List<J4pReadResponse> resp = c.execute(createBulkRequests());

        verifyOptionalBulkResponses(resp);
    }


    private void verifyOptionalBulkResponses(List<J4pReadResponse> resp) {
        assertEquals(3, resp.size());
        assertTrue(resp.get(0) instanceof J4pReadResponse);
        assertEquals("Bla", ((J4pReadResponse) resp.get(0)).<String>getValue());
        assertNull(resp.get(1));
        assertTrue(resp.get(2) instanceof J4pReadResponse);
    }

}
