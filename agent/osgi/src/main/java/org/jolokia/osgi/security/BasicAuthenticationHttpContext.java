package org.jolokia.osgi.security;

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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Authentication context which uses a simple user/password credential pair
 *
 * @author roland
 * @since Jan 7, 2010
 */
public class BasicAuthenticationHttpContext extends DefaultHttpContext {

    private final String realm;
    private final Authenticator authenticator;

    /**
     * Constructor
     *
     * @param pRealm realm to authenticate against
     */
    public BasicAuthenticationHttpContext(String pRealm, Authenticator pAuthenticator) {
        realm = pRealm;
        authenticator = pAuthenticator;
    }

    /** {@inheritDoc} */
    public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!authenticator.authenticate(request)) {
            response.setHeader("WWW-Authenticate",HttpServletRequest.BASIC_AUTH + " realm=\""+realm+"\"");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        } else {
            return true;
        }
    }
}
