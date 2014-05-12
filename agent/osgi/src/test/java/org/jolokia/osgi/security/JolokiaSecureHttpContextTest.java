package org.jolokia.osgi.security;

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

import java.io.IOException;

import javax.security.auth.Subject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jolokia.config.ConfigKey;
import org.jolokia.http.AgentServlet;
import org.osgi.service.http.HttpContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertTrue;

public class JolokiaSecureHttpContextTest extends BasicAuthenticationHttpContextTest {

    public static final String ROLE_NAME_ADMIN = "admin";

    @Override
    @BeforeMethod
    public void setup() {
        request = createMock(HttpServletRequest.class);
        response = createMock(HttpServletResponse.class);
        String realm = ConfigKey.REALM.getDefaultValue();
        context = new BasicAuthenticationHttpContext(realm, new JaasAuthenticator(realm));
    }

    @Test(enabled = false)
    public void correctAuth() throws IOException {
        expect(request.getHeader("Authorization")).andReturn("basic cm9sYW5kOnMhY3IhdA==");
        request.setAttribute(HttpContext.AUTHENTICATION_TYPE, HttpServletRequest.BASIC_AUTH);
        request.setAttribute(HttpContext.REMOTE_USER, "roland");
        request.setAttribute(eq(AgentServlet.JAAS_SUBJECT_REQUEST_ATTRIBUTE), isA(Subject.class));
        replay(request, response);

        assertTrue(context.handleSecurity(request,response));
    }
}