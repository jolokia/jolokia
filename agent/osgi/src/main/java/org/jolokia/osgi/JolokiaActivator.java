package org.jolokia.osgi;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Dictionary;
import java.util.Hashtable;

import javax.servlet.ServletException;

import org.jolokia.config.ConfigKey;
import org.jolokia.osgi.security.*;
import org.jolokia.osgi.servlet.JolokiaContext;
import org.jolokia.osgi.servlet.JolokiaServlet;
import org.jolokia.osgi.util.LogHelper;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.NetworkUtil;
import org.osgi.framework.*;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.http.*;
import org.osgi.util.tracker.ServiceTracker;
import org.osgi.util.tracker.ServiceTrackerCustomizer;

import static org.jolokia.config.ConfigKey.*;

/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


/**
 * OSGi Activator for the Jolokia Agent
 *
 * @author roland
 * @since Dec 27, 2009
 */
public class JolokiaActivator implements BundleActivator, JolokiaContext {

    // Base filter to use for filtering out HttpServices
    public static final String HTTP_SERVICE_FILTER_BASE =
            "(" + Constants.OBJECTCLASS + "=" + HttpService.class.getName() + ")";

    // Context associated with this activator
    private BundleContext bundleContext;

    // Tracker for HttpService
    private ServiceTracker httpServiceTracker;

    // Tracker for ConfigAdmin Service
    private ServiceTracker configAdminTracker;

    // Prefix used for configuration values
    private static final String CONFIG_PREFIX = "org.jolokia";

    // Prefix used for ConfigurationAdmin pid
    private static final String CONFIG_ADMIN_PID = "org.jolokia.osgi";

    // HttpContext used for authorization
    private HttpContext jolokiaHttpContext;

    // Registration object for this JolokiaContext
    private ServiceRegistration jolokiaServiceRegistration;

    // Restrictor and associated service tracker when tracking restrictor
    // services
    private Restrictor restrictor = null;

    /** {@inheritDoc} */
    public void start(BundleContext pBundleContext) {
        bundleContext = pBundleContext;

        //Track ConfigurationAdmin service
        configAdminTracker = new ServiceTracker(pBundleContext,
                                                "org.osgi.service.cm.ConfigurationAdmin",
                                                null);
        configAdminTracker.open();

        if (Boolean.parseBoolean(getConfiguration(USE_RESTRICTOR_SERVICE))) {
            // If no restrictor is set in the constructor and we are enabled to listen for a restrictor
            // service, a delegating restrictor is installed
            restrictor = new DelegatingRestrictor(bundleContext);
        }

        // Track HttpService
        if (Boolean.parseBoolean(getConfiguration(LISTEN_FOR_HTTP_SERVICE))) {
            httpServiceTracker = new ServiceTracker(pBundleContext,
                                                    buildHttpServiceFilter(pBundleContext),
                                                    new HttpServiceCustomizer(pBundleContext));
            httpServiceTracker.open();

            // Register us as JolokiaContext
            jolokiaServiceRegistration = pBundleContext.registerService(JolokiaContext.class.getCanonicalName(), this, null);
        }
    }

    /** {@inheritDoc} */
    public void stop(BundleContext pBundleContext) {
        assert pBundleContext.equals(bundleContext);

        if (httpServiceTracker != null) {
            // Closing the tracker will also call {@link HttpServiceCustomizer#removedService()}
            // for every active service which in turn unregisters the servlet
            httpServiceTracker.close();
            httpServiceTracker = null;
        }

        if (jolokiaServiceRegistration != null) {
            jolokiaServiceRegistration.unregister();
            jolokiaServiceRegistration = null;
        }

        //Shut this down last to make sure nobody calls for a property after this is shutdown
        if (configAdminTracker != null) {
            configAdminTracker.close();
            configAdminTracker = null;
        }

        if (jolokiaHttpContext instanceof ServiceAuthenticationHttpContext) {
            final ServiceAuthenticationHttpContext context =
                    (ServiceAuthenticationHttpContext) jolokiaHttpContext;
            context.close();
        }

        restrictor = null;
        bundleContext = null;
    }

    /**
     * Get the security context for out servlet. Dependent on the configuration,
     * this is either a no-op context or one which authenticates with a given user
     *
     * @return the HttpContext with which the agent servlet gets registered.
     */
    public synchronized HttpContext getHttpContext() {
        if (jolokiaHttpContext == null) {
            final String user = getConfiguration(USER);
            final String authMode = getConfiguration(AUTH_MODE);
            if (user != null || "jaas".equalsIgnoreCase(authMode)) {
                 jolokiaHttpContext =
                    new BasicAuthenticationHttpContext(getConfiguration(REALM),
                                                       createAuthenticator(authMode));
            } else if (ServiceAuthenticationHttpContext.shouldBeUsed(authMode)) {
                jolokiaHttpContext = new ServiceAuthenticationHttpContext(bundleContext, authMode);
            } else {
                jolokiaHttpContext = new DefaultHttpContext();
            }
        }
        return jolokiaHttpContext;
    }

    /**
     * Get the servlet alias under which the agent servlet is registered
     * @return get the servlet alias
     */
    public String getServletAlias() {
        return getConfiguration(AGENT_CONTEXT);
    }

    // ==================================================================================

    // Customizer for registering servlet at a HttpService
    private Dictionary<String,String> getConfiguration() {
        Dictionary<String,String> config = new Hashtable<String,String>();
        for (ConfigKey key : ConfigKey.values()) {
            String value = getConfiguration(key);
            if (value != null) {
                config.put(key.getKeyValue(),value);
            }
        }
        String jolokiaId = NetworkUtil.replaceExpression(config.get(ConfigKey.AGENT_ID.getKeyValue()));
        if (jolokiaId == null) {
            config.put(ConfigKey.AGENT_ID.getKeyValue(),
                       NetworkUtil.getAgentId(hashCode(),"osgi"));
        }
        config.put(ConfigKey.AGENT_TYPE.getKeyValue(),"osgi");
        return config;
    }


    private String getConfiguration(ConfigKey pKey) {
        // TODO: Use fragments if available.
        String value = getConfigurationFromConfigAdmin(pKey);
        if (value == null) {
            value = bundleContext.getProperty(CONFIG_PREFIX + "." + pKey.getKeyValue());
        }
        if (value == null) {
            value = pKey.getDefaultValue();
        }
        return value;
    }

    private String getConfigurationFromConfigAdmin(ConfigKey pkey) {
        ConfigurationAdmin configAdmin = (ConfigurationAdmin) configAdminTracker.getService();
        if (configAdmin == null) {
            return null;
        }
        try {
            Configuration config = configAdmin.getConfiguration(CONFIG_ADMIN_PID);
            if (config == null) {
                return null;
            }
            Dictionary<?, ?> props = config.getProperties();
            if (props == null) {
                return null;
            }
            return (String) props.get(CONFIG_PREFIX + "." + pkey.getKeyValue());
        } catch (IOException e) {
            return null;
        }
    }

    private Filter buildHttpServiceFilter(BundleContext pBundleContext) {
        String customFilter = getConfiguration(ConfigKey.HTTP_SERVICE_FILTER);
        String filter = customFilter != null && customFilter.trim().length() > 0 ?
                "(&" + HTTP_SERVICE_FILTER_BASE + customFilter + ")" :
                HTTP_SERVICE_FILTER_BASE;
        try {
            return pBundleContext.createFilter(filter);
        } catch (InvalidSyntaxException e) {
            throw new IllegalArgumentException("Unable to parse filter " + filter,e);
        }
    }

    private Authenticator createAuthenticator(String authMode) {
        Authenticator authenticator = createCustomAuthenticator();
        if (authenticator != null) {
            return authenticator;
        }
        return createAuthenticatorFromAuthMode(authMode);
    }

    private Authenticator createCustomAuthenticator() {
        final String authenticatorClass = getConfiguration(ConfigKey.AUTH_CLASS);
        if (authenticatorClass != null) {
            try {
                Class<?> authClass = Class.forName(authenticatorClass);
                if (!Authenticator.class.isAssignableFrom(authClass)) {
                    throw new IllegalArgumentException("Provided authenticator class [" + authenticatorClass +
                                                       "] is not a subclass of Authenticator");
                }
                return lookupAuthenticator(authClass);
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException("Cannot find authenticator class", e);
            }
        }
        return null;
    }

    private Authenticator lookupAuthenticator(final Class<?> pAuthClass) {
        Authenticator authenticator = null;
        try {
            // prefer constructor that takes configuration
            try {
                final Constructor<?> constructorThatTakesConfiguration = pAuthClass.getConstructor(Configuration.class);
                authenticator = (Authenticator) constructorThatTakesConfiguration.newInstance(getConfiguration());
            } catch (NoSuchMethodException ignore) {
                // Next try
                authenticator = lookupAuthenticatorWithDefaultConstructor(pAuthClass, ignore);
            } catch (InvocationTargetException e) {
                throw new IllegalArgumentException("Cannot create an instance of custom authenticator class with configuration", e);
            }
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("Cannot create an instance of custom authenticator class", e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot create an instance of custom authenticator class", e);
        }
        return authenticator;
    }

    private Authenticator lookupAuthenticatorWithDefaultConstructor(final Class<?> pAuthClass, final NoSuchMethodException ignore)
            throws InstantiationException, IllegalAccessException {

        // fallback to default constructor
        try {
            final Constructor<?> defaultConstructor = pAuthClass.getConstructor();
            return (Authenticator) defaultConstructor.newInstance();
        } catch (NoSuchMethodException e) {
            e.initCause(ignore);
            throw new IllegalArgumentException("Cannot create an instance of custom authenticator class, no default constructor to use", e);
        } catch (InvocationTargetException e) {
            e.initCause(ignore);
            throw new IllegalArgumentException("Cannot create an instance of custom authenticator using default constructor", e);
        }
    }

    private Authenticator createAuthenticatorFromAuthMode(String pAuthMode) {
        if ("basic".equalsIgnoreCase(pAuthMode)) {
            return new BasicAuthenticator(getConfiguration(USER),getConfiguration(PASSWORD));
        } else if ("jaas".equalsIgnoreCase(pAuthMode)) {
            return new JaasAuthenticator(getConfiguration(REALM));
        } else {
            throw new IllegalArgumentException("Unknown authentication method '" + pAuthMode + "' configured");
        }
    }

    // =============================================================================

    private class HttpServiceCustomizer implements ServiceTrackerCustomizer {
        private final BundleContext context;

        HttpServiceCustomizer(BundleContext pContext) {
            context = pContext;
        }

        /** {@inheritDoc} */
        public Object addingService(ServiceReference reference) {
            HttpService service = (HttpService) context.getService(reference);
            try {
                service.registerServlet(getServletAlias(),
                                        new JolokiaServlet(context,restrictor),
                                        getConfiguration(),
                                        getHttpContext());
            } catch (ServletException e) {
                LogHelper.logError(bundleContext, "Servlet Exception: " + e, e);
            } catch (NamespaceException e) {
                LogHelper.logError(bundleContext, "Namespace Exception: " + e, e);
            }
            return service;
        }

        /** {@inheritDoc} */
        public void modifiedService(ServiceReference reference, Object service) {
        }

        /** {@inheritDoc} */
        public void removedService(ServiceReference reference, Object service) {
            HttpService httpService = (HttpService) service;
            httpService.unregister(getServletAlias());
        }
    }

}
