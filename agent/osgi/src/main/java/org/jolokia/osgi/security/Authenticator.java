package org.jolokia.osgi.security;

import javax.servlet.http.HttpServletRequest;

/**
 * Interface used for authentication. Can be implemented externally, too.
 *
 * @author roland
 * @since 07.02.18
 */
public interface Authenticator {

    /**
     * Authenticate the given request
     * @param pRequest request to examine
     * @return true if authentication passes, false otherwise
     */
    boolean authenticate(HttpServletRequest pRequest);
}
