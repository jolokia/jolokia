package org.jolokia.jvmagent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.management.JMException;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.jolokia.restrictor.DenyAllRestrictor;
import org.jolokia.util.TestJolokiaContext;
import org.jolokia.util.TestRequestDispatcher;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.expect;
import static org.testng.Assert.assertTrue;

/**
 * @author roland
 * @since 17.05.13
 */
public class JolokiaHttpHandlerRestrictorTest {

    @Test
    void denyAllRestrictor() throws JMException, IOException, ParseException, URISyntaxException {
        TestJolokiaContext testContext = new TestJolokiaContext.Builder()
                .restrictor(new DenyAllRestrictor()).build();
        checkRestrictor("No access", testContext);
    }

    private void checkRestrictor(String pParam, TestJolokiaContext pTestContext) throws URISyntaxException, IOException, ParseException, JMException {
        JolokiaHttpHandler newHandler = new JolokiaHttpHandler(pTestContext, new TestRequestDispatcher(pTestContext));
        HttpExchange exchange = JolokiaHttpHandlerTest.prepareExchange("http://localhost:8080/jolokia/read/java.lang:type=Memory/HeapMemoryUsage");
        // Simple GET method
        expect(exchange.getRequestMethod()).andReturn("GET");
        Headers header = new Headers();
        ByteArrayOutputStream out = JolokiaHttpHandlerTest.prepareResponse(exchange, header);
        newHandler.start(false);
        try {
            newHandler.handle(exchange);
        } finally {
            newHandler.stop();
        }
        JSONObject resp = (JSONObject) new JSONParser().parse(out.toString());
        assertTrue(resp.containsKey("error"));
        assertTrue(((String) resp.get("error")).contains(pParam));

    }


}
