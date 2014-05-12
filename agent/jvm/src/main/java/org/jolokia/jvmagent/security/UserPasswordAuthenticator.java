package org.jolokia.jvmagent.security;

import com.sun.net.httpserver.BasicAuthenticator;

/**
 * Simple authenticator using user and password for basic authentication.
 *
 * @author roland
 * @since 07.06.13
*/
public class UserPasswordAuthenticator extends BasicAuthenticator {
    private String user;
    private String password;

    /**
     * Authenticator which checks against a given user and password
     *
     * @param pRealm realm for this authentication
     * @param pUser user to check again
     * @param pPassword her password
     */
    public UserPasswordAuthenticator(String pRealm, String pUser, String pPassword) {
        super(pRealm);
        user = pUser;
        password = pPassword;
    }

    /** {@inheritDoc} */
    public boolean checkCredentials(String pUserGiven, String pPasswordGiven) {
        return user.equals(pUserGiven) && password.equals(pPasswordGiven);
    }
}
