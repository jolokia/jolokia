package org.jolokia.converter.json;

import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Stack;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.json.simplifier.BigIntegerSimplifier;
import org.jolokia.converter.json.simplifier.UrlSimplifier;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @author Neven Radovanović
 * @since June 27, 2018
 */
@Test()
public class SimplifiersTest {

    private BigIntegerSimplifier bigIntegerSimplifier;
    private UrlSimplifier urlSimplifier;
    private ObjectToJsonConverter converter;

    @BeforeMethod
    public void setup() {
        bigIntegerSimplifier = new BigIntegerSimplifier();
        urlSimplifier = new UrlSimplifier();

        // Needed for subclassing final object
        converter = new ObjectToJsonConverter(null);
        converter.setupContext(new JsonConvertOptions.Builder().faultHandler(ValueFaultHandler.THROWING_VALUE_FAULT_HANDLER).build());
    }

    @Test
    public void bigIntegerSimplifier() throws AttributeNotFoundException {
        BigInteger bigInt = new BigInteger("12345678901234567890");
        Object result = bigIntegerSimplifier.extractObject(converter, bigInt, new Stack<String>(), false);
        assertEquals(result, bigInt);
    }

    @Test
    public void bigIntegerSimplifierJson() throws AttributeNotFoundException {
        BigInteger bigInt = new BigInteger("12345678901234567890");
        Object result = bigIntegerSimplifier.extractObject(converter, bigInt, new Stack<String>(), true);
        assertEquals(result.toString(), "{\"bigint\":\"12345678901234567890\"}");
    }

    @Test
    public void urlSimplifier() throws AttributeNotFoundException, MalformedURLException {
        URL url = new URL("https://www.jolokia.org");
        Object result = urlSimplifier.extractObject(converter, url, new Stack<String>(), false);
        assertEquals(result, url);
    }

    @Test
    public void urlSimplifierJson() throws AttributeNotFoundException, MalformedURLException {
        URL url = new URL("https://www.jolokia.org");
        Object result = urlSimplifier.extractObject(converter, url, new Stack<String>(), true);
        assertEquals(result.toString(), "{\"url\":\"https:\\/\\/www.jolokia.org\"}");
    }


    private class PathAttributeFilterValueFaultHandler implements ValueFaultHandler {
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
