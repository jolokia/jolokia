package org.jolokia.server.core.osgi;

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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.easymock.EasyMock;
import org.osgi.service.http.HttpContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 13.08.11
 */
public class JolokiaAuthenticatedHttpContextTest {

    private HttpServletRequest request;
    private HttpServletResponse response;

    JolokiaAuthenticatedHttpContext context;

    @BeforeMethod
    public void setup() {
        request = createMock(HttpServletRequest.class);
        response = createMock(HttpServletResponse.class);
        context = new JolokiaAuthenticatedHttpContext("roland","s!cr!t");
    }

    @Test
    public void correctAuth() throws IOException {
        expect(request.getHeader("Authorization")).andReturn("basic cm9sYW5kOnMhY3IhdA==");
        request.setAttribute(HttpContext.AUTHENTICATION_TYPE,"Basic");
        request.setAttribute(HttpContext.REMOTE_USER, "roland");
        replay(request,response);

        assertTrue(context.handleSecurity(request,response));
    }

    @Test
    public void noAuth() throws IOException {

        expect(request.getHeader("Authorization")).andReturn(null);
        response.setHeader(eq("WWW-Authenticate"), EasyMock.<String>anyObject());
        response.sendError(401);
        replay(request, response);

        assertFalse(context.handleSecurity(request, response));
    }

    @Test
    public void wrongAuth() throws IOException {

        expect(request.getHeader("Authorization")).andReturn("basic Blub");
        response.setHeader(eq("WWW-Authenticate"), EasyMock.<String>anyObject());
        response.sendError(401);
        replay(request, response);

        assertFalse(context.handleSecurity(request,response));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*BasicAuthentication.*")
    public void invalidMethod() throws IOException {
        expect(request.getHeader("Authorization")).andReturn("unknown Blub");
        replay(request, response);

        context.handleSecurity(request,response);
    }


}
