package org.jolokia.jvmagent;

import com.sun.net.httpserver.BasicAuthenticator;

/**
 * Simple authenticator using user and password for basic authentication.
 *
 * @author roland
 * @since 07.06.13
*/
class UserPasswordAuthenticator extends BasicAuthenticator {
    private String user;
    private String password;

    /**
     * Authenticator which checks agains a given user and password
     *
     * @param pUser user to check again
     * @param pPassword her password
     */
    UserPasswordAuthenticator(String pUser, String pPassword) {
        super("jolokia");
        user = pUser;
        password = pPassword;
    }

    /** {@inheritDoc} */
    public boolean checkCredentials(String pUserGiven, String pPasswordGiven) {
        return user.equals(pUserGiven) && password.equals(pPasswordGiven);
    }
}
