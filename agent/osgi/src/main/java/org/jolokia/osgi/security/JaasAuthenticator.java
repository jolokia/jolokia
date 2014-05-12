package org.jolokia.osgi.security;

import java.io.IOException;

import javax.security.auth.callback.*;
import javax.security.auth.login.*;
import javax.servlet.http.HttpServletRequest;

import org.jolokia.http.AgentServlet;
import org.osgi.service.http.HttpContext;

/**
 * @author roland
 * @since 26.05.14
 */
public class JaasAuthenticator extends Authenticator {

    private final String realm;

    public JaasAuthenticator(String pRealm) {
        realm = pRealm;
    }

    @Override
    protected boolean doAuthenticate(HttpServletRequest pRequest, AuthInfo pAuthInfo) {
        try {
            String user = pAuthInfo.getUser();
            String password = pAuthInfo.getPassword();

            final CallbackHandler handler = new UserPasswordCallbackHandler(user, password);
            LoginContext loginContext = new LoginContext(realm, handler);
            loginContext.login();

            pRequest.setAttribute(HttpContext.AUTHENTICATION_TYPE,HttpServletRequest.BASIC_AUTH);
            pRequest.setAttribute(HttpContext.REMOTE_USER, user);
            pRequest.setAttribute(AgentServlet.JAAS_SUBJECT_REQUEST_ATTRIBUTE,loginContext.getSubject());

            return true;
        } catch (AccountException e) {
            return false;
        } catch (LoginException e) {
            return false;
        }
    }

    static final class UserPasswordCallbackHandler implements CallbackHandler {
        private final String username;
        private final String password;

        private UserPasswordCallbackHandler(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback) {
                    ((NameCallback) callback).setName(username);
                } else if (callback instanceof PasswordCallback) {
                    ((PasswordCallback) callback).setPassword(password.toCharArray());
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }

    }
}
