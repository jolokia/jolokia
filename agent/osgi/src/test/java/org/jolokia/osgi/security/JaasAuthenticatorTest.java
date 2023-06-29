package org.jolokia.osgi.security;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletRequest;

import org.easymock.EasyMock;
import org.jolokia.config.ConfigKey;
import org.jolokia.util.AuthorizationHeaderParser;
import org.osgi.service.http.HttpContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.mock;
import static org.easymock.EasyMock.replay;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class JaasAuthenticatorTest {

    private static final Subject SUBJECT = new Subject();

    private AuthorizationHeaderParser.Result info;

    @BeforeMethod
    public void setUp() throws Exception {
        info = AuthorizationHeaderParser.parse("Basic cm9sYW5kOnMhY3IhdA==");
    }

    @Test
    public void testAuthenticateNoLoginModule() throws Exception {
        HttpServletRequest req = createMock(HttpServletRequest.class);
        JaasAuthenticator auth = new JaasAuthenticator("jolokia");

        replay(req);

        // requires ~/.java.login.config file on JDK 6
        assertFalse(auth.doAuthenticate(req, info));
    }


    @Test
    public void testAuthenticationPositive() throws Exception {
        HttpServletRequest req = prepareRequest();
        JaasAuthenticator auth = new JaasAuthenticator("jolokia") {
            @Override
            protected LoginContext createLoginContext(String realm, CallbackHandler handler) throws LoginException {
                LoginContext mockLogin = mock(LoginContext.class);
                mockLogin.login();
                expect(mockLogin.getSubject()).andReturn(SUBJECT);
                replay(mockLogin);
                return mockLogin;
            }
        };
        assertTrue(auth.doAuthenticate(req, info));
    }

    @Test
    public void testAuthenticationNegative() throws Exception {
        HttpServletRequest req = prepareRequest();

        JaasAuthenticator auth = new JaasAuthenticator("jolokia") {
            @Override
            protected LoginContext createLoginContext(String realm, CallbackHandler handler) throws LoginException {
                LoginContext mockLogin = mock(LoginContext.class);
                mockLogin.login();
                EasyMock.expectLastCall().andThrow(new LoginException("Failed"));
                replay(mockLogin);
                return mockLogin;
            }
        };
        assertFalse(auth.doAuthenticate(req, info));
    }

    private HttpServletRequest prepareRequest() {
        HttpServletRequest req = createMock(HttpServletRequest.class);
        req.setAttribute(HttpContext.AUTHENTICATION_TYPE, HttpServletRequest.BASIC_AUTH);
        req.setAttribute(HttpContext.REMOTE_USER, "roland");
        req.setAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE, SUBJECT);
        replay(req);
        return req;
    }


}
