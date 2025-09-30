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

import org.jolokia.client.J4pClient;
import org.jolokia.client.J4pClientBuilder;
import org.jolokia.client.J4pQueryParameter;
import org.jolokia.client.JolokiaOperation;
import org.jolokia.client.exception.*;
import org.jolokia.client.response.JolokiaExecResponse;
import org.jolokia.client.response.JolokiaReadResponse;
import org.jolokia.client.response.JolokiaResponse;
import org.jolokia.client.response.JolokiaVersionResponse;
import org.jolokia.client.response.ValidatingResponseExtractor;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author roland
 * @since Jun 9, 2010
 */
public class BulkRequestIntegrationTest extends AbstractJ4pIntegrationTest {

    @Test
    public void simpleBulkRequest() throws MalformedObjectNameException, J4pException {
        JolokiaRequest req1 = new JolokiaExecRequest(itSetup.getOperationMBean(),"fetchNumber","inc");
        JolokiaVersionRequest req2 = new JolokiaVersionRequest();
        List<?> resp = j4pClient.execute(req1,req2);
        assertEquals(resp.size(),2);
        assertTrue(resp.get(0) instanceof JolokiaExecResponse);
        assertTrue(resp.get(1) instanceof JolokiaVersionResponse);
        List<JolokiaResponse<JolokiaRequest>> typeSaveResp = j4pClient.execute(req1,req2);
        for (JolokiaResponse<?> r : typeSaveResp) {
            assertTrue(r instanceof JolokiaExecResponse || r instanceof JolokiaVersionResponse);
        }
    }

    @Test
    public void simpleBulkRequestWithOptions() throws MalformedObjectNameException, J4pException {
        JolokiaRequest req1 = new JolokiaReadRequest(itSetup.getAttributeMBean(), "ComplexNestedValue");
        JolokiaVersionRequest req2 = new JolokiaVersionRequest();
        Map<J4pQueryParameter, String> params = new HashMap<>();
        params.put(J4pQueryParameter.MAX_DEPTH, "2");
        List<?> resps = j4pClient.execute(Arrays.asList(req1, req2), params);
        assertEquals(resps.size(), 2);
        JolokiaReadResponse resp = (JolokiaReadResponse) resps.get(0);
        JSONObject value = resp.getValue();
        JSONArray inner = (JSONArray) value.get("Blub");
        assertTrue(inner.get(1) instanceof String);

        // requests can be found inside the response
        assertEquals(((JolokiaReadResponse) resps.get(0)).getRequest().getType(), JolokiaOperation.READ);
        assertEquals(((JolokiaVersionResponse) resps.get(1)).getRequest().getType(), JolokiaOperation.VERSION);
    }

    @Test
    public void simpleBulkRequestWithRequestExcludedFromResponse() throws MalformedObjectNameException, J4pException {
        JolokiaRequest req1 = new JolokiaReadRequest(itSetup.getAttributeMBean(), "ComplexNestedValue");
        JolokiaVersionRequest req2 = new JolokiaVersionRequest();
        Map<J4pQueryParameter, String> params = new HashMap<>();
        params.put(J4pQueryParameter.MAX_DEPTH, "2");
        params.put(J4pQueryParameter.INCLUDE_REQUEST, "false");
        List<?> resps = j4pClient.execute(Arrays.asList(req1, req2), params);
        assertEquals(resps.size(), 2);
        JolokiaReadResponse resp = (JolokiaReadResponse) resps.get(0);
        JSONObject value = resp.getValue();
        JSONArray inner = (JSONArray) value.get("Blub");
        assertTrue(inner.get(1) instanceof String);

        // requests can be found inside the response
        assertNull(((JolokiaReadResponse) resps.get(0)).getRequest());
        assertNull(((JolokiaVersionResponse) resps.get(1)).getRequest());

        // but we can correlate by order
        assertEquals(req1.getType(), ((JolokiaResponse<?>) resps.get(0)).getType());
        assertEquals(req2.getType(), ((JolokiaResponse<?>) resps.get(1)).getType());
    }

    @Test
    public void bulkRequestWithErrors() throws MalformedObjectNameException, J4pException {

        List<JolokiaReadRequest> requests = createBulkRequests();
        try {
            j4pClient.execute(requests);
            fail();
        } catch (J4pBulkRemoteException e) {
            List<?> results = e.getResults();
            assertEquals(3, results.size());
            results = e.getResponses();
            assertEquals(2, results.size());
            assertTrue(results.get(0) instanceof JolokiaReadResponse);
            assertEquals("Bla", ((JolokiaReadResponse) results.get(0)).getValue());
            assertTrue(results.get(1) instanceof JolokiaReadResponse);

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

    private List<JolokiaReadRequest> createBulkRequests() throws MalformedObjectNameException {
        JolokiaReadRequest req1 = new JolokiaReadRequest(itSetup.getAttributeMBean(),"ComplexNestedValue");
        req1.setPath("Blub/0");
        JolokiaReadRequest req2 = new JolokiaReadRequest("bla:type=blue","Sucks");
        JolokiaReadRequest req3 = new JolokiaReadRequest("java.lang:type=Memory","HeapMemoryUsage");
        return Arrays.asList(req1,req2,req3);
    }

    @Test
    public void optionalBulkRequestsWithExtractorAsArgument() throws MalformedObjectNameException, J4pException {
        List<JolokiaReadResponse> resp = j4pClient.execute(createBulkRequests(),null, ValidatingResponseExtractor.OPTIONAL);

        verifyOptionalBulkResponses(resp);
    }

    @Test
    public void optionalBulkRequestsWithExtractorAsDefault() throws MalformedObjectNameException, J4pException {
        J4pClient c = new J4pClientBuilder().url(j4pUrl)
                               .user("jolokia")
                               .password("jolokia")
//                               .authenticator(new BasicClientCustomizer().preemptive())
                               .responseExtractor(ValidatingResponseExtractor.OPTIONAL)
                               .build();

        List<JolokiaReadResponse> resp = c.execute(createBulkRequests());

        verifyOptionalBulkResponses(resp);
    }


    private void verifyOptionalBulkResponses(List<JolokiaReadResponse> resp) {
        assertEquals(3, resp.size());
        assertNotNull(resp.get(0));
        assertEquals("Bla", resp.get(0).getValue());
        assertNull(resp.get(1));
        assertNotNull(resp.get(2));
    }

}
