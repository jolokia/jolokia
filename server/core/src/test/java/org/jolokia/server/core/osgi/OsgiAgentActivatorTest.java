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
import java.lang.reflect.Proxy;
import java.util.Dictionary;
import java.util.Hashtable;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.osgi.security.Authenticator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.log.LogService;
import org.osgi.service.servlet.context.ServletContextHelper;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.find;
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
public class OsgiAgentActivatorTest {

    private static final String CONFIG_SERVICE_FILTER = "(objectClass=org.osgi.service.cm.ConfigurationAdmin)";
    private static final String AUTHENTICATOR_SERVICE_FILTER =
        "(objectClass=" + Authenticator.class.getName() + ")";

    private BundleContext context;
    private OsgiAgentActivator activator;

    private ServiceRegistration<ServletContextHelper> contextRegistration;
    private ServiceRegistration<HttpServlet> servletRegistration;

    private ServiceListener configAdminServiceListener;

    private ServiceListener authenticationServiceListener;

    @BeforeMethod
    public void setup() {
        activator = new OsgiAgentActivator();
        context = createMock(BundleContext.class);
    }

    @Test
    public void withHttpService() throws InvalidSyntaxException, IOException {
        startupHttpService();
        startActivator(true, null);
        stopActivator(true);
        verify(context);
    }

    @Test
    public void withHttpServiceAndExplicitServiceShutdown() throws InvalidSyntaxException, IOException {
        startupHttpService();
        startActivator(true, null);

        stopActivator(true);
        // Check, that unregister was called
        verify(context);
    }

    @Test
    public void withoutServices() throws InvalidSyntaxException, IOException {
        startActivator(false, null);
        stopActivator(false);
    }

    @Test
    public void authentication() throws InvalidSyntaxException, ServletException, IOException {
        startupHttpService("roland","s!cr!t");
        startActivator(true, null);
        stopActivator(true);
        verify(context);
    }

    @Test
    public void authenticationSecure() throws InvalidSyntaxException, IOException {
        startupHttpService("roland","s!cr!t","jaas");
        startActivator(true, null);
        stopActivator(true);
        verify(context);
    }

    @Test
    public void testConfigAdminEmptyDictionary() throws Exception {
        Dictionary<String, Object> dict = new Hashtable<>();
        startActivator(false, dict);
        stopActivator(false);
    }

    @Test
    public void testConfigAdminEmptyDictionaryNoHttpListener() throws Exception {
        Dictionary<String, Object> dict = new Hashtable<>();
        startActivator(false, dict);
        stopActivator(false);
    }

    @Test
    public void testSomePropsFromConfigAdmin() throws Exception {
        Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("org.jolokia.user", "roland");
        dict.put("org.jolokia.password", "s!cr!t");
        dict.put("org.jolokia.authMode", "jaas");
        startupHttpServiceWithConfigAdminProps();
        startActivator(true, dict);
        stopActivator(true);
        verify(context);
    }

    @Test
    public void testServiceAuthModeFromConfigAdmin() throws Exception {
        final Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("org.jolokia.authMode", "service-all");
        startupHttpServiceWithConfigAdminProps(true);
        startActivator(true, dict);
        stopActivator(true);
        verify(context);
    }

    @Test
    public void testNoServiceAvailable() throws Exception {
        final HttpServletRequest request = createMock(HttpServletRequest.class);
        final HttpServletResponse response = createNiceMock(HttpServletResponse.class);
        final Dictionary<String, Object> dict = new Hashtable<>();
        dict.put("org.jolokia.authMode", "service-all");
        startupHttpServiceWithConfigAdminProps(true);
        startActivator(true, dict);

        expect(request.getHeader("Authorization")).andReturn("Basic cm9sYW5kOnMhY3IhdA==");
        replay(request, response);

        // w/o an Authenticator Service registered, requests should always be denied.

        final ServletContextHelper httpContext = activator.getServletContextHelper();
        assertFalse(httpContext.handleSecurity(request, response));

        stopActivator(true);
    }

    // ========================================================================================================

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void prepareErrorLog(Exception exp,String msg) {
        ServiceReference logServiceReference = createMock(ServiceReference.class);
        LogService logService = createMock(LogService.class);
        expect(context.getServiceReference(LogService.class.getName())).andReturn(logServiceReference);
        expect(context.getService(logServiceReference)).andReturn(logService);
        logService.log(eq(LogService.LOG_ERROR), find(msg), eq(exp));
        expect(context.ungetService(logServiceReference)).andReturn(false);
        replay(logServiceReference, logService);
    }

    private void startupHttpService(Object ... args) {
        String[] auth = new String[] { null, null,null };
        Exception exp = null;
        int i = 0;
        for (Object arg : args) {
            if (arg instanceof String) {
                auth[i++] = (String) arg;
            } else if (arg instanceof Exception) {
                exp = (Exception) arg;
            }
        }

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

        if (exp != null) {
            expectLastCall().andThrow(exp);
        }
//        replay(context);
    }

    private void startupHttpServiceWithConfigAdminProps() throws InvalidSyntaxException {

        this.startupHttpServiceWithConfigAdminProps(false);
    }

    private void startupHttpServiceWithConfigAdminProps(boolean serviceAuthentication) throws InvalidSyntaxException {

        int i = 0;
        for (ConfigKey key : ConfigKey.values()) {
            if (!serviceAuthentication && (key == ConfigKey.USER || key == ConfigKey.PASSWORD || key == ConfigKey.AUTH_MODE)) {
                //ignore these, they will be provided from config admin service
                continue;
            }
            if (serviceAuthentication && (key == ConfigKey.AUTH_MODE)) {
                //ignore these, they will be provided from config admin service
                continue;
            }
            expect(context.getProperty("org.jolokia." + key.getKeyValue())).andStubReturn(
                    i++ % 2 == 0 ? key.getDefaultValue() : null);
        }

        final Filter filter = createFilterMockWithToString(AUTHENTICATOR_SERVICE_FILTER, null);
        expect(context.createFilter(filter.toString())).andStubReturn(filter);
        expect(context.getServiceReferences(Authenticator.class.getName(), null)).andStubReturn(null);
        context.addServiceListener(authenticationServiceListener(), eq(filter.toString()));

//        replay(context);
    }

    private void stopActivator(boolean withHttpListener) {
        reset(context);
        if (withHttpListener) {
            reset(contextRegistration);
            reset(servletRegistration);
        }
        if (withHttpListener) {
            contextRegistration.unregister();
            servletRegistration.unregister();
        }

        expect(context.ungetService(anyObject(ServiceReference.class))).andReturn(true).anyTimes();
        context.removeServiceListener(configAdminServiceListener);
        context.removeServiceListener(authenticationServiceListener);
        expectLastCall().anyTimes();

        replay(context);
        if (withHttpListener) {
            replay(contextRegistration);
            replay(servletRegistration);
        }
        activator.stop(context);
    }

    private void startActivator(boolean doWhiteboardServlet, Dictionary<String, Object> configAdminProps) throws InvalidSyntaxException, IOException {
//        reset(context);
        prepareStart(doWhiteboardServlet, true, configAdminProps);

        replay(context);
        if (doWhiteboardServlet) {
            replay(contextRegistration);
            replay(servletRegistration);
        }

        activator.start(context);
        assertNotNull(configAdminServiceListener);

        reset(context);
    }

    private void prepareStart(boolean doWhiteboardServlet, boolean doRestrictor, Dictionary<String, Object> configAdminProps) throws InvalidSyntaxException, IOException {
        expect(context.getProperty("org.jolokia.registerWhiteboardServlet")).andReturn("" + doWhiteboardServlet);
        if (doWhiteboardServlet) {
            contextRegistration = createMock(ServiceRegistration.class);
            servletRegistration = createMock(ServiceRegistration.class);
            expect(context.registerService(eq(ServletContextHelper.class), EasyMock.<ServletContextHelper>anyObject(), anyObject()))
                    .andReturn(contextRegistration);
            expect(context.registerService(eq(HttpServlet.class), EasyMock.<HttpServlet>anyObject(), anyObject()))
                    .andReturn(servletRegistration);
            expect(context.getProperty("org.osgi.framework.version")).andReturn("4.5.0");
        }

        //Setup ConfigurationAdmin service
        Filter configFilter = createFilterMockWithToString(CONFIG_SERVICE_FILTER, null);
        expect(context.createFilter(configFilter.toString())).andReturn(configFilter);
        context.addServiceListener(configAdminRememberListener(), eq(configFilter.toString()));
        ServiceReference<ConfigurationAdmin> configAdminRef = createMock(ServiceReference.class);
        expect(context.getServiceReferences(ConfigurationAdmin.class.getCanonicalName(), null)).andReturn(new ServiceReference[]{ configAdminRef }).anyTimes();
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

    private static Filter createFilterMockWithToString(final Class<?> clazz, final String filter, final String additionalFilter) {
        return (Filter) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{Filter.class}, (proxy, method, args) -> {
            if (method.getName().equals("toString")) {
                if (additionalFilter == null) {
                    return filter;
                } else {
                    return "(&" + filter + additionalFilter +")" ;
                }
            }
            throw new UnsupportedOperationException("Sorry this is a very limited proxy implementation of Filter");
        });
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
