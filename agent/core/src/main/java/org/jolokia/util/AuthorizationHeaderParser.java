package org.jolokia.util;

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

import java.util.StringTokenizer;

import jakarta.servlet.http.HttpServletRequest;

import org.jolokia.util.Base64Util;

public final class AuthorizationHeaderParser {

    public static final String JOLOKIA_ALTERNATE_AUTHORIZATION_HEADER="X-jolokia-authorization";

    private AuthorizationHeaderParser() { }

    /**
     * Parse the HTTP authorization header
     *
     * @param pAuthInfo header to parse
     * @return method, user, password and whehter the header was valid
     */
    public static Result parse(String pAuthInfo) {
        StringTokenizer stok = new StringTokenizer(pAuthInfo);
        String method = stok.nextToken();
        if (!HttpServletRequest.BASIC_AUTH.equalsIgnoreCase(method)) {
            throw new IllegalArgumentException("Only BasicAuthentication is supported");
        }

        String b64Auth = stok.nextToken();
        String auth = new String(Base64Util.decode(b64Auth));

        int p = auth.indexOf(':');
        String user;
        String password;
        boolean valid;
        if (p != -1) {
            user = auth.substring(0, p);
            password = auth.substring(p+1);
            valid = true;
        } else {
            valid = false;
            user = null;
            password = null;
        }
        return new Result(method,user,password,valid);
    }

        // ============================================================================================================

    public static class Result {
        private final String method;
        private final String user;
        private final String password;
        private final boolean valid;

        public Result(String pMethod, String pUser, String pPassword, boolean pValid) {
            method = pMethod;
            user = pUser;
            password = pPassword;
            valid = pValid;
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
