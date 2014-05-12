package org.jolokia.osgi.security;

import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 26.05.14
 */

public class AuthorizationHeaderParserTest {

    @Test
    public void testParseAuthorizationPositive() throws Exception {
        AuthorizationHeaderParser.Result result =
                AuthorizationHeaderParser.parse("Basic cm9sYW5kOnMhY3IhdA==");
        assertEquals(result.getUser(),"roland");
        assertEquals(result.getPassword(),"s!cr!t");
        assertTrue(result.isValid());
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".*BasicAuthentication.*")
    public void testParseAuthorizationWrongMethod() throws Exception {
        AuthorizationHeaderParser.parse("Digest cm9sYW5kOnMhY3IhdA==");
    }

    @Test
    public void testParseAuthorizationInvalidFormat() throws Exception {
        AuthorizationHeaderParser.Result result =
                AuthorizationHeaderParser.parse("Basic cm9sYAZ5kOnMhA=");
        assertFalse(result.isValid());
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void decodeNull() {
        AuthorizationHeaderParser.decode(null);
    }

    @Test
    public void decodeEmpty() {
        assertEquals(AuthorizationHeaderParser.decode("").length,0);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void decodeToSmall() {
        assertEquals(AuthorizationHeaderParser.decode("abc").length,0);
    }

    @Test
    public void decodeBig() {
        byte[] res = AuthorizationHeaderParser.decode("TG9yZW0gaXBzdW0gZG9sb3Igc2l0IGFtZXQsIGNvbnNldGV0dXIgc2FkaXBzY2luZyBlbGl0ciwg\n" +
                                                      "c2VkIGRpYW0gbm9udW15IGVpcm1vZCB0ZW1wb3IgaW52aWR1bnQgdXQgbGFib3JlIGV0IGRvbG9y\n" +
                                                      "ZSBtYWduYSBhbGlxdXlhbSBlcmF0LCBzZWQgZGlhbSB2b2x1cHR1YS4gQXQgdmVybyBlb3MgZXQg\n" +
                                                      "YWNjdXNhbSBldCBqdXN0byBkdW8gZG9sb3JlcyBldCBlYSByZWJ1bS4gU3RldCBjbGl0YSBrYXNk\n" +
                                                      "IGd1YmVyZ3Jlbiwgbm8gc2VhIHRha2ltYXRhIHNhbmN0dXMgZXN0IExvcmVtIGlwc3VtIGRvbG9y\n" +
                                                      "IHNpdCBhbWV0LiBMb3JlbSBpcHN1bSBkb2xvciBzaXQgYW1ldCwgY29uc2V0ZXR1ciBzYWRpcHNj\n" +
                                                      "aW5nIGVsaXRyLCBzZWQgZGlhbSBub251bXkgZWlybW9kIHRlbXBvciBpbnZpZHVudCB1dCBsYWJv\n" +
                                                      "cmUgZXQgZG9sb3JlIG1hZ25hIGFsaXF1eWFtIGVyYXQsIHNlZCBkaWFtIHZvbHVwdHVhLiBBdCB2\n" +
                                                      "ZXJvIGVvcyBldCBhY2N1c2FtIGV0IGp1c3RvIGR1byBkb2xvcmVzIGV0IGVhIHJlYnVtLiBTdGV0\n" +
                                                      "IGNsaXRhIGthc2QgZ3ViZXJncmVuLCBubyBzZWEgdGFraW1hdGEgc2FuY3R1cyBlc3QgTG9yZW0g\n" +
                                                      "aXBzdW0gZG9sb3Igc2l0IGFtZXQuIDEyMzQ1IS84Ly8vMzQ1KiY=");
        Assert.assertEquals(new String(res), "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod " +
                                             "tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero " +
                                             "eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata " +
                                             "sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing " +
                                             "elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed " +
                                             "diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd " +
                                             "gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. 12345!/8///345*&");
    }
}
