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

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import javax.management.MalformedObjectNameException;

import org.jolokia.client.JolokiaClient;
import org.jolokia.client.JolokiaClientBuilder;
import org.jolokia.client.JolokiaQueryParameter;
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
public class ClientBulkRequestIntegrationTest extends AbstractClientIntegrationTest {

    @Test
    public void simpleBulkRequest() throws MalformedObjectNameException, JolokiaException {
        JolokiaRequest req1 = new JolokiaExecRequest(itSetup.getOperationMBean(),"fetchNumber","inc");
        JolokiaVersionRequest req2 = new JolokiaVersionRequest();
        List<?> resp = jolokiaClient.execute(req1,req2);
        assertEquals(2, resp.size());
        assertTrue(resp.get(0) instanceof JolokiaExecResponse);
        assertTrue(resp.get(1) instanceof JolokiaVersionResponse);
        List<JolokiaResponse<JolokiaRequest>> typeSaveResp = jolokiaClient.execute(req1,req2);
        for (JolokiaResponse<?> r : typeSaveResp) {
            assertTrue(r instanceof JolokiaExecResponse || r instanceof JolokiaVersionResponse);
        }
    }

    @Test
    public void simpleBulkRequestWithOptions() throws MalformedObjectNameException, JolokiaException {
        JolokiaRequest req1 = new JolokiaReadRequest(itSetup.getAttributeMBean(), "ComplexNestedValue");
        JolokiaVersionRequest req2 = new JolokiaVersionRequest();
        Map<JolokiaQueryParameter, String> params = new HashMap<>();
        params.put(JolokiaQueryParameter.MAX_DEPTH, "2");
        List<?> responses = jolokiaClient.execute(Arrays.asList(req1, req2), params);
        assertEquals(2, responses.size());
        JolokiaReadResponse resp = (JolokiaReadResponse) responses.get(0);
        JSONObject value = resp.getValue();
        JSONArray inner = (JSONArray) value.get("Blub");
        assertTrue(inner.get(1) instanceof String);

        // requests can be found inside the response
        assertEquals(JolokiaOperation.READ, ((JolokiaReadResponse) responses.get(0)).getRequest().getType());
        assertEquals(JolokiaOperation.VERSION, ((JolokiaVersionResponse) responses.get(1)).getRequest().getType());
    }

    @Test
    public void simpleBulkRequestWithRequestExcludedFromResponse() throws MalformedObjectNameException, JolokiaException {
        JolokiaRequest req1 = new JolokiaReadRequest(itSetup.getAttributeMBean(), "ComplexNestedValue");
        JolokiaVersionRequest req2 = new JolokiaVersionRequest();
        Map<JolokiaQueryParameter, String> params = new HashMap<>();
        params.put(JolokiaQueryParameter.MAX_DEPTH, "2");
        params.put(JolokiaQueryParameter.INCLUDE_REQUEST, "false");
        List<?> responses = jolokiaClient.execute(Arrays.asList(req1, req2), params);
        assertEquals(2, responses.size());
        JolokiaReadResponse resp = (JolokiaReadResponse) responses.get(0);
        JSONObject value = resp.getValue();
        JSONArray inner = (JSONArray) value.get("Blub");
        assertTrue(inner.get(1) instanceof String);

        // requests can be found inside the response
        assertNull(((JolokiaReadResponse) responses.get(0)).getRequest());
        assertNull(((JolokiaVersionResponse) responses.get(1)).getRequest());

        // but we can correlate by order
        assertEquals(req1.getType(), ((JolokiaResponse<?>) responses.get(0)).getType());
        assertEquals(req2.getType(), ((JolokiaResponse<?>) responses.get(1)).getType());
    }

    @Test
    public void bulkRequestWithErrors() throws MalformedObjectNameException, JolokiaException {

        List<JolokiaReadRequest> requests = createBulkRequests();
        try {
            jolokiaClient.execute(requests);
            fail();
        } catch (JolokiaBulkRemoteException e) {
            List<?> results = e.getResults();
            assertEquals(3, results.size());
            results = e.getResponses();
            assertEquals(2, results.size());
            assertTrue(results.get(0) instanceof JolokiaReadResponse);
            assertEquals("Bla", ((JolokiaReadResponse) results.get(0)).getValue());
            assertTrue(results.get(1) instanceof JolokiaReadResponse);

            results = e.getRemoteExceptions();
            assertEquals(1, results.size());
            assertTrue(results.get(0) instanceof JolokiaRemoteException);
            JolokiaRemoteException exp = (JolokiaRemoteException) results.get(0);
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
    public void optionalBulkRequestsWithExtractorAsArgument() throws MalformedObjectNameException, JolokiaException {
        List<JolokiaReadResponse> resp = jolokiaClient.execute(createBulkRequests(),null, ValidatingResponseExtractor.OPTIONAL);

        verifyOptionalBulkResponses(resp);
    }

    @Test
    public void optionalBulkRequestsWithExtractorAsDefault() throws MalformedObjectNameException, JolokiaException, IOException {
        JolokiaClient c = new JolokiaClientBuilder().url(jolokiaUrl)
            .user("jolokia")
            .password("jolokia")
            .protocolVersion("TLSv1.3")
            .keystore(Path.of("../java/src/test/resources/certificates/client.p12"))
            .keystorePassword("1234")
            .keyPassword("1234")
            .truststore(Path.of("../java/src/test/resources/certificates/server.p12"))
            .truststorePassword("1234")
            .responseExtractor(ValidatingResponseExtractor.OPTIONAL)
            .build();

        List<JolokiaReadResponse> resp = c.execute(createBulkRequests());

        verifyOptionalBulkResponses(resp);
        c.close();
    }

    private void verifyOptionalBulkResponses(List<JolokiaReadResponse> resp) {
        assertEquals(3, resp.size());
        assertNotNull(resp.get(0));
        assertEquals("Bla", resp.get(0).getValue());
        assertNull(resp.get(1));
        assertNotNull(resp.get(2));
    }

}
