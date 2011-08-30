package org.jolokia.http;

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

import java.util.Vector;

import javax.servlet.*;

import org.easymock.EasyMock;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;

/**
 * @author roland
 * @since 30.08.11
 */
public class AgentServletTest {

    @Test
    public void simple() throws ServletException {
        AgentServlet servlet = new AgentServlet();
        ServletConfig config = createMock(ServletConfig.class);
        Vector paramNames = new Vector();

        expect(config.getInitParameterNames()).andReturn(paramNames.elements());
        ServletContext ctx = createMock(ServletContext.class);
        expect(config.getServletContext()).andReturn(ctx);
        expect(config.getServletName()).andReturn("jolokia");
        ctx.log(EasyMock.<String>anyObject());
        replay(config, ctx);
        servlet.init(config);
    }
}
