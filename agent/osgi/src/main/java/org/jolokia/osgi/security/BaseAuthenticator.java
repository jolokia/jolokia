package org.jolokia.osgi.security;

import javax.servlet.http.HttpServletRequest;
import org.jolokia.util.AuthorizationHeaderParser;

/**
 * Interface used for performing the authentication.
 *
 * @author roland
 * @since 26.05.14
 */
public abstract class BaseAuthenticator implements Authenticator {

    /**
     * Authenticate the given request
     * @param pRequest request to examine
     * @return true if authentication passes, false otherwise
     */
    public boolean authenticate(HttpServletRequest pRequest) {
        String auth = pRequest.getHeader("Authorization");
        if(auth==null){
            //For cases where middleware may strip credentials
            auth=pRequest.getHeader(AuthorizationHeaderParser.JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER);
        }
        if (auth == null) {
            return false;
        }
        AuthorizationHeaderParser.Result authInfo = AuthorizationHeaderParser.parse(auth);
        return authInfo.isValid() && doAuthenticate(pRequest, authInfo);
    }

    /**
     * Overriden by concrete implementations for doing the real authentication
     *
     * @param pRequest request which can be used to store additional authentication information
     * @param pAuthInfo authentication information provided by the user
     * @return true if authentication is ok, false otherwise
     */
    abstract protected boolean doAuthenticate(HttpServletRequest pRequest, AuthorizationHeaderParser.Result pAuthInfo);
}
