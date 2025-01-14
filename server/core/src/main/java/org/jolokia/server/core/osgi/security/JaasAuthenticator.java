package org.jolokia.server.core.osgi.security;

import javax.security.auth.callback.*;
import javax.security.auth.login.*;
import jakarta.servlet.http.HttpServletRequest;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.http.security.AuthorizationHeaderParser;
import org.jolokia.server.core.util.UserPasswordCallbackHandler;

/**
 * @author roland
 * @since 26.05.14
 */
public class JaasAuthenticator extends BaseAuthenticator {

    private final String realm;

    public JaasAuthenticator(String pRealm) {
        realm = pRealm;
    }

    @Override
    protected boolean doAuthenticate(HttpServletRequest pRequest, AuthorizationHeaderParser.Result pAuthInfo) {
        try {
            String user = pAuthInfo.getUser();
            String password = pAuthInfo.getPassword();

            final CallbackHandler handler = new UserPasswordCallbackHandler(user, password);
            LoginContext loginContext = createLoginContext(realm, handler);
            loginContext.login();

            pRequest.setAttribute(ConfigKey.JAAS_SUBJECT_REQUEST_ATTRIBUTE, loginContext.getSubject());

            return true;
        } catch (LoginException e) {
            return false;
        }
    }

    protected LoginContext createLoginContext(String realm, CallbackHandler handler) throws LoginException {
        return new LoginContext(realm, handler);
    }

}
