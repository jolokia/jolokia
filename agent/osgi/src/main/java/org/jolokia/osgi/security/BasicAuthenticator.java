package org.jolokia.osgi.security;

import javax.servlet.http.HttpServletRequest;

import org.jolokia.util.AuthorizationHeaderParser;
import org.osgi.service.http.HttpContext;

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
        if (providedUser != null && providedUser.trim().equals(userToCheck) &&
                providedPassword != null && providedPassword.trim().equals(passwordToCheck)) {

            pRequest.setAttribute(HttpContext.AUTHENTICATION_TYPE,HttpServletRequest.BASIC_AUTH);
            pRequest.setAttribute(HttpContext.REMOTE_USER, userToCheck);
            return true;
        } else {
            return false;
        }
    }
}
