package org.jolokia.osgi.servlet;

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

import java.lang.instrument.Instrumentation;
import java.util.Set;

import javax.management.MBeanServerConnection;
import javax.servlet.*;

import org.easymock.EasyMock;
import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.detector.ServerDetector;
import org.jolokia.detector.ServerHandle;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.test.util.HttpTestUtil;
import org.jolokia.util.LogHandler;
import org.osgi.framework.*;
import org.osgi.framework.Filter;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertNull;

/**
 * @author roland
 * @since 02.09.11
 */
public class JolokiaServletTest {

    private ServletConfig config;
    private ServletContext servletContext;
    private BundleContext bundleContext;
    private JolokiaServlet servlet;


    @BeforeMethod
    public void setup() {

        config = createMock(ServletConfig.class);
        servletContext = createMock(ServletContext.class);
        bundleContext = createMock(BundleContext.class);

        expect(config.getServletContext()).andReturn(servletContext).anyTimes();
        expect(config.getServletName()).andReturn("jolokia").anyTimes();
    }

    @Test
    public void simpleInit() throws ServletException, InvalidSyntaxException {
        servlet = new JolokiaServlet();
        initWithLogService();
    }

    @Test
    public void simpleInitWithGivenBundleContext() throws InvalidSyntaxException, ServletException {
        servlet = new JolokiaServlet(bundleContext,new AllowAllRestrictor());
        initWithLogService();
    }


    @Test
    public void initWithoutBundleContext() throws ServletException {
        servlet = new JolokiaServlet();

        expect(servletContext.getAttribute("osgi-bundlecontext")).andReturn(null).anyTimes();
        HttpTestUtil.prepareServletConfigMock(config);
        HttpTestUtil.prepareServletContextMock(servletContext);
        preparePlainLogging();
        replay(servletContext,config);
        servlet.init(config);
        servlet.destroy();

    }

    private void initWithLogService() throws InvalidSyntaxException, ServletException {
        prepareLogServiceLookup();
        HttpTestUtil.prepareServletConfigMock(config);
        HttpTestUtil.prepareServletContextMock(servletContext);
        preparePlainLogging();

        replay(config, servletContext, bundleContext);

        servlet.init(config);
        assertNull(JolokiaServlet.getCurrentBundleContext());

        LogHandler handler = servlet.createLogHandler(config, true);
        handler.debug("Debug");
        handler.info("Info");
        handler.error("Error",new Exception());
        destroyServlet();
    }



    private void destroyServlet() {
        reset(bundleContext);
        bundleContext.removeServiceListener(EasyMock.<ServiceListener>anyObject());
        replay(bundleContext);
        servlet.destroy();
    }

    // ===========================================================================

    private void prepareLogServiceLookup() throws InvalidSyntaxException {
        expect(servletContext.getAttribute("osgi-bundlecontext")).andReturn(bundleContext).times(3);
        Filter mockFilter = mock(Filter.class);
        expect(bundleContext.createFilter("(objectClass=org.osgi.service.log.LogService)")).andReturn(mockFilter).times(2);
        bundleContext.addServiceListener(EasyMock.<ServiceListener>anyObject(), eq("(objectClass=org.osgi.service.log.LogService)"));
        expectLastCall().times(2);
        expect(bundleContext.getServiceReferences(LogService.class.getName(),null)).andReturn(null).times(2);
    }

    private void preparePlainLogging() {
        servletContext.log(EasyMock.<String>anyObject());
        servletContext.log("jolokia: Debug");
        servletContext.log("jolokia: Info");
        servletContext.log(eq("jolokia: Error"),isA(Exception.class));
        expectLastCall().anyTimes();
    }

    // ===========================================================================
    // Detector to avoid checkup with every detector

    public static class CatchAllDetector implements ServerDetector {

        public ServerHandle detect(MBeanServerExecutor pMBeanServerExecutor) {
            return new ServerHandle(null,null,null, null);
        }

        public void addMBeanServers(Set<MBeanServerConnection> pMBeanServers) {
        }

        public void jvmAgentStartup(Instrumentation instrumentation) {}
    }

}
