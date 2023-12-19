package org.jolokia.jvmagent.security;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpExchange;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.util.UserPasswordCallbackHandler;

/**
 * Authenticator using JAAS for logging in with user and password for the given realm.
 *
 * @author roland
 * @since 26.05.14
 */
public class JaasHttpAuthenticator extends BasicAuthenticator {

    // Used for communicating back the subject obtained.
    private final ThreadLocal<Subject> subjectThreadLocal = new ThreadLocal<>();

    public JaasHttpAuthenticator(String pRealm) {
        super(pRealm);
    }

    @Override
    public Result authenticate(HttpExchange pHttpExchange) {
        try {
            Result result = super.authenticate(pHttpExchange);
            if (result instanceof Success) {
                Subject subject = subjectThreadLocal.get();
                if (subject != null) {
                    pHttpExchange.setAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE, subject);
                }
            }
            return result;
        } finally {
            subjectThreadLocal.remove();
        }
    }

    @Override
    public boolean checkCredentials(String pUser, String pPassword) {
        try {
            final CallbackHandler handler = new UserPasswordCallbackHandler(pUser, pPassword);
            LoginContext loginContext = createLoginContext(realm, handler);
            loginContext.login();
            subjectThreadLocal.set(loginContext.getSubject());
            return true;
        } catch (LoginException e) {
            return false;
        }
    }

    protected LoginContext createLoginContext(String realm, CallbackHandler handler) throws LoginException {
        return new LoginContext(realm, handler);
    }
}
