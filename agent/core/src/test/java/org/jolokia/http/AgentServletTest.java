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

import java.io.FileNotFoundException;
import java.security.PrivilegedActionException;
import java.util.*;

import javax.servlet.*;

import org.easymock.EasyMock;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.util.ConfigKey;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;

/**
 * @author roland
 * @since 30.08.11
 */
public class AgentServletTest {

    private ServletContext context;
    private ServletConfig config;

    private AgentServlet servlet;

    @Test
    public void simple() throws ServletException {
        servlet = new AgentServlet();
        initMocks(null,"No access restrictor found",null);
        replay(config, context);

        servlet.init(config);
        servlet.destroy();
    }

    @Test
    public void simpleWithAcessRestriction() throws ServletException {
        servlet = new AgentServlet();
        initMocks(new String[] { ConfigKey.POLICY_LOCATION.getKeyValue(),"classpath:/access-sample1.xml" },
                  "Using access restrictor.*access-sample1.xml",null);
        replay(config, context);

        servlet.init(config);
        servlet.destroy();
    }

    @Test
    public void simpleWithInvalidPolicyFile() throws ServletException {
        servlet = new AgentServlet();
        initMocks(new String[] { ConfigKey.POLICY_LOCATION.getKeyValue(),"file:///blablub.xml" },
                  "Error.*blablub.xml.*Denying", FileNotFoundException.class );
        replay(config, context);

        servlet.init(config);
        servlet.destroy();
    }

    @Test
    public void customAccessRestrictor() throws ServletException {
        servlet = new AgentServlet(new AllowAllRestrictor());
        initMocks(null,"custom access",null);
        replay(config,context);

        servlet.init(config);
        servlet.destroy();
    }


    // ============================================================================================

    private void initMocks(String[] pInitParams,String pLogRegexp,Class<? extends Exception> pExceptionClass) {
        config = createMock(ServletConfig.class);


        Map<String,String> configParams = new HashMap<String, String>();
        if (pInitParams != null) {
            for (int i = 0; i < pInitParams.length; i += 2) {
                configParams.put(pInitParams[i],pInitParams[i+1]);
            }
            for (String key : configParams.keySet()) {
                expect(config.getInitParameter(key)).andReturn(configParams.get(key)).anyTimes();
            }
        }
        
        Vector paramNames = new Vector(configParams.keySet());
        expect(config.getInitParameterNames()).andReturn(paramNames.elements());

        context = createMock(ServletContext.class);
        expect(config.getServletContext()).andReturn(context).anyTimes();
        expect(config.getServletName()).andReturn("jolokia").anyTimes();
        if (pExceptionClass != null) {
            context.log(find(pLogRegexp),isA(pExceptionClass));
        } else {
            context.log(find(pLogRegexp));
        }
    }
}
