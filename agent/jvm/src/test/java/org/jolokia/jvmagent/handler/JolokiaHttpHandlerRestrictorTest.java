package org.jolokia.jvmagent.handler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.jolokia.server.core.restrictor.DenyAllRestrictor;
import org.jolokia.server.core.util.TestJolokiaContext;
import org.jolokia.json.JSONObject;
import org.jolokia.json.parser.JSONParser;
import org.jolokia.json.parser.ParseException;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.expect;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 17.05.13
 */
public class JolokiaHttpHandlerRestrictorTest {

    @Test
    void denyAllRestrictor() throws IOException, ParseException, URISyntaxException {
        TestJolokiaContext testContext = new TestJolokiaContext.Builder()
                .restrictor(new DenyAllRestrictor()).build();
        checkRestrictor("No access", testContext);
    }

    private void checkRestrictor(String pParam, TestJolokiaContext pTestContext) throws URISyntaxException, IOException, ParseException {
        JolokiaHttpHandler newHandler = new JolokiaHttpHandler(pTestContext);
        HttpExchange exchange = JolokiaHttpHandlerTest.prepareExchange("http://localhost:8080/jolokia/read/java.lang:type=Memory/HeapMemoryUsage");
        // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("GET");
        Headers header = new Headers();
        ByteArrayOutputStream out = JolokiaHttpHandlerTest.prepareResponse(exchange, header);
        newHandler.handle(exchange);
        JSONObject resp = new JSONParser().parse(out.toString(), JSONObject.class);
        assertTrue(resp.containsKey("error"));
        assertTrue(((String) resp.get("error")).contains(pParam));
    }
}
