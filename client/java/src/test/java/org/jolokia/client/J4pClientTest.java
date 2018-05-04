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
import java.util.*;

import javax.management.MalformedObjectNameException;

import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.easymock.EasyMock;
import org.jolokia.client.exception.*;
import org.jolokia.client.request.*;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.testng.collections.Maps;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 23.09.11
 */
public class J4pClientTest {

    private static String MEMORY_RESPONSE =
            "{\"timestamp\":1316801201,\"status\":200," +
            "\"request\":{\"mbean\":\"java.lang:type=Memory\",\"attribute\":\"HeapMemoryUsage\",\"type\":\"read\"}," +
            "\"value\":{\"max\":530186240,\"committed\":85000192,\"init\":0,\"used\":17962568}}";

    private static String EMPTY_RESPONSE = "{}";

    private static String ARRAY_RESPONSE = "[ " + MEMORY_RESPONSE + "]";
    public static final String TEST_URL = "http://localhost:8080/jolokia";

	private static final String ERROR_VALUE_RESPONSE = "{" +
			"\"error_type\":\"errorType\"" +
			"\"status\":500" +
			"\"error_value\":{\"test\":\"ok\"}" +
			"}";

    public J4pReadRequest TEST_REQUEST,TEST_REQUEST_2;


    @BeforeTest
    public void setup() throws MalformedObjectNameException {
        TEST_REQUEST = new J4pReadRequest("java.lang:type=Memory", "HeapMemoryUsage");
        TEST_REQUEST_2 = new J4pReadRequest("java.lang:type=Memory", "NonHeapMemoryUsage");
    }

    @Test
    public void simple() throws MalformedObjectNameException, J4pException, IOException {
        HttpClient client = prepareMocks("utf-8",MEMORY_RESPONSE);

        J4pClient j4p = new J4pClient(TEST_URL,client);
        J4pReadResponse resp = j4p.execute(TEST_REQUEST);
        assertEquals(((Map) resp.getValue()).get("max"), 530186240L);
    }

    @Test(expectedExceptions = J4pException.class,expectedExceptionsMessageRegExp = ".*JSONArray.*")
    public void invalidArrayResponse() throws MalformedObjectNameException, J4pException, IOException {
        HttpClient client = prepareMocks(null,ARRAY_RESPONSE);

        J4pClient j4p = new J4pClient(TEST_URL,client);
        Map<J4pQueryParameter,String> opts = new HashMap<J4pQueryParameter, String>();;
        opts.put(J4pQueryParameter.IGNORE_ERRORS,"true");
        j4p.execute(TEST_REQUEST, opts);
    }

    @Test(expectedExceptions = J4pTimeoutException.class,expectedExceptionsMessageRegExp = ".*timeout.*")
    public void timeout() throws IOException, MalformedObjectNameException, J4pException {
        throwException(false, new ConnectTimeoutException());
    }

    @Test(expectedExceptions = J4pException.class,expectedExceptionsMessageRegExp = ".*IO-Error.*")
    public void ioException() throws IOException, MalformedObjectNameException, J4pException {
        throwException(false,new IOException());
    }

    @Test(expectedExceptions = J4pTimeoutException.class,expectedExceptionsMessageRegExp = ".*timeout.*")
    public void connectExceptionForBulkRequests() throws IOException, J4pException {
        throwException(true,new ConnectTimeoutException());
    }

    @Test(expectedExceptions = J4pException.class,expectedExceptionsMessageRegExp = ".*IO-Error.*")
    public void ioExceptionForBulkRequests() throws IOException, J4pException {
        throwException(true,new IOException());
    }

    @Test(expectedExceptions = J4pException.class,expectedExceptionsMessageRegExp = ".*reading.*response.*")
    public void throwIOExceptionWhenParsingAnswer() throws IOException, J4pException {
        HttpClient client = createMock(HttpClient.class);
        HttpResponse response  = createMock(HttpResponse.class);
        HttpEntity entity = createMock(HttpEntity.class);
        expect(client.execute(EasyMock.<HttpUriRequest>anyObject())).andReturn(response);
        expect(response.getEntity()).andReturn(entity);
        expect(entity.getContentEncoding()).andReturn(null);
        expect(entity.isStreaming()).andReturn(false);
        expect(entity.getContent()).andThrow(new IOException());
        replay(client, entity, response);

        J4pClient j4p = new J4pClient(TEST_URL,client);
        j4p.execute(TEST_REQUEST);
    }

    @Test(expectedExceptions = J4pException.class,expectedExceptionsMessageRegExp = ".*Invalid.*bulk.*")
    public void invalidBulkRequestResponse() throws IOException, J4pException {
        HttpClient client = prepareMocks(null,MEMORY_RESPONSE);

        J4pClient j4p = new J4pClient(TEST_URL,client);
        j4p.execute(TEST_REQUEST,TEST_REQUEST_2);
    }

    @Test(expectedExceptions = J4pRemoteException.class,expectedExceptionsMessageRegExp = ".*Invalid.*")
    public void noStatus() throws IOException, J4pException {
        HttpClient client = prepareMocks(null,EMPTY_RESPONSE);

        J4pClient j4p = new J4pClient(TEST_URL,client);
        j4p.execute(TEST_REQUEST);
    }
    
    @Test(expectedExceptions = J4pRemoteException.class)
    public void remoteExceptionErrorValue() throws IOException, J4pException {
        HttpClient client = prepareMocks("utf-8", ERROR_VALUE_RESPONSE);

        J4pClient j4p = new J4pClient(TEST_URL,client);
        Map<J4pQueryParameter, String> options = Maps.newHashMap();
        options.put(J4pQueryParameter.SERIALIZE_EXCEPTION, "true");
        options.put(J4pQueryParameter.INCLUDE_STACKTRACE, "false");

		try {
			j4p.execute(TEST_REQUEST, options);
		} catch (J4pRemoteException e) {
			assertEquals(e.getErrorValue().toJSONString(), "{\"test\":\"ok\"}");
			throw e;
		}
		
		fail("No exception was thrown");
    }

    private void throwException(boolean bulk,Exception exp) throws IOException, J4pException {
        HttpClient client = createMock(HttpClient.class);
        expect(client.execute(EasyMock.<HttpUriRequest>anyObject())).andThrow(exp);
        replay(client);

        J4pClient j4p = new J4pClient(TEST_URL,client);
        if (bulk) {
            j4p.execute(TEST_REQUEST, TEST_REQUEST_2);
        } else {
            j4p.execute(TEST_REQUEST);
        }
    }

    private HttpClient prepareMocks(String encoding,String jsonResp) throws IOException {
        HttpClient client = createMock(HttpClient.class);
        HttpResponse response  = createMock(HttpResponse.class);
        HttpEntity entity = createMock(HttpEntity.class);
        expect(client.execute(EasyMock.<HttpUriRequest>anyObject())).andReturn(response);
        expect(response.getEntity()).andReturn(entity);
        expect(entity.isStreaming()).andReturn(false);
        expect(entity.getContentEncoding()).andReturn(encoding != null ? new BasicHeader("Content-Encoding",encoding) : null);

        final ByteArrayInputStream bis =
                new ByteArrayInputStream(jsonResp.getBytes());
        expect(entity.getContent()).andReturn(bis);
        replay(client, response, entity);
        return client;
    }
}
