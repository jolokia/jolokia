package org.jolokia.osgi;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import org.apache.karaf.jaas.boot.principal.RolePrincipal;
import org.jolokia.config.ConfigKey;
import org.jolokia.util.LogHandler;
import org.osgi.service.http.HttpContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.security.Principal;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.AssertJUnit.assertFalse;

public class JolokiaSecureHttpContextTest extends  JolokiaAuthenticatedHttpContextTest {

    public static final String ROLE_NAME_ADMIN = "admin";

    SimpleLoginContextFactory slcf;

    @Override
    @BeforeMethod
    public void setup() {
        request = createMock(HttpServletRequest.class);
        response = createMock(HttpServletResponse.class);
        slcf = new SimpleLoginContextFactory(ROLE_NAME_ADMIN);
        context = new JolokiaSecureHttpContext(ConfigKey.REALM.getDefaultValue(), ROLE_NAME_ADMIN,slcf, new LogHandler.StdoutLogHandler(true));
    }

    private static class SimpleLoginContextFactory implements  LoginContextFactory {

        private String role;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public SimpleLoginContextFactory(String role) {
            this.role=role;
        }

        public LoginContext createLoginContext(String name, Subject subject, CallbackHandler callbackHandler) throws LoginException {

            Principal p = new RolePrincipal(role);
            subject.getPrincipals().add(p);
            return createMock(LoginContext.class);
        }

    }

    @Test
    public void correctAuth() throws IOException {
        expect(request.getHeader("Authorization")).andReturn("basic cm9sYW5kOnMhY3IhdA==");
        request.setAttribute(HttpContext.AUTHENTICATION_TYPE, HttpServletRequest.BASIC_AUTH);
        request.setAttribute(HttpContext.REMOTE_USER, "roland");
        request.setAttribute(eq(JolokiaHttpContext.REQUEST_SUBJECT), isA(Subject.class));
        replay(request, response);

        assertTrue(context.handleSecurity(request,response));
    }

    @Test
    public void incorrectRole() throws IOException {
        slcf.setRole("root");
        expect(request.getHeader("Authorization")).andReturn("basic cm9sYW5kOnMhY3IhdA==");
        request.setAttribute(HttpContext.AUTHENTICATION_TYPE, HttpServletRequest.BASIC_AUTH);
        //request.setAttribute(HttpContext.REMOTE_USER, "roland");
        //request.setAttribute(eq(JolokiaHttpContext.REQUEST_SUBJECT), isA(Subject.class));
        response.setHeader(JolokiaHttpContext.HEADER_WWW_AUTHENTICATE, HttpServletRequest.BASIC_AUTH + " realm=\"" + ConfigKey.REALM.getDefaultValue() + "\"");
        //response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);

        replay(request, response);

        assertFalse(context.handleSecurity(request, response));
    }

    @Test
    public void testInstance() {

        JolokiaSecureHttpContext jshc = (JolokiaSecureHttpContext) context;

        System.out.println(jshc.toString());
        assertEquals(ROLE_NAME_ADMIN,jshc.getRole());
        assertEquals(ConfigKey.REALM.getDefaultValue(),jshc.getRealm());

    }
}