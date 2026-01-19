package org.jolokia.client;

/*
 * Copyright 2009-2011 Roland Huss
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.management.MalformedObjectNameException;

//import org.apache.http.HttpEntity;
//import org.apache.http.HttpResponse;
//import org.apache.http.client.HttpClient;
//import org.apache.http.conn.ConnectTimeoutException;
//import org.apache.http.message.BasicHeader;
import org.easymock.EasyMock;
import org.jolokia.client.exception.JolokiaException;
import org.jolokia.client.exception.JolokiaRemoteException;
import org.jolokia.client.exception.JolokiaTimeoutException;
import org.jolokia.client.jdkclient.JdkHttpClient;
import org.jolokia.client.request.JolokiaReadRequest;
import org.jolokia.client.response.JolokiaReadResponse;
import org.jolokia.client.spi.HttpClientSpi;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.collections.Maps;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

/**
 * @author roland
 * @since 23.09.11
 */
public class JolokiaClientTest {

    private static final String MEMORY_RESPONSE = """
        {
            "timestamp":1316801201,
            "status":200,
            "request": { "mbean":"java.lang:type=Memory", "attribute":"HeapMemoryUsage", "type":"read"},
            "value": {"max":530186240, "committed":85000192, "init":0, "used":17962568 }
        }
        """;

    private static final String EMPTY_RESPONSE = "{}";

    private static final String ARRAY_RESPONSE = "[ " + MEMORY_RESPONSE + "]";
    public static final URI TEST_URL = URI.create("http://localhost:8080/jolokia");

    private static final String ERROR_VALUE_RESPONSE = """
        {
            "error_type": "errorType",
            "status": 500,
            "error_value": {"test":"ok"}
        }
        """;

    public JolokiaReadRequest TEST_REQUEST, TEST_REQUEST_2;

    @BeforeClass
    public void setup() throws MalformedObjectNameException {
        TEST_REQUEST = new JolokiaReadRequest("java.lang:type=Memory", "HeapMemoryUsage");
        TEST_REQUEST_2 = new JolokiaReadRequest("java.lang:type=Memory", "NonHeapMemoryUsage");
    }

    @Test
    public void simple() throws JolokiaException, IOException {
        HttpClientSpi<?> client = prepareMocks("utf-8", MEMORY_RESPONSE);

        JolokiaClient j4p = new JolokiaClient(TEST_URL, client);
        JolokiaReadResponse resp = j4p.execute(TEST_REQUEST);
        assertEquals(((Map<?, ?>) resp.getValue()).get("max"), 530186240L);
    }

    @Test(expectedExceptions = JolokiaException.class, expectedExceptionsMessageRegExp = ".*JSONArray.*")
    public void invalidArrayResponse() throws JolokiaException, IOException {
        HttpClientSpi<?> client = prepareMocks(null, ARRAY_RESPONSE);

        JolokiaClient j4p = new JolokiaClient(TEST_URL, client);
        Map<JolokiaQueryParameter, String> opts = new HashMap<>();
        opts.put(JolokiaQueryParameter.IGNORE_ERRORS, "true");
        j4p.execute(TEST_REQUEST, opts);
    }

    @Test(expectedExceptions = JolokiaTimeoutException.class, expectedExceptionsMessageRegExp = ".*timeout.*")
    public void timeout() throws IOException, JolokiaException {
        throwException(false, new HttpConnectTimeoutException("timeout"));
    }

    @Test(expectedExceptions = JolokiaException.class, expectedExceptionsMessageRegExp = ".*I/O exception.*")
    public void ioException() throws IOException, JolokiaException {
        throwException(false, new IOException());
    }

    @Test(expectedExceptions = JolokiaTimeoutException.class, expectedExceptionsMessageRegExp = ".*timeout.*")
    public void connectExceptionForBulkRequests() throws IOException, JolokiaException {
        throwException(true, new HttpConnectTimeoutException("timeout"));
    }

    @Test(expectedExceptions = JolokiaException.class, expectedExceptionsMessageRegExp = ".*I/O exception.*")
    public void ioExceptionForBulkRequests() throws IOException, JolokiaException {
        throwException(true, new IOException());
    }

    @Test(expectedExceptions = JolokiaException.class, expectedExceptionsMessageRegExp = ".*Invalid JSON response for a single request.*")
    public void throwIOExceptionWhenParsingAnswer() throws IOException, JolokiaException {
        HttpClientSpi<?> client = createMock(HttpClientSpi.class);
        HttpResponse<InputStream> response = createMock(HttpResponse.class);
        expect(response.statusCode()).andReturn(200);
        expect(response.headers()).andReturn(HttpHeaders.of(Collections.emptyMap(), (n, v) -> true));
        expect(response.body()).andReturn(new ByteArrayInputStream(new byte[0]));
        expect(client.execute(EasyMock.anyObject(), EasyMock.anyObject(), EasyMock.anyObject(), EasyMock.anyObject())).andReturn(null);
//        expect(response.body()).andThrow(new IOException());
        replay(client, response);

        JolokiaClient j4p = new JolokiaClient(TEST_URL, client);
        j4p.execute(TEST_REQUEST);
    }

    @Test(expectedExceptions = JolokiaException.class, expectedExceptionsMessageRegExp = ".*Invalid.*bulk.*")
    public void invalidBulkRequestResponse() throws IOException, JolokiaException {
        HttpClientSpi<?> client = prepareMocks(null, MEMORY_RESPONSE, true);

        JolokiaClient j4p = new JolokiaClient(TEST_URL, client);
        j4p.execute(TEST_REQUEST, TEST_REQUEST_2);
    }

    @Test(expectedExceptions = JolokiaRemoteException.class, expectedExceptionsMessageRegExp = ".*Invalid.*")
    public void noStatus() throws IOException, JolokiaException {
        HttpClientSpi<?> client = prepareMocks(null, EMPTY_RESPONSE);

        JolokiaClient j4p = new JolokiaClient(TEST_URL, client);
        j4p.execute(TEST_REQUEST);
    }

    @Test(expectedExceptions = JolokiaRemoteException.class)
    public void remoteExceptionErrorValue() throws IOException, JolokiaException {
        HttpClientSpi<?> client = prepareMocks("utf-8", ERROR_VALUE_RESPONSE);

        JolokiaClient j4p = new JolokiaClient(TEST_URL, client);
        Map<JolokiaQueryParameter, String> options = Maps.newHashMap();
        options.put(JolokiaQueryParameter.SERIALIZE_EXCEPTION, "true");
        options.put(JolokiaQueryParameter.INCLUDE_STACKTRACE, "false");

        try {
            j4p.execute(TEST_REQUEST, options);
        } catch (JolokiaRemoteException e) {
            assertEquals(e.getErrorValue().toJSONString(), "{\"test\":\"ok\"}");
            throw e;
        }

        fail("No exception was thrown");
    }

    @SuppressWarnings("unchecked")
    private void throwException(boolean bulk, Exception exp) throws IOException, JolokiaException {
        HttpClient client = createMock(HttpClient.class);
        try {
            expect(client.send(EasyMock.anyObject(), (HttpResponse.BodyHandler<InputStream>) EasyMock.anyObject())).andThrow(exp);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        replay(client);

        JolokiaClient j4p = new JolokiaClient(TEST_URL, new JdkHttpClient(client, JolokiaClientBuilder.Configuration.withUrl(URI.create("http://localhost"))));
        if (bulk) {
            j4p.execute(TEST_REQUEST, TEST_REQUEST_2);
        } else {
            j4p.execute(TEST_REQUEST);
        }
    }

    private HttpClientSpi<?> prepareMocks(String encoding, String jsonResp) throws IOException {
        return prepareMocks(encoding, jsonResp, false);
    }

    @SuppressWarnings("unchecked")
    private HttpClientSpi<?> prepareMocks(String encoding, String jsonResp, boolean bulk) throws IOException {
        HttpClient client = createMock(HttpClient.class);
        HttpResponse<InputStream> response = createMock(HttpResponse.class);
        HttpClientSpi<HttpClient> spi = new JdkHttpClient(client, JolokiaClientBuilder.Configuration.withUrl(URI.create("http://localhost")));
        try {
            expect(client.send(EasyMock.anyObject(), (HttpResponse.BodyHandler<InputStream>) EasyMock.anyObject())).andReturn(response);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        final ByteArrayInputStream bis = new ByteArrayInputStream(jsonResp.getBytes());
        expect(response.statusCode()).andReturn(200);
        expect(response.headers()).andReturn(HttpHeaders.of(Collections.emptyMap(), (n, v) -> true));
        expect(response.body()).andReturn(bis).anyTimes();

        replay(client, response);
        return spi;
    }

}
