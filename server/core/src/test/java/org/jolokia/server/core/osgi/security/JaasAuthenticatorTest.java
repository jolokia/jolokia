package org.jolokia.server.core.osgi.security;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import jakarta.servlet.http.HttpServletRequest;

import org.easymock.EasyMock;
import org.jolokia.server.core.config.ConfigKey;
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
    public void testAuthenticateNoLoginModule() {
        HttpServletRequest req = createMock(HttpServletRequest.class);
        JaasAuthenticator auth = new JaasAuthenticator("jolokia");

        replay(req);

        // requires ~/.java.login.config file on JDK 11+
        assertFalse(auth.doAuthenticate(req, info));
    }


    @Test
    public void testAuthenticationPositive() {
        HttpServletRequest req = prepareRequest();
        JaasAuthenticator auth = new JaasAuthenticator("jolokia") {
            @Override
            protected LoginContext createLoginContext(String realm, CallbackHandler handler) throws LoginException {
                // can't mock original LoginContext because sun.invoke.util.VerifyAccess.isTypeVisible()
                // checks classloaders - we need AppClassLoader and LoginContext.class has null classloader
                LoginContext mockLogin = mock(TestLoginContext.class);
                mockLogin.login();
                expect(mockLogin.getSubject()).andReturn(SUBJECT);
                replay(mockLogin);
                return mockLogin;
            }
        };
        assertTrue(auth.doAuthenticate(req, info));
    }

    @Test
    public void testAuthenticationNegative() {
        HttpServletRequest req = prepareRequest();

        JaasAuthenticator auth = new JaasAuthenticator("jolokia") {
            @Override
            protected LoginContext createLoginContext(String realm, CallbackHandler handler) throws LoginException {
                // can't mock original LoginContext because sun.invoke.util.VerifyAccess.isTypeVisible()
                // checks classloaders - we need AppClassLoader and LoginContext.class has null classloader
                LoginContext mockLogin = mock(TestLoginContext.class);
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
        req.setAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE, SUBJECT);
        replay(req);
        return req;
    }

    public static class TestLoginContext extends LoginContext {
        public TestLoginContext(String name) throws LoginException {
            super(name);
        }

        public TestLoginContext(String name, Subject subject) throws LoginException {
            super(name, subject);
        }

        public TestLoginContext(String name, CallbackHandler callbackHandler) throws LoginException {
            super(name, callbackHandler);
        }

        public TestLoginContext(String name, Subject subject, CallbackHandler callbackHandler) throws LoginException {
            super(name, subject, callbackHandler);
        }

        public TestLoginContext(String name, Subject subject, CallbackHandler callbackHandler, Configuration config) throws LoginException {
            super(name, subject, callbackHandler, config);
        }
    }

}
