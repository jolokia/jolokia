package org.jolokia.util;

import java.io.IOException;

import javax.security.auth.callback.*;

/**
 * JAAS Callback handler setting user and password programtically.
 *
* @author roland
* @since 26.05.14
*/
public final class UserPasswordCallbackHandler implements CallbackHandler {
    private final String username;
    private final String password;

    /**
     * Callback handler for the given user and password
     * @param username
     * @param password
     */
    public UserPasswordCallbackHandler(String username, String password) {
        this.username = username;
        this.password = password;
    }

    /** {@inheritDoc} */
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
