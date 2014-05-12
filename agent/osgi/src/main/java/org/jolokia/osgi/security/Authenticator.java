package org.jolokia.osgi.security;

import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;

/**
 * @author roland
 * @since 26.05.14
 */
public abstract class Authenticator {

    boolean authenticate(HttpServletRequest pRequest) {
        String auth = pRequest.getHeader("Authorization");
        if (auth == null) {
            return false;
        }
        AuthInfo authInfo = new AuthInfo(auth);
        return authInfo.isValid() && doAuthenticate(pRequest, authInfo);
    }

    protected abstract boolean doAuthenticate(HttpServletRequest pRequest, AuthInfo pAuthInfo);

    protected class AuthInfo {
        private final String method;
        private final String user;
        private final String password;
        private final boolean valid;

        protected AuthInfo(String pAuthInfo) {
            StringTokenizer stok = new StringTokenizer(pAuthInfo);
            method = stok.nextToken();
            if (!HttpServletRequest.BASIC_AUTH.equalsIgnoreCase(method)) {
                throw new IllegalArgumentException("Only BasicAuthentication is supported");
            }

            String b64Auth = stok.nextToken();
            String auth = new String(Base64.decode(b64Auth));

            int p = auth.indexOf(':');
            if (p != -1) {
                user = auth.substring(0, p);
                password = auth.substring(p+1);
                valid = true;
            } else {
                valid = false;
                user = null;
                password = null;
            }
        }

        public String getMethod() {
            return method;
        }

        public String getUser() {
            return user;
        }

        public String getPassword() {
            return password;
        }

        public boolean isValid() {
            return valid;
        }
    }
}
