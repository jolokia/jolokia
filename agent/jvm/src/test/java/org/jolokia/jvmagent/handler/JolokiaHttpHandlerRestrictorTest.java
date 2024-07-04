package org.jolokia.jvmagent.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.jolokia.server.core.restrictor.DenyAllRestrictor;
import org.jolokia.server.core.util.JSONAware;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.expect;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 17.05.13
 */
public class JolokiaHttpHandlerRestrictorTest {

    @Test
    void denyAllRestrictor() throws IOException, JSONException, URISyntaxException {
        TestJolokiaContext testContext = new TestJolokiaContext.Builder()
                .restrictor(new DenyAllRestrictor()).build();
        checkRestrictor("No access", testContext);
    }

    private void checkRestrictor(String pParam, TestJolokiaContext pTestContext) throws URISyntaxException, IOException, JSONException {
        JolokiaHttpHandler newHandler = new JolokiaHttpHandler(pTestContext);
        HttpExchange exchange = JolokiaHttpHandlerTest.prepareExchange("http://localhost:8080/jolokia/read/java.lang:type=Memory/HeapMemoryUsage");
        // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("GET");
        Headers header = new Headers();
        ByteArrayOutputStream out = JolokiaHttpHandlerTest.prepareResponse(exchange, header);
        newHandler.handle(exchange);
        JSONObject resp = JSONAware.parse(new StringReader(out.toString())).getObject();
        assertTrue(resp.has("error"));
        assertTrue(((String) resp.get("error")).contains(pParam));
    }
}
