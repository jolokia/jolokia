package org.jolokia.osgi;
/**
 *  Copyright 2005-2014 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */

import org.jolokia.util.LogHandler;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.http.HttpContext;

import javax.security.auth.Subject;
import javax.security.auth.callback.*;
import javax.security.auth.login.AccountException;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Principal;

public class JolokiaSecureHttpContext extends JolokiaHttpContext {

    //private static final transient Logger logHandler = Logger.getLogger(JolokiaSecureHttpContext.class.getName());
    public static final String ORG_APACHE_KARAF_JAAS_BOOT_PRINCIPAL_ROLE_PRINCIPAL = "org.apache.karaf.jaas.boot.principal.RolePrincipal";

    private LogHandler logHandler;

    private LoginContextFactory loginContextFactory;

    private final String realm;
    private final String role;

    /**
     * Constructor
     */
    public JolokiaSecureHttpContext(String realm, String role,LoginContextFactory loginContextFactory,LogHandler logHandler) {
        this.realm = realm;
        this.role = role;
        this.loginContextFactory=loginContextFactory;
        this.logHandler=logHandler;
    }

    public boolean handleSecurity(HttpServletRequest request, HttpServletResponse response) {
        return authenticate(request, response);
    }

    public Subject doAuthenticate(final String username, final String password) {
        try {
            Subject subject = new Subject();
            final CallbackHandler handler = new AuthenticationCallbackHandler(username, password);

            LoginContext loginContext = loginContextFactory.createLoginContext(realm, subject,handler);

            loginContext.login();
            if (role != null && role.length() > 0) {
                String clazz = ORG_APACHE_KARAF_JAAS_BOOT_PRINCIPAL_ROLE_PRINCIPAL;
                String name = role;
                int idx = role.indexOf(':');
                if (idx > 0) {
                    clazz = role.substring(0, idx);
                    name = role.substring(idx + 1);
                }
                boolean found = false;
                for (Principal p : subject.getPrincipals()) {
                    if (p.getClass().getName().equals(clazz)
                            && p.getName().equals(name)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new FailedLoginException("User does not have the required role " + role);
                }
            }
            return subject;
        } catch (AccountException e) {
            logHandler.debug("Account failure "+e.getMessage());
            return null;
        } catch (LoginException e) {
            logHandler.debug("Account failure "+ e.getMessage());
            return null;
        } catch (GeneralSecurityException e) {
            logHandler.debug("Account failure " + e.getMessage());
            return null;
        }
    }

    protected static final class AuthenticationCallbackHandler implements CallbackHandler {

        private final String username;
        private final String password;

        private AuthenticationCallbackHandler(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (int i = 0; i < callbacks.length; i++) {
                if (callbacks[i] instanceof NameCallback) {
                    ((NameCallback) callbacks[i]).setName(username);
                } else if (callbacks[i] instanceof PasswordCallback) {
                    ((PasswordCallback) callbacks[i]).setPassword(password.toCharArray());
                } else {
                    throw new UnsupportedCallbackException(callbacks[i]);
                }
            }
        }

    }
    //TODO: We might want to clean this up a bit.
    public boolean authenticate(HttpServletRequest request, HttpServletResponse response) {

        // Return immediately if the header is missing
        String authHeader = request.getHeader(HEADER_AUTHORIZATION);
        if (authHeader != null && authHeader.length() > 0) {

            // Get the authType (Basic, Digest) and authInfo (user/password)
            // from the header
            authHeader = authHeader.trim();
            int blank = authHeader.indexOf(' ');
            if (blank > 0) {
                String authType = authHeader.substring(0, blank);
                String authInfo = authHeader.substring(blank).trim();

                // Check whether authorization type matches
                if (authType.equalsIgnoreCase(HttpServletRequest.BASIC_AUTH)) {
                    try {
                        String srcString = base64Decode(authInfo);
                        int i = srcString.indexOf(':');
                        if (i != -1) {
                            String username = srcString.substring(0, i);
                            String password = srcString.substring(i + 1);

                            // authenticate
                            Subject subject = doAuthenticate(username, password);
                            if (subject != null) {
                                // as per the spec, set attributes
                                request.setAttribute(HttpContext.AUTHENTICATION_TYPE, HttpServletRequest.BASIC_AUTH);
                                request.setAttribute(HttpContext.REMOTE_USER, username);
                                request.setAttribute(REQUEST_SUBJECT,subject);
                                // succeed
                                return true;
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Error here");
                        e.printStackTrace();
                        // Ignore
                    }
                } else {
                    throw new IllegalArgumentException("Only BasicAuthentication is supported");
                }
            }
        }

        // request authentication
        try {
            response.setHeader(HEADER_WWW_AUTHENTICATE, HttpServletRequest.BASIC_AUTH + " realm=\"" + this.realm + "\"");
            //response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            // failed sending the response ... cannot do anything about it
        }

        // inform HttpService that authentication failed
        return false;
    }


    private String base64Decode(String srcString) {
        return new String(Base64.decode(srcString));
    }

    public String getRealm() {
        return realm;
    }

    public String getRole() {
        return role;
    }

    public String toString() {
        Bundle bundle = FrameworkUtil.getBundle(getClass());
        if (null != bundle) {
            return getClass().getSimpleName() + "{" + bundle.getSymbolicName() + " - " + bundle.getBundleId() + "}";
        } else {
            return super.toString();
        }
    }
}