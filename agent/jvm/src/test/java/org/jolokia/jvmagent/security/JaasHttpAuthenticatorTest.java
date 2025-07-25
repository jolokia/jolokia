package org.jolokia.jvmagent.security;

import java.lang.reflect.Field;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.easymock.EasyMock;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.expect;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

public class JaasHttpAuthenticatorTest extends BaseAuthenticatorTest {

    private JaasHttpAuthenticator auth;

    @BeforeMethod
    public void setUp() {
        auth = new JaasHttpAuthenticator("jolokia", null);
    }

    @AfterMethod
    public void checkThatThreadLocalIsRemoved() throws NoSuchFieldException, IllegalAccessException {
        Field field = auth.getClass().getDeclaredField("subjectThreadLocal");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ThreadLocal<Subject> tl = (ThreadLocal<Subject>) field.get(auth);
        assertNull(tl.get());
        field.setAccessible(false);
    }

    @Test
    public void testAuthenticateNoAuthorizationHeader() {
        Headers respHeader = new Headers();
        HttpExchange ex = createHttpExchange(respHeader);

        Authenticator.Result res = auth.authenticate(ex);

        assertEquals(((Authenticator.Retry) res).getResponseCode(),401);
        assertTrue(respHeader.containsKey("WWW-Authenticate"));
        assertTrue(respHeader.getFirst("WWW-Authenticate").contains("jolokia"));
    }

    @Test
    public void testAuthenticateNoLoginModules() {
            Headers respHeader = new Headers();
            HttpExchange ex = createHttpExchange(respHeader, "Authorization", "Basic cm9sYW5kOnMhY3IhdA==");

            Authenticator.Result result = auth.authenticate(ex);
            assertEquals(((Authenticator.Failure) result).getResponseCode(), 401);
    }

    @Test
    public void testAuthenticateSuccess() {
        Headers respHeader = new Headers();
        final Subject subject = new Subject();
        HttpExchange ex = createHttpExchange(respHeader, subject, "Authorization", "Basic cm9sYW5kOnMhY3IhdA==");

        JaasHttpAuthenticator successAuth = new JaasHttpAuthenticator("jolokia", null) {
            @Override
            protected LoginContext createLoginContext(String realm, CallbackHandler handler) throws LoginException {
                LoginContext mockLogin = EasyMock.mock(MockableLoginContext.class);
                mockLogin.login();
                expect(mockLogin.getSubject()).andReturn(subject);
                EasyMock.replay(mockLogin);
                return mockLogin;
            }
        };

        Authenticator.Result result = successAuth.authenticate(ex);
        HttpPrincipal principal = ((Authenticator.Success) result).getPrincipal();
        assertEquals(principal.getRealm(),"jolokia");
        assertEquals(principal.getUsername(),"roland");
    }

    public static class MockableLoginContext extends LoginContext {
        public MockableLoginContext(String name) throws LoginException {
            super(name);
        }

        public MockableLoginContext(String name, Subject subject) throws LoginException {
            super(name, subject);
        }

        public MockableLoginContext(String name, CallbackHandler callbackHandler) throws LoginException {
            super(name, callbackHandler);
        }

        public MockableLoginContext(String name, Subject subject, CallbackHandler callbackHandler) throws LoginException {
            super(name, subject, callbackHandler);
        }

        public MockableLoginContext(String name, Subject subject, CallbackHandler callbackHandler, Configuration config) throws LoginException {
            super(name, subject, callbackHandler, config);
        }
    }

}
