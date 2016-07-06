package org.jolokia.jvmagent.security;

import java.lang.reflect.Field;

import javax.security.auth.Subject;

import com.sun.net.httpserver.*;
import org.jolokia.test.util.MockLoginContext;
import org.testng.annotations.*;

import static org.testng.Assert.*;

public class JaasAuthenticatorTest extends BaseAuthenticatorTest {

    private JaasAuthenticator auth;

    @BeforeMethod
    public void setUp() throws Exception {
        auth = new JaasAuthenticator("jolokia");
    }

    @AfterMethod
    public void checkThatThreadLocalIsRemoved() throws NoSuchFieldException, IllegalAccessException {
        Field field = auth.getClass().getDeclaredField("subjectThreadLocal");
        field.setAccessible(true);
        ThreadLocal<Subject> tl = (ThreadLocal<Subject>) field.get(auth);
        assertNull(tl.get());
        field.setAccessible(false);
    }

    @Test
    public void testAuthenticateNoAuthorizationHeader() throws Exception {
        Headers respHeader = new Headers();
        HttpExchange ex = createHttpExchange(respHeader);

        Authenticator.Result res = auth.authenticate(ex);

        assertEquals(((Authenticator.Retry) res).getResponseCode(),401);
        assertTrue(respHeader.containsKey("WWW-Authenticate"));
        assertTrue(respHeader.getFirst("WWW-Authenticate").contains("jolokia"));
    }

    @Test
    public void testAuthenticateNoLoginModules() throws Exception {
            Headers respHeader = new Headers();
            HttpExchange ex = createHttpExchange(respHeader, "Authorization", "Basic cm9sYW5kOnMhY3IhdA==");

            Authenticator.Result result = auth.authenticate(ex);
            assertEquals(((Authenticator.Failure) result).getResponseCode(), 401);
    }

    @Test
    public void testAuthenticateSuccess() throws Exception {
        Headers respHeader = new Headers();
        HttpExchange ex = createHttpExchange(respHeader, MockLoginContext.SUBJECT, "Authorization", "Basic cm9sYW5kOnMhY3IhdA==");

        new MockLoginContext("jolokia",true);

        Authenticator.Result result = auth.authenticate(ex);
        HttpPrincipal principal = ((Authenticator.Success) result).getPrincipal();
        assertEquals(principal.getRealm(),"jolokia");
        assertEquals(principal.getUsername(),"roland");
    }

}
