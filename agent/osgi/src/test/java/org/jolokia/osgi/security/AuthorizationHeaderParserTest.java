package org.jolokia.osgi.security;

import org.jolokia.util.Base64Util;
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

}
