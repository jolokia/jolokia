package org.jolokia.server.core.osgi;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;

import jakarta.servlet.http.HttpServlet;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.osgi.security.*;
import org.jolokia.server.core.service.api.Restrictor;
import org.jolokia.server.core.util.NetworkUtil;
import org.osgi.framework.*;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.servlet.context.ServletContextHelper;
import org.osgi.service.servlet.whiteboard.HttpWhiteboardConstants;
import org.osgi.util.tracker.ServiceTracker;

import static org.jolokia.server.core.config.ConfigKey.*;

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
 * OSGi Activator for the Jolokia Agent using Whiteboard Servlet specification
 *
 * @author roland
 * @since Dec 27, 2009
 */
public class OsgiAgentActivator implements BundleActivator {

    // Context associated with this activator
    private BundleContext bundleContext;

    // Tracker for ConfigAdmin Service
    private ServiceTracker<ConfigurationAdmin, ConfigurationAdmin> configAdminTracker;

    // Prefix used for configuration values
    private static final String CONFIG_PREFIX = "org.jolokia";

    // Prefix used for ConfigurationAdmin pid
    private static final String CONFIG_ADMIN_PID = "org.jolokia.osgi";

    // ServletContextHelper (Whiteboard) used for authorization
    // (it was an org.osgi.service.http.HttpContext when HttpService specification was used)
    private ServletContextHelper jolokiaContextHelper;

    // Restrictor and associated service tracker when tracking restrictor
    // services
    private Restrictor restrictor = null;

    private ServiceRegistration<ServletContextHelper> contextRegistration = null;
    private ServiceRegistration<HttpServlet> servletRegistration = null;

    /** {@inheritDoc} */
    @SuppressWarnings("deprecation")
    public void start(BundleContext pBundleContext) {
        bundleContext = pBundleContext;

        //Track ConfigurationAdmin service
        configAdminTracker = new ServiceTracker<>(pBundleContext,
                                                "org.osgi.service.cm.ConfigurationAdmin",
                                                null);
        configAdminTracker.open();

        if (Boolean.parseBoolean(getConfiguration(USE_RESTRICTOR_SERVICE))) {
            // If no restrictor is set in the constructor and we are enabled to listen for a restrictor
            // service, a delegating restrictor is installed
            restrictor = new DelegatingRestrictor(bundleContext);
        }

        // Whiteboard servlet
        if (Boolean.parseBoolean(getConfiguration(REGISTER_WHITEBOARD_SERVLET, LISTEN_FOR_HTTP_SERVICE))) {
            registerWhiteboardServlet(pBundleContext);
        }
    }

    /** {@inheritDoc} */
    public void stop(BundleContext pBundleContext) {
        assert pBundleContext.equals(bundleContext);

        if (servletRegistration != null) {
            servletRegistration.unregister();
            contextRegistration.unregister();
        }

        //Shut this down last to make sure nobody calls for a property after this is shutdown
        if (configAdminTracker != null) {
            configAdminTracker.close();
            configAdminTracker = null;
        }

        if (jolokiaContextHelper instanceof ServiceAuthenticationServletContextHelper) {
            final ServiceAuthenticationServletContextHelper context =
                    (ServiceAuthenticationServletContextHelper) jolokiaContextHelper;
            context.close();
        }

        restrictor = null;
        bundleContext = null;
    }

    /**
     * Get the security context for our servlet. Dependent on the configuration,
     * this is either a no-op context or one which authenticates with a given user
     *
     * @return the HttpContext with which the agent servlet gets registered.
     */
    public synchronized ServletContextHelper getServletContextHelper() {
        if (jolokiaContextHelper == null) {
            final String user = getConfiguration(USER);
            final String authMode = getConfiguration(AUTH_MODE);
            if (user != null || "jaas".equalsIgnoreCase(authMode)) {
                 jolokiaContextHelper =
                    new BasicAuthenticationHttpContext(getConfiguration(REALM),
                                                       createAuthenticator(authMode));
            } else if (ServiceAuthenticationServletContextHelper.shouldBeUsed(authMode)) {
                jolokiaContextHelper = new ServiceAuthenticationServletContextHelper(bundleContext, authMode);
            } else {
                jolokiaContextHelper = new DefaultServletContextHelper();
            }
        }
        return jolokiaContextHelper;
    }

    /**
     * Get the servlet context path under which the agent servlet is registered
     * @return get the servlet context path
     */
    public String getServletContextPath() {
        return getConfiguration(AGENT_CONTEXT);
    }

    // ==================================================================================

    // Customizer for registering servlet at a HttpService
    private Dictionary<String,String> getConfiguration() {
        Dictionary<String,String> config = new Hashtable<>();
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

    private String getConfiguration(ConfigKey pKey, ConfigKey fallbackKey) {
        String value = getConfigurationFromConfigAdmin(pKey);
        if (value == null) {
            value = bundleContext.getProperty(CONFIG_PREFIX + "." + pKey.getKeyValue());
        }
        if (value == null) {
            value = getConfiguration(fallbackKey);
        }
        if (value == null) {
            value = pKey.getDefaultValue();
        }
        return value;
    }

    private String getConfigurationFromConfigAdmin(ConfigKey pkey) {
        ConfigurationAdmin configAdmin = configAdminTracker.getService();
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
        Authenticator authenticator;
        try {
            // prefer constructor that takes configuration
            try {
                // TODO: Something wrong here - Configuration vs. Dictionary constructor
                final Constructor<?> constructorThatTakesConfiguration = pAuthClass.getConstructor(Dictionary.class);
                authenticator = (Authenticator) constructorThatTakesConfiguration.newInstance(getConfiguration());
            } catch (NoSuchMethodException e) {
                // Next try
                authenticator = lookupAuthenticatorWithDefaultConstructor(pAuthClass, e);
            } catch (InvocationTargetException e) {
                throw new IllegalArgumentException("Cannot create an instance of custom authenticator class with configuration", e);
            }
        } catch (InstantiationException | IllegalAccessException e) {
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

    private void registerWhiteboardServlet(BundleContext pBundleContext) {
        // register org.osgi.service.servlet.context.ServletContextHelper
        // in 1.x it was a org.osgi.service.http.HttpContext parameter to
        // org.osgi.service.http.HttpService.registerServlet() method
        ServletContextHelper contextHelper = getServletContextHelper();
        String agentContext = getServletContextPath();
        Dictionary<String, Object> properties = new Hashtable<>();
        properties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME, "jolokia");
        properties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH, agentContext);
        contextRegistration = pBundleContext.registerService(ServletContextHelper.class, contextHelper, properties);

        // register servlet
        HttpServlet servlet = new OsgiAgentServlet(pBundleContext, restrictor);
        properties = new Hashtable<>();
        properties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME, "jolokia");
        properties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_ASYNC_SUPPORTED, Boolean.TRUE);
        properties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN, "/*");
        properties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_SELECT,
                String.format("(%s=jolokia)", HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME));
        properties.put("load-on-startup", "0"); // Pax Web specific property
        Dictionary<String, String> config = getConfiguration();
        for (Enumeration<String> e = config.keys(); e.hasMoreElements(); ) {
            String key = e.nextElement();
            properties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_INIT_PARAM_PREFIX + key, config.get(key));
        }
        servletRegistration = pBundleContext.registerService(HttpServlet.class, servlet, properties);
    }

}
