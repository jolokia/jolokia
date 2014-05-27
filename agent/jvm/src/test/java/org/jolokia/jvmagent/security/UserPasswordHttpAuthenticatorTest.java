package org.jolokia.jvmagent.security;

import org.testng.annotations.Test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class UserPasswordHttpAuthenticatorTest {

    @Test
    public void testCheckCredentials() throws Exception {
        UserPasswordHttpAuthenticator auth = new UserPasswordHttpAuthenticator("jolokia","roland","s!cr!t");
        assertTrue(auth.checkCredentials("roland","s!cr!t"));
        assertFalse(auth.checkCredentials("roland", "bla"));
        assertFalse(auth.checkCredentials(null, "bla"));
        assertFalse(auth.checkCredentials("roland", null));
        assertFalse(auth.checkCredentials(null, null));

    }
}