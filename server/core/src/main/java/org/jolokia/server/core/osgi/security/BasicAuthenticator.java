package org.jolokia.server.core.osgi.security;

import jakarta.servlet.http.HttpServletRequest;
import org.jolokia.server.core.http.security.AuthorizationHeaderParser;

/**
* @author roland
* @since 26.05.14
*/

public class BasicAuthenticator extends BaseAuthenticator {

    private final String userToCheck;
    private final String passwordToCheck;

    public BasicAuthenticator(String pUser, String pPassword) {
        userToCheck = pUser;
        passwordToCheck = pPassword;
    }

    @Override
    protected boolean doAuthenticate(HttpServletRequest pRequest, AuthorizationHeaderParser.Result pAuthInfo) {
        String providedUser = pAuthInfo.getUser();
        String providedPassword = pAuthInfo.getPassword();
        return providedUser != null && providedUser.trim().equals(userToCheck) &&
                providedPassword != null && providedPassword.trim().equals(passwordToCheck);
    }
}
