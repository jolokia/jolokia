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

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;
import org.jolokia.config.ConfigKey;
import org.jolokia.osgi.security.Authenticator;
import org.jolokia.osgi.servlet.JolokiaContext;
import org.jolokia.osgi.servlet.JolokiaServlet;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.osgi.service.log.LogService;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.find;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reportMatcher;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

/**
 * @author roland
 * @since 01.09.11
 */
public class JolokiaActivatorTest {

    private static final String HTTP_SERVICE_FILTER = "(objectClass=org.osgi.service.http.HttpService)";
    private static final String CONFIG_SERVICE_FILTER = "(objectClass=org.osgi.service.cm.ConfigurationAdmin)";
    private static final String AUTHENTICATOR_SERVICE_FILTER =
        "(objectClass=" + Authenticator.class.getName() + ")";

    private BundleContext context;
    private JolokiaActivator activator;

    // Listener registered by the ServiceTracker
    private ServiceListener httpServiceListener;
    private ServiceRegistration registration;

    private HttpService httpService;
    private ServiceReference httpServiceReference;

    private ServiceReference configAdminRef;
    private ServiceListener configAdminServiceListener;

    private ServiceListener authenticationServiceListener;

    @BeforeMethod
    public void setup() {
        activator = new JolokiaActivator();
        context = createMock(BundleContext.class);
    }

    @Test
    public void withHttpService() throws InvalidSyntaxException, NoSuchFieldException, IllegalAccessException, ServletException, NamespaceException, IOException {
        startActivator(true, null, null);
        startupHttpService();
        unregisterJolokiaServlet();
        stopActivator(true);
        verify(httpService);
    }

    @Test
    public void withHttpServiceAndExplicitServiceShutdown() throws InvalidSyntaxException, NoSuchFieldException, IllegalAccessException, ServletException, NamespaceException, IOException {
        startActivator(true, null, null);
        startupHttpService();

        // Expect that servlet gets unregistered
        unregisterJolokiaServlet();
        shutdownHttpService();

        stopActivator(true);
        // Check, that unregister was called
        verify(httpService);
    }

    @Test
    public void withHttpServiceAndAdditionalFilter() throws InvalidSyntaxException, NoSuchFieldException, IllegalAccessException, ServletException, NamespaceException, IOException {
        startActivator(true, "(Wibble=Wobble)", null);
        startupHttpService();
        unregisterJolokiaServlet();
        stopActivator(true);
        verify(httpService);
    }

    @Test
    public void modifiedService() throws InvalidSyntaxException, ServletException, NamespaceException, IOException {
        startActivator(true, null, null);
        startupHttpService();

        // Expect that servlet gets unregistered
        modifiedHttpService();

        // Check, that unregister was called
        verify(httpService);
    }

    @Test
    public void withoutServices() throws InvalidSyntaxException, IOException {
        startActivator(false, null, null);
        stopActivator(false);
    }

    @Test
    public void exceptionDuringRegistration() throws InvalidSyntaxException, ServletException, NamespaceException, IOException {
        startActivator(true, null, null);
        ServletException exp = new ServletException();
        prepareErrorLog(exp,"Servlet");
        startupHttpService(exp);
    }

    @Test
    public void exceptionDuringRegistration2() throws InvalidSyntaxException, ServletException, NamespaceException, IOException {
        startActivator(true, null, null);
        NamespaceException exp = new NamespaceException("Error");
        prepareErrorLog(exp,"Namespace");
        startupHttpService(exp);
    }

    @Test
    public void exceptionWithoutLogService() throws InvalidSyntaxException, ServletException, NamespaceException, IOException {
        startActivator(true, null, null);
        expect(context.getServiceReference(LogService.class.getName())).andReturn(null);
        startupHttpService(new ServletException());
    }

    @Test
    public void authentication() throws InvalidSyntaxException, ServletException, NamespaceException, IOException {
        startActivator(true, null, null);
        startupHttpService("roland","s!cr!t");
        unregisterJolokiaServlet();
        stopActivator(true);
        verify(httpService);
    }

    @Test
    public void authenticationSecure() throws InvalidSyntaxException, ServletException, NamespaceException, IOException {
        startActivator(true, null, null);
        startupHttpService("roland","s!cr!t","jaas");
        unregisterJolokiaServlet();
        stopActivator(true);
        verify(httpService);
    }

    @Test
    public void testConfigAdminEmptyDictionary() throws Exception {
        Dictionary dict = new Hashtable();
        startActivator(false, null, dict);
        stopActivator(false);
    }

    @Test
    public void testConfigAdminEmptyDictionaryNoHttpListener() throws Exception {
        Dictionary dict = new Hashtable();
        startActivator(false, null, dict);
        stopActivator(false);
    }

    @Test
    public void testSomePropsFromConfigAdmin() throws Exception {
        Dictionary<String, String> dict = new Hashtable<String, String>();
        dict.put("org.jolokia.user", "roland");
        dict.put("org.jolokia.password", "s!cr!t");
        dict.put("org.jolokia.authMode", "jaas");
        startActivator(true, null, dict);
        startupHttpServiceWithConfigAdminProps();
        unregisterJolokiaServlet();
        stopActivator(true);
        verify(httpService);
    }

    @Test
    public void testServiceAuthModeFromConfigAdmin() throws Exception {
        final Dictionary<String, String> dict = new Hashtable<String, String>();
        dict.put("org.jolokia.authMode", "service-all");
        startActivator(true, AUTHENTICATOR_SERVICE_FILTER, dict);
        startupHttpServiceWithConfigAdminProps(true);
        unregisterJolokiaServlet();
        stopActivator(true);
        verify(httpService);
    }

    @Test
    public void testNoServiceAvailable() throws Exception {
        final HttpServletRequest request = createMock(HttpServletRequest.class);
        final HttpServletResponse response = createNiceMock(HttpServletResponse.class);
        final Dictionary<String, String> dict = new Hashtable<String, String>();
        dict.put("org.jolokia.authMode", "service-all");
        startActivator(true, AUTHENTICATOR_SERVICE_FILTER, dict);
        startupHttpServiceWithConfigAdminProps(true);

        expect(request.getHeader("Authorization")).andReturn("Basic cm9sYW5kOnMhY3IhdA==");
        request.setAttribute(HttpContext.AUTHENTICATION_TYPE, HttpServletRequest.BASIC_AUTH);
        request.setAttribute(HttpContext.REMOTE_USER, "roland");
        replay(request, response);

        // w/o an Authenticator Service registered, requests should always be denied.

        final HttpContext httpContext = activator.getHttpContext();
        assertFalse(httpContext.handleSecurity(request, response));

        unregisterJolokiaServlet();
        stopActivator(true);
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
        httpServiceListener.serviceChanged(new ServiceEvent(ServiceEvent.UNREGISTERING, httpServiceReference));
    }

    private void modifiedHttpService() {
        httpServiceListener.serviceChanged(new ServiceEvent(ServiceEvent.MODIFIED, httpServiceReference));
    }

    private void unregisterJolokiaServlet() {
        reset(httpService);
        // Will be called when activator is stopped
        httpService.unregister(ConfigKey.AGENT_CONTEXT.getDefaultValue());
        replay(httpService);
    }

    private void startupHttpService(Object ... args) throws ServletException, NamespaceException {
        String auth[] = new String[] { null, null,null };
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
                expect(context.getProperty("org.jolokia." + ConfigKey.USER.getKeyValue())).andStubReturn(auth[0]);
            } else if (auth[1] != null && key == ConfigKey.PASSWORD) {
                expect(context.getProperty("org.jolokia." + ConfigKey.PASSWORD.getKeyValue())).andStubReturn(auth[1]);
            } else if (auth[2] != null && key == ConfigKey.AUTH_MODE) {
                expect(context.getProperty("org.jolokia." + ConfigKey.AUTH_MODE.getKeyValue())).andStubReturn(auth[2]);
            } else {
                expect(context.getProperty("org.jolokia." + key.getKeyValue())).andStubReturn(
                        i++ % 2 == 0 ? key.getDefaultValue() : null);
            }
        }

        httpService.registerServlet(eq(ConfigKey.AGENT_CONTEXT.getDefaultValue()),
                                    isA(JolokiaServlet.class),
                                    EasyMock.<Dictionary>anyObject(),
                                    EasyMock.<HttpContext>anyObject());
        if (exp != null) {
            expectLastCall().andThrow(exp);
        }
        replay(context, httpServiceReference, httpService);

        // Attach service
        httpServiceListener.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, httpServiceReference));
    }

    private void startupHttpServiceWithConfigAdminProps()
            throws ServletException, NamespaceException, InvalidSyntaxException {

        this.startupHttpServiceWithConfigAdminProps(false);
    }

    private void startupHttpServiceWithConfigAdminProps(boolean serviceAuthentication)
            throws ServletException, NamespaceException, InvalidSyntaxException {

        httpServiceReference = createMock(ServiceReference.class);
        httpService = createMock(HttpService.class);

        expect(context.getService(httpServiceReference)).andReturn(httpService);
        int i = 0;
        for (ConfigKey key : ConfigKey.values()) {
            if (!serviceAuthentication && (key == ConfigKey.USER || key == ConfigKey.PASSWORD || key == ConfigKey.AUTH_MODE)) {
                //ignore these, they will be provided from config admin service
            } else if (serviceAuthentication && (key == ConfigKey.AUTH_MODE)) {
                //ignore these, they will be provided from config admin service
            } else {
                expect(context.getProperty("org.jolokia." + key.getKeyValue())).andStubReturn(
                        i++ % 2 == 0 ? key.getDefaultValue() : null);
            }
        }

        final Filter filter = createFilterMockWithToString(AUTHENTICATOR_SERVICE_FILTER, null);
        expect(context.createFilter(filter.toString())).andStubReturn(filter);
        expect(context.getServiceReferences(Authenticator.class.getName(), null)).andStubReturn(null);
        context.addServiceListener(authenticationServiceListener(), eq(filter.toString()));


        httpService.registerServlet(eq(ConfigKey.AGENT_CONTEXT.getDefaultValue()),
                isA(JolokiaServlet.class),
                EasyMock.<Dictionary>anyObject(),
                EasyMock.<HttpContext>anyObject());

        replay(context, httpServiceReference, httpService);

        // Attach service
        httpServiceListener.serviceChanged(new ServiceEvent(ServiceEvent.REGISTERED, httpServiceReference));
    }

    private void stopActivator(boolean withHttpListener) {
        reset(context);
        if (withHttpListener) {
            reset(registration);
        }
        if (withHttpListener) {
            context.removeServiceListener(httpServiceListener);
            expect(context.getProperty("org.jolokia." + ConfigKey.AGENT_CONTEXT.getKeyValue()))
                    .andReturn(ConfigKey.AGENT_CONTEXT.getDefaultValue());
            registration.unregister();
        }

        expect(context.ungetService(anyObject(ServiceReference.class))).andReturn(true).anyTimes();
        context.removeServiceListener(configAdminServiceListener);
        context.removeServiceListener(authenticationServiceListener);

        replay(context);
        if (withHttpListener) {
            replay(registration);
        }
        activator.stop(context);
    }

    private void startActivator(boolean withHttpListener, String httpFilter, Dictionary configAdminProps) throws InvalidSyntaxException, IOException {
        reset(context);
        prepareStart(withHttpListener, true, httpFilter, configAdminProps);

        replay(context);
        if (withHttpListener) {
            replay(registration);
        }

        activator.start(context);
        if (withHttpListener) {
            assertNotNull(httpServiceListener);
        }
        assertNotNull(configAdminServiceListener);

        reset(context);
    }

    private void prepareStart(boolean doHttpService, boolean doRestrictor, String httpFilter, Dictionary configAdminProps) throws InvalidSyntaxException, IOException {
        expect(context.getProperty("org.jolokia.listenForHttpService")).andReturn("" + doHttpService);
        if (doHttpService) {
            expect(context.getProperty("org.jolokia.httpServiceFilter")).andReturn(httpFilter);

            Filter filter = createFilterMockWithToString(HTTP_SERVICE_FILTER, httpFilter);
            expect(context.createFilter(filter.toString())).andReturn(filter);
            expect(context.getProperty("org.osgi.framework.version")).andReturn("4.5.0");
            context.addServiceListener(httpRememberListener(), eq(filter.toString()));
            expect(context.getServiceReferences(null, filter.toString())).andReturn(null);
            registration = createMock(ServiceRegistration.class);
            expect(context.registerService(JolokiaContext.class.getName(), activator, null)).andReturn(registration);

        }

        //Setup ConfigurationAdmin service
        Filter configFilter = createFilterMockWithToString(CONFIG_SERVICE_FILTER, null);
        expect(context.createFilter(configFilter.toString())).andReturn(configFilter);
        context.addServiceListener(configAdminRememberListener(), eq(configFilter.toString()));
        configAdminRef = createMock(ServiceReference.class);
        expect(context.getServiceReferences(ConfigurationAdmin.class.getCanonicalName(), null)).andReturn(new ServiceReference[]{configAdminRef}).anyTimes();
        ConfigurationAdmin configAdmin = createMock(ConfigurationAdmin.class);
        Configuration config = createMock(Configuration.class);
        expect(config.getProperties()).andReturn(configAdminProps).anyTimes();
        expect(configAdmin.getConfiguration("org.jolokia.osgi")).andReturn(config).anyTimes();
        expect(context.getService(configAdminRef)).andReturn(configAdmin).anyTimes();
        replay(configAdminRef, configAdmin, config);

        expect(context.getProperty("org.jolokia.useRestrictorService")).andReturn("" + doRestrictor);
    }

    // Easymock work around given the fact you can not mock toString() using easymock (because it easymock uses toString()
    // of mocked objects internally)
    private Filter createFilterMockWithToString(final String filter, final String additionalFilter) {
        return createFilterMockWithToString(this.getClass(), filter, additionalFilter);
    }

    private static Filter createFilterMockWithToString(final Class clazz, final String filter, final String additionalFilter) {
        return (Filter) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{Filter.class}, new InvocationHandler() {
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


    private ServiceListener httpRememberListener() {
        reportMatcher(new IArgumentMatcher() {
            public boolean matches(Object argument) {
                httpServiceListener = (ServiceListener) argument;
                return true;
            }

            public void appendTo(StringBuffer buffer) {
            }
        });
        return null;
    }

    private ServiceListener configAdminRememberListener() {
        reportMatcher(new IArgumentMatcher() {
            public boolean matches(Object argument) {
                configAdminServiceListener = (ServiceListener) argument;
                return true;
            }

            public void appendTo(StringBuffer buffer) {
            }
        });
        return null;
    }

    private ServiceListener authenticationServiceListener() {
        reportMatcher(new IArgumentMatcher() {
            public boolean matches(Object argument) {
                authenticationServiceListener = (ServiceListener) argument;
                return true;
            }

            public void appendTo(StringBuffer stringBuffer) {
            }
        });
        return null;
    }
}
