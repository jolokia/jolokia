package org.jolokia.osgi.security;

import javax.servlet.http.HttpServletRequest;

import org.jolokia.config.ConfigKey;
import org.jolokia.test.util.MockLoginContext;
import org.osgi.service.http.HttpContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

public class JaasAuthenticatorTest {

    private JaasAuthenticator auth;
    private AuthorizationHeaderParser.Result info;

    @BeforeMethod
    public void setUp() throws Exception {
        auth = new JaasAuthenticator("jolokia");
        info = AuthorizationHeaderParser.parse("Basic cm9sYW5kOnMhY3IhdA==");
    }

    @Test
    public void testAuthenticateNoLoginModule() throws Exception {
        HttpServletRequest req = createMock(HttpServletRequest.class);

        replay(req);

        assertFalse(auth.doAuthenticate(req, info));
    }


    @Test
    public void testAuthenticationPositive() throws Exception {
        HttpServletRequest req = prepareRequest();

        new MockLoginContext("jolokia",true);
        assertTrue(auth.doAuthenticate(req, info));
    }

    @Test
    public void testAuthenticationNegative() throws Exception {
        HttpServletRequest req = prepareRequest();

        new MockLoginContext("jolokia",false);
        assertFalse(auth.doAuthenticate(req, info));
    }

    private HttpServletRequest prepareRequest() {
        HttpServletRequest req = createMock(HttpServletRequest.class);
        req.setAttribute(HttpContext.AUTHENTICATION_TYPE, HttpServletRequest.BASIC_AUTH);
        req.setAttribute(HttpContext.REMOTE_USER, "roland");
        req.setAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE, MockLoginContext.SUBJECT);
        replay(req);
        return req;
    }


}