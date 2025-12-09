package org.jolokia.converter.json;

import org.jolokia.json.JSONObject;
import org.jolokia.core.service.serializer.SerializeOptions;
import org.jolokia.core.service.serializer.ValueFaultHandler;
import org.jolokia.converter.json.simplifier.FileSimplifier;
import org.jolokia.converter.json.simplifier.UrlSimplifier;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.AttributeNotFoundException;
import java.io.File;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.testng.Assert.assertEquals;

/**
 * @author Neven Radovanović
 * @since June 27, 2018
 */
@Test()
public class SimplifiersTest {

    private UrlSimplifier urlSimplifier;
    private org.jolokia.converter.json.ObjectToJsonConverter converter;
    private FileSimplifier fileSimplifier;

    @BeforeMethod
    public void setup() {
        urlSimplifier = new UrlSimplifier();
        fileSimplifier = new FileSimplifier();

        // Needed for subclassing final object
        converter = new org.jolokia.converter.json.ObjectToJsonConverter(null, null, null);
        converter.setupContext(new SerializeOptions.Builder().faultHandler(ValueFaultHandler.THROWING_VALUE_FAULT_HANDLER).build());
    }

    @Test
    public void bigIntegerSimplifier() throws AttributeNotFoundException {
        BigInteger bigInt = new BigInteger("12345678901234567890");
        Object result = converter.extractObject(bigInt, new LinkedList<>(), false);
        assertEquals(result, bigInt);
    }

    @Test
    public void bigIntegerSimplifierJson() throws AttributeNotFoundException {
        BigInteger bigInt = new BigInteger("12345678901234567890");
        Object result = converter.extractObject(bigInt, new LinkedList<>(), true);
        // BigInteger is proper JSON value, so no need to convert it to String
        assertEquals(result, bigInt);
    }

    @Test
    public void urlSimplifier() throws AttributeNotFoundException, MalformedURLException {
        URL url = new URL("https://www.jolokia.org");
        Object result = urlSimplifier.extractObject(converter, url, new LinkedList<>(), false);
        assertEquals(result, url);
    }

    @Test(expectedExceptions = ValueFaultHandler.AttributeFilteredException.class)
    public void urlSimplifierWithPaths() throws AttributeNotFoundException, MalformedURLException {
        URL url = new URL("https://www.jolokia.org");
        // we can get a "url" property from the object returned by UrlSimplifier
        Object result = urlSimplifier.extractObject(converter, url, new LinkedList<>(List.of("url")), false);
        assertEquals(result, "https://www.jolokia.org");

        // but we can't get "bytes" property of a String
        urlSimplifier.extractObject(converter, url, new LinkedList<>(Arrays.asList("url", "bytes")), false);
    }

    @Test
    public void fileSimplifier() throws AttributeNotFoundException {
        File file = new File("/etc/os-release");
        Object result = fileSimplifier.extractObject(converter, file, new LinkedList<>(), true);
        assertEquals(((JSONObject) result).get("name"), "os-release");
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
