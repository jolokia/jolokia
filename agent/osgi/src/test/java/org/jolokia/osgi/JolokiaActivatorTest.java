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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Dictionary;

import javax.servlet.ServletException;

import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;
import org.jolokia.osgi.servlet.OsgiServletConfiguration;
import org.jolokia.osgi.servlet.OsgiAgentServlet;
import org.jolokia.core.config.ConfigKey;
import org.osgi.framework.*;
import org.osgi.service.http.*;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertNotNull;

/**
 * @author roland
 * @since 01.09.11
 */
public class JolokiaActivatorTest {

    public static final String SERVICE_FILTER = "(objectClass=org.osgi.service.http.HttpService)";

    private BundleContext context;
    private JolokiaActivator activator;

    // Listener registered by the ServiceTracker
    private ServiceListener listener;

    private ServiceRegistration registration;
    private HttpService httpService;
    private ServiceReference httpServiceReference;

    @BeforeMethod
    public void setup() {
        activator = new JolokiaActivator();
        context = createMock(BundleContext.class);
    }

    @Test
    public void withHttpService() throws InvalidSyntaxException, NoSuchFieldException, IllegalAccessException, ServletException, NamespaceException {
        startActivator(true, null);
        startupHttpService();
        unregisterJolokiaServlet();
        stopActivator(true);
        verify(httpService);
    }

    @Test
    public void withHttpServiceAndExplicitServiceShutdown() throws InvalidSyntaxException, NoSuchFieldException, IllegalAccessException, ServletException, NamespaceException {
        startActivator(true, null);
        startupHttpService();

        // Expect that servlet gets unregistered
        unregisterJolokiaServlet();
        shutdownHttpService();

        stopActivator(true);
        // Check, that unregister was called
        verify(httpService);
    }

    @Test
    public void withHttpServiceAndAdditionalFilter() throws InvalidSyntaxException, NoSuchFieldException, IllegalAccessException, ServletException, NamespaceException {
        startActivator(true, "(Wibble=Wobble)");
        startupHttpService();
        unregisterJolokiaServlet();
        stopActivator(true);
        verify(httpService);
    }

    @Test
    public void modifiedService() throws InvalidSyntaxException, ServletException, NamespaceException {
        startActivator(true, null);
        startupHttpService();

        // Expect that servlet gets unregistered
        modifiedHttpService();

        // Check, that unregister was called
        verify(httpService);
    }

    @Test
    public void withoutServices() throws InvalidSyntaxException {
        startActivator(false, null);
        stopActivator(false);
    }

    @Test
    public void exceptionDuringRegistration() throws InvalidSyntaxException, ServletException, NamespaceException {
        startActivator(true, null);
        ServletException exp = new ServletException();
        prepareErrorLog(exp,"Servlet");
        startupHttpService(exp);
    }

    @Test
    public void exceptionDuringRegistration2() throws InvalidSyntaxException, ServletException, NamespaceException {
        startActivator(true, null);
        NamespaceException exp = new NamespaceException("Error");
        prepareErrorLog(exp,"Namespace");
        startupHttpService(exp);
    }

    @Test
    public void exceptionWithoutLogService() throws InvalidSyntaxException, ServletException, NamespaceException {
        startActivator(true, null);
        expect(context.getServiceReference(LogService.class.getName())).andReturn(null);
        startupHttpService(new ServletException());
    }

    @Test
    public void authentication() throws InvalidSyntaxException, ServletException, NamespaceException {
        startActivator(true, null);
        startupHttpService("roland","s!cr!t");
        unregisterJolokiaServlet();
        stopActivator(true);
        verify(httpService);
    }

    // ========================================================================================================

    private void prepareErrorLog(Exception exp,String msg) {
        ServiceReference logServiceReference = createMock(ServiceReference.class);
        LogService logService = createMock(LogService.class);
        expect(context.getServiceReference(LogService.class.getName())).andReturn(logServiceReference);
        expect(context.getService(logServiceReference)).andReturn(logService);
        logService.log(eq(LogService.LOG_ERROR), find(msg), eq(exp));
        expect(context.ungetService(logServiceReference)).andReturn(false);
        replay(logServiceReference, logService);
    }

    private void shutdownHttpService() {
        listener.serviceChanged(new ServiceEvent(ServiceEvent.UNREGISTERING,httpServiceReference));
    }

    private void modifiedHttpService() {
        listener.serviceChanged(new ServiceEvent(ServiceEvent.MODIFIED,httpServiceReference));
    }

    private void unregisterJolokiaServlet() {
        reset(httpService);
        // Will be called when activator is stopped
        httpService.unregister(ConfigKey.AGENT_CONTEXT.getDefaultValue());
        replay(httpService);
    }

    private void startupHttpService(Object ... args) throws ServletException, NamespaceException {
        String auth[] = new String[] { null, null };
        Exception exp = null;
        int i = 0;
        for (Object arg : args) {
            if (arg instanceof String) {
                auth[i++] = (String) arg;
            } else if (arg instanceof Exception) {
                exp = (Exception) arg;
            }
        }


        httpServiceReference = createMock(ServiceReference.class);
        httpService = createMock(HttpService.class);

        expect(context.getService(httpServiceReference)).andReturn(httpService);
        i = 0;
        for (ConfigKey key : ConfigKey.values()) {
            if (auth[0] != null && key == ConfigKey.USER) {
                expect(context.getProperty("org.jolokia." + ConfigKey.USER.getKeyValue())).andReturn(auth[0]).times(2);
            } else if (auth[1] != null && key == ConfigKey.PASSWORD) {
                expect(context.getProperty("org.jolokia." + ConfigKey.PASSWORD.getKeyValue())).andReturn(auth[1]).times(2);
            } else {
                expect(context.getProperty("org.jolokia." + key.getKeyValue())).andReturn(
                        i++ % 2 == 0 ? key.getDefaultValue() : null).anyTimes();
            }
        }
        httpService.registerServlet(eq(ConfigKey.AGENT_CONTEXT.getDefaultValue()),
                                    isA(OsgiAgentServlet.class),
                                    EasyMock.<Dictionary>anyObject(),
                                    EasyMock.<HttpContext>anyObject());
        if (exp != null) {
            expectLastCall().andThrow(exp);
        }
        replay(context, httpServiceReference, httpService);

        // Attach service
        listener.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, httpServiceReference));
    }

    private void stopActivator(boolean withHttpListener) {
        reset(context);
        if (withHttpListener) {
            reset(registration);
        }
        if (withHttpListener) {
            context.removeServiceListener(listener);
            expect(context.getProperty("org.jolokia." + ConfigKey.AGENT_CONTEXT.getKeyValue()))
                    .andReturn(ConfigKey.AGENT_CONTEXT.getDefaultValue());
            registration.unregister();
        }
        replay(context);
        if (withHttpListener) {
            replay(registration);
        }
        activator.stop(context);
    }

    private void startActivator(boolean withHttpListener, String httpFilter) throws InvalidSyntaxException {
        reset(context);
        prepareStart(withHttpListener, true, httpFilter);

        replay(context);
        if (withHttpListener) {
            replay(registration);
        }

        activator.start(context);
        if (withHttpListener) {
            assertNotNull(listener);
        }
        reset(context);
    }

    private void prepareStart(boolean doHttpService, boolean doRestrictor, String httpFilter) throws InvalidSyntaxException {
        expect(context.getProperty("org.jolokia.listenForHttpService")).andReturn("" + doHttpService);
        if (doHttpService) {
            expect(context.getProperty("org.jolokia.httpServiceFilter")).andReturn(httpFilter);

            Filter filter = createFilterMockWithToString(SERVICE_FILTER, httpFilter);
            expect(context.createFilter(filter.toString())).andReturn(filter);
            expect(context.getProperty("org.osgi.framework.version")).andReturn("4.5.0");
            context.addServiceListener(rememberListener(), eq(filter.toString()));
            expect(context.getServiceReferences(null, filter.toString())).andReturn(null);
            registration = createMock(ServiceRegistration.class);
            expect(context.registerService(OsgiServletConfiguration.class.getName(), activator, null)).andReturn(registration);

        }
        expect(context.getProperty("org.jolokia.useRestrictorService")).andReturn("" + doRestrictor);
    }

    // Easymock work around given the fact you can not mock toString() using easymock (because it easymock uses toString()
    // of mocked objects internally)
    private Filter createFilterMockWithToString(final String filter, final String additionalFilter) {
        return (Filter) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Filter.class}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("toString")) {
                    if (additionalFilter == null) {
                        return filter;
                    } else {
                        return "(&" + filter + additionalFilter +")" ;
                    }
                }
                throw new UnsupportedOperationException("Sorry this is a very limited proxy implementation of Filter");
            }
        });
    }

    private ServiceListener rememberListener() {
        reportMatcher(new IArgumentMatcher() {
            public boolean matches(Object argument) {
                listener = (ServiceListener) argument;
                return true;
            }

            public void appendTo(StringBuffer buffer) {
            }
        });
        return null;
    }
}
