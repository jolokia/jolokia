package org.jolokia.osgi;

/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.http.HttpContext;

/**
 * Authentication context which uses a simple user/password credential pair
 *
 * @author roland
 * @since Jan 7, 2010
 */
class JolokiaAuthenticatedHttpContext extends JolokiaHttpContext {
    private final String user;
    private final String password;
    private final String realm;

    /**
     * Constructor
     *
     * @param pUser user to check against
     * @param pPassword password to check against
     * @param pRealm realm to authenticate against
     */
    JolokiaAuthenticatedHttpContext(String pUser, String pPassword,String pRealm) {
        user = pUser;
        password = pPassword;
        realm=pRealm;
    }

    /** {@inheritDoc} */
    @Override
    public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String auth = request.getHeader(HEADER_AUTHORIZATION);
        if (auth == null || !verifyAuthentication(auth, user, password)) {
            response.setHeader(HEADER_WWW_AUTHENTICATE,HttpServletRequest.BASIC_AUTH + " realm=\""+realm+"\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        } else {
            request.setAttribute(HttpContext.AUTHENTICATION_TYPE,HttpServletRequest.BASIC_AUTH);
            request.setAttribute(HttpContext.REMOTE_USER, user);
            return true;
        }
    }

    private boolean verifyAuthentication(String pAuth,String pUser, String pPassword) {
        StringTokenizer stok = new StringTokenizer(pAuth);
        String method = stok.nextToken();
        if (!HttpServletRequest.BASIC_AUTH.equalsIgnoreCase(method)) {
            throw new IllegalArgumentException("Only BasicAuthentication is supported");
        }
        String b64Auth = stok.nextToken();
        String auth = new String(Base64.decode(b64Auth));

        int p = auth.indexOf(':');
        if (p != -1) {
            String name = auth.substring(0, p);
            String pwd = auth.substring(p+1);

            return name.trim().equals(pUser) &&
                    pwd.trim().equals(pPassword);
        } else {
            return false;
        }
    }

    public String getRealm() {
        return realm;
    }

}
