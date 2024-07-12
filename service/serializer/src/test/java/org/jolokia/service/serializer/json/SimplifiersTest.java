package org.jolokia.service.serializer.json;

import org.jolokia.json.JSONObject;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.jolokia.server.core.service.serializer.ValueFaultHandler;
import org.jolokia.service.serializer.json.simplifier.BigIntegerSimplifier;
import org.jolokia.service.serializer.json.simplifier.FileSimplifier;
import org.jolokia.service.serializer.json.simplifier.UrlSimplifier;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.AttributeNotFoundException;
import java.io.File;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

/**
 * @author Neven Radovanović
 * @since June 27, 2018
 */
@Test()
public class SimplifiersTest {

    private BigIntegerSimplifier bigIntegerSimplifier;
    private UrlSimplifier urlSimplifier;
    private ObjectToJsonConverter converter;
    private FileSimplifier fileSimplifier;

    @BeforeMethod
    public void setup() {
        bigIntegerSimplifier = new BigIntegerSimplifier();
        urlSimplifier = new UrlSimplifier();
        fileSimplifier = new FileSimplifier();

        // Needed for subclassing final object
        converter = new ObjectToJsonConverter(null);
        converter.setupContext(new SerializeOptions.Builder().faultHandler(ValueFaultHandler.THROWING_VALUE_FAULT_HANDLER).build());
    }

    @Test
    public void bigIntegerSimplifier() throws AttributeNotFoundException {
        BigInteger bigInt = new BigInteger("12345678901234567890");
        Object result = bigIntegerSimplifier.extractObject(converter, bigInt, new LinkedList<>(), false);
        assertEquals(result, bigInt);
    }

    @Test
    public void bigIntegerSimplifierJson() throws AttributeNotFoundException {
        BigInteger bigInt = new BigInteger("12345678901234567890");
        Object result = bigIntegerSimplifier.extractObject(converter, bigInt, new LinkedList<>(), true);
        assertEquals(((JSONObject) result).toJSONString(), "{\"bigint\":\"12345678901234567890\"}");
    }

    @Test
    public void urlSimplifier() throws AttributeNotFoundException, MalformedURLException {
        URL url = new URL("https://www.jolokia.org");
        Object result = urlSimplifier.extractObject(converter, url, new LinkedList<>(), false);
        assertEquals(result, url);
    }

    @Test
    public void fileSimplifier() throws AttributeNotFoundException {
        File file = new File("/etc/os-release");
        Object result = fileSimplifier.extractObject(converter, file, new LinkedList<>(), true);
        assertTrue(((JSONObject) result).toJSONString().contains("{\"name\":\"os-release\""));
    }

    @Test
    public void urlSimplifierJson() throws AttributeNotFoundException, MalformedURLException {
        URL url = new URL("https://www.jolokia.org");
        Object result = urlSimplifier.extractObject(converter, url, new LinkedList<>(), true);
        assertEquals(((JSONObject) result).toJSONString(), "{\"url\":\"https://www.jolokia.org\"}");
    }


    private static class PathAttributeFilterValueFaultHandler implements ValueFaultHandler {
        private final ValueFaultHandler origHandler;

        PathAttributeFilterValueFaultHandler(ValueFaultHandler pOrigHandler) {
            origHandler = pOrigHandler;
        }

        public <T extends Throwable> Object handleException(T exception) throws T {
            if (exception instanceof AttributeNotFoundException) {
                throw new AttributeFilteredException(exception.getMessage());
            } else {
                return origHandler.handleException(exception);
            }
        }
    }

}
