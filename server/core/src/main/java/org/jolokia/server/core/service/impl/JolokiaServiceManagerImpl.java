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

package org.jolokia.server.core.service.impl;

import java.security.PrivilegedAction;
import java.util.*;
import java.util.stream.*;

import javax.management.*;
import javax.security.auth.Subject;

import org.jolokia.json.JSONObject;
import org.jolokia.server.core.auth.JolokiaAgentPrincipal;
import org.jolokia.server.core.backend.MBeanServerHandler;
import org.jolokia.server.core.backend.MBeanServerHandlerMBean;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.Configuration;
import org.jolokia.server.core.detector.*;
import org.jolokia.server.core.service.api.*;
import org.jolokia.server.core.service.container.ContainerLocator;
import org.jolokia.server.core.service.request.RequestHandler;
import org.jolokia.server.core.service.request.RequestInterceptor;
import org.jolokia.server.core.util.DebugStore;
import org.jolokia.server.core.util.jmx.DefaultMBeanServerAccess;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.json.parser.JSONParser;

/**
 * The service manager for handling all the service organisation stuff.
 *
 * @author roland
 * @since 28.03.13
 */
public class JolokiaServiceManagerImpl implements JolokiaServiceManager {

    // Details of this agent
    private AgentDetails agentDetails;

    // Lookup for finding detectors
    private final ServerDetectorLookup detectorLookup;

    // Overall configuration
    private final Configuration configuration;

    // Logger to use
    private final LogHandler logHandler;

    // Restrictor to use
    private final Restrictor restrictor;

    // Whether this service manager is already initialized
    private boolean isInitialized;

    // Order in which services get initialized
    private static final Class<?>[] SERVICE_TYPE_ORDER =
            new Class[] { ServerDetector.class, RequestHandler.class};

    // All service factories used
    private final List<JolokiaServiceLookup> serviceLookups;

    // Instantiated services, categorized by type and ordered;
    private final Map<Class<? extends JolokiaService<?>>, SortedSet<? extends JolokiaService<?>>> staticServices;

    // The lowest order service registered - includes lowest order (highest priority) services from staticServices
    private final Map<Class<? extends JolokiaService<?>>, JolokiaService<?>> staticLowServices;

    // Jolokia context connecting to this manager
    private JolokiaContextImpl jolokiaContext;

    // MBean registry for holding MBeans
    private MBeanRegistry mbeanRegistry;

    // Access for JMX MBeanServers
    private DefaultMBeanServerAccess mbeanServerAccess;

    private final DebugStore debugStore;

    private ObjectName mBeanServerHandlerName;

    /**
     * Set of enabled services (by class name) narrowing down detected services to explicitly configured ones.
     */
    private Set<String> enabledServices;

    /**
     * Set of disabled services (by class name) narrowing down detected services to all <em>but</em> the ones
     * disabled. Has higher priority than {@link #enabledServices}.
     */
    private Set<String> disabledServices;

    /**
     * Create the implementation of a service manager
     *
     * @param pConfig configuration to use
     * @param pLogHandler the logger
     * @param pRestrictor restrictor to apply
     * @param pDetectorLookup additional lookup server detectors when the services start
     *                        (in addition to the classpath based lookup) These detectors while have a higher
     *                        precedence than the classpath based lookup. Might be null.
     */
    public JolokiaServiceManagerImpl(Configuration pConfig,
                                     LogHandler pLogHandler,
                                     Restrictor pRestrictor,
                                     ServerDetectorLookup pDetectorLookup) {
        configuration = pConfig;
        logHandler = pLogHandler;
        restrictor = pRestrictor;
        isInitialized = false;
        serviceLookups = new ArrayList<>();
        staticServices = new HashMap<>();
        staticLowServices = new HashMap<>();
        detectorLookup = pDetectorLookup != null ? pDetectorLookup : new ClasspathServerDetectorLookup();
        // The version request handler must be always present and always be first
        addService(new VersionRequestHandler());
        // DebugStore is also a service, so we can integrate it with JolokiaContext
        debugStore = new DebugStore();
        addService(debugStore);

        // prepare configuration of enabled/disabled services - even if they may be added later before start()
        configureEnabledServices();

        // user may call addServices(JolokiaServiceCreator) now before calling start()
    }

    /**
     * Get the overall configuration
     *
     * @return configuration
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Get the loghandler used for logging
     *
     * @return loghandler
     */
    public LogHandler getLogHandler() {
        return logHandler;
    }

    /**
     * Get the restrictor
     *
     * @return restrictor
     */
    public Restrictor getRestrictor() {
        return restrictor;
    }

    /** {@inheritDoc} */
    public final synchronized void addService(JolokiaService<?> pService) {
        Class<? extends JolokiaService<?>> type = pService.getType();
        @SuppressWarnings("unchecked")
        SortedSet<JolokiaService<?>> servicesOfType = (SortedSet<JolokiaService<?>>) staticServices.get(type);
        if (servicesOfType == null) {
            servicesOfType = new TreeSet<>();
            staticServices.put(type, servicesOfType);
        }
        servicesOfType.add(pService);
        JolokiaService<?> pLowService = staticLowServices.get(type);
        if (pLowService == null || pLowService.getOrder() > pService.getOrder()) {
            staticLowServices.put(type,pService);
        }
    }

    /** {@inheritDoc} */
    public void addServiceLookup(JolokiaServiceLookup pLookup) {
        serviceLookups.add(pLookup);
    }

    /** {@inheritDoc} */
    public void addServices(JolokiaServiceCreator pServiceCreator) {
        for (JolokiaService<?> service : pServiceCreator.getServices(logHandler)) {
            addService(service);
        }
    }

    /** {@inheritDoc} */
    public synchronized JolokiaContext start() {
        if (!isInitialized) {
            ServerHandle handle = Subject.doAs(JolokiaAgentPrincipal.asSubject(), new PrivilegedAction<>() {
                @Override
                public ServerHandle run() {
                    SortedSet<ServerDetector> detectors = new TreeSet<>();
                    if (!Boolean.parseBoolean(configuration.getConfig(ConfigKey.DISABLE_DETECTORS))) {
                        detectors = detectorLookup.lookup(logHandler);
                    }
                    mbeanServerAccess = createMBeanServerAccess(detectors);
                    return detect(getDetectorOptions(), detectors, mbeanServerAccess);
                }
            });
            agentDetails = new AgentDetails(configuration,handle);

            // Create context and remember
            jolokiaContext = new JolokiaContextImpl(this);
            jolokiaContext.setDebugStore(debugStore);

            // Create the MBean registry
            mbeanRegistry = new MBeanRegistry();

            // register jolokia:type=ServerHandler
            MBeanServerHandler mBeanServerHandler = new MBeanServerHandler(mbeanServerAccess);
            try {
                mBeanServerHandlerName = new ObjectName(MBeanServerHandlerMBean.OBJECT_NAME + ",agent=" + getAgentDetails().getAgentId());
                jolokiaContext.registerMBean(mBeanServerHandler, mBeanServerHandlerName.toString());
            } catch (JMException e) {
                jolokiaContext.error("Cannot register MBean " + mBeanServerHandlerName + ": " + e, e);
            }

            // Initialize all services in the proper order
            List<Class<? extends JolokiaService<?>>> serviceTypes = getServiceTypes();
            for (Iterator<Class<? extends JolokiaService<?>>> it1 = serviceTypes.iterator(); it1.hasNext(); ) {
                Class<? extends JolokiaService<?>> serviceType = it1.next();
                // Initialize services
                Set<? extends JolokiaService<?>> services = staticServices.get(serviceType);
                if (services != null) {
                    for (Iterator<? extends JolokiaService<?>> it2 = services.iterator(); it2.hasNext(); ) {
                        JolokiaService<?> service = it2.next();
                        if (service.isEnabled(jolokiaContext)) {
                            service.init(jolokiaContext);
                        } else {
                            it2.remove();
                        }
                    }
                    if (services.isEmpty()) {
                        it1.remove();
                    }
                }
            }
            staticLowServices.values().removeIf(value -> !isServiceEnabled(value.getClass().getName()));

            // All dynamic service factories are initialized as well. The factory itself is responsible
            // for initializing any new services coming in with the JolokiaContext
            // for now, only OSGi allows such dynamic services
            for (JolokiaServiceLookup lookup : serviceLookups) {
                lookup.init(jolokiaContext);
            }

            isInitialized = true;
        }
        return jolokiaContext;
    }

    /** {@inheritDoc} */
    public synchronized void stop() {
        Subject.doAs(JolokiaAgentPrincipal.asSubject(), new PrivilegedAction<>() {
            @Override
            public Object run() {
                stopInternal();
                return null;
            }
        });
    }

    private synchronized void stopInternal() {
        if (isInitialized) {
            try {
                mbeanRegistry.destroy();
            } catch (JMException e) {
                logHandler.error("Cannot unregister own MBeans: " + e, e);
            }
            if (mBeanServerHandlerName != null) {
                try {
                    jolokiaContext.unregisterMBean(mBeanServerHandlerName);
                } catch (MBeanRegistrationException ignored) {
                }
            }
            for (JolokiaServiceLookup factory : serviceLookups) {
                factory.destroy();
            }
            for (Class<? extends JolokiaService<?>> serviceType : getServiceTypes()) {
                Set<? extends JolokiaService<?>> services = staticServices.get(serviceType);
                if (services != null) {
                    for (JolokiaService<?> service : services) {
                        try {
                            service.destroy();
                        } catch (Exception e) {
                            logHandler.error("Error while stopping service " + service + " of type " + service.getType() + ": " + e,e);
                        }
                    }

                }
            }
            isInitialized = false;
        }
    }

    /**
     * Get all services of a certain type currently registered. Static services
     * are returned directly, for dynamic services a lookup to the service factory is
     * performed.
     *
     * @param pType service type to fetch
     * @return set of services detected or an empty list
     */
    public <T extends JolokiaService<?>> SortedSet<T> getServices(Class<T> pType) {
        @SuppressWarnings("unchecked")
        SortedSet<T> services = (SortedSet<T>) staticServices.get(pType);
        SortedSet<T> ret = services != null ? new TreeSet<>(services) : new TreeSet<>();
        for (JolokiaServiceLookup factory : serviceLookups) {
            ret.addAll(factory.getServices(pType));
        }
        return ret;
    }

    /**
     * Get a single service. If more than one service of the given type has been
     * registered, return the one with the lowest order (highest priority). If no one has been registered
     * return <code>null</code>
     *
     * @param pType requested service type
     * @return the requested service or null if none has been registered
     */
    public <T extends JolokiaService<?>> T getService(Class<T> pType) {
        @SuppressWarnings("unchecked")
        T ret = (T) staticLowServices.get(pType);
        int order = ret != null ? ret.getOrder() : Integer.MAX_VALUE;
        for (JolokiaServiceLookup factory : serviceLookups) {
            for (T service : factory.getServices(pType)) {
                if (service.getOrder() < order) {
                    ret = service;
                    order = ret.getOrder();
                }
            }
        }
        return ret;
    }

    /** {@inheritDoc} */
    public boolean isServiceEnabled(String serviceClassName) {
        if (disabledServices != null) {
            return !disabledServices.contains(serviceClassName);
        } else if (enabledServices != null) {
            return enabledServices.contains(serviceClassName);
        }
        return true;
    }

    // Access to merged MBean servers
    MBeanServerAccess getMBeanServerAccess() {
        return mbeanServerAccess;
    }


    // =============================================================================================================


    @SuppressWarnings("unchecked")
    private ServerHandle detect(Map<String,Object> pConfig, SortedSet<ServerDetector> detectors, MBeanServerAccess pMBeanServerAccess) {
        for (ServerDetector detector : detectors) {
            try {
                detector.init((Map<String, Object>) pConfig.get(detector.getName()));
                ServerHandle info = detector.detect(pMBeanServerAccess);
                if (info != null) {
                    addInterceptor(detector,pMBeanServerAccess);
                    addRuntimeLocator(detector);
                    return info;
                }
            } catch (Exception exp) {
                // We are defensive here and wont stop the agent because
                // there is a problem with the server detection. A error will be logged
                // nevertheless, though.
                logHandler.error("Error while using detector " + detector.getClass().getSimpleName() + ": " + exp,exp);
            }
        }
        return DefaultServerHandle.NULL_SERVER_HANDLE;
    }

    // Add an interceptor if available
    private void addInterceptor(ServerDetector detector,MBeanServerAccess pServerAccess) {
        RequestInterceptor interceptor = detector.getRequestInterceptor(pServerAccess);
        if (interceptor  != null) {
            addService(interceptor);
        }
    }

    // Add a runtime locator service using the _main_ detector
    private void addRuntimeLocator(ServerDetector detector) {
        ContainerLocator locator = detector.getContainerLocator();
        if (locator != null) {
            addService(locator);
        }
    }

    private DefaultMBeanServerAccess createMBeanServerAccess(SortedSet<ServerDetector> pDetectors) {
        Set<MBeanServerConnection> mbeanServers = new HashSet<>();
        for (ServerDetector detector : pDetectors) {
            Set<MBeanServerConnection> found = detector.getMBeanServers();
            if (found != null) {
                mbeanServers.addAll(found);
            }
        }
        return new DefaultMBeanServerAccess(mbeanServers);
    }
    /**
     * Get the optional options used for detectors-default. This should be a JSON string specifying all options
     * for all detectors-default. Keys are the name of the detector's product, the values are JSON object containing
     * specific parameters for this agent. E.g.
     *
     * <pre>
     *    {
     *        "glassfish" : { "bootAmx": true  }
     *    }
     * </pre>
     *
     * @return the detector specific configuration
     */
    private Map<String,Object> getDetectorOptions() {
        String optionString = configuration.getConfig(ConfigKey.DETECTOR_OPTIONS);
        if (optionString != null) {
            try {
                return new JSONParser().parse(optionString, JSONObject.class);
            } catch (Exception e) {
                logHandler.error("Could not parse detetctor options '" + optionString + "' as JSON object: " + e, e);
            }
        } else {
            return Collections.emptyMap();
        }
        return null;
    }


    // Extract the order in which services should be initialized
    @SuppressWarnings("unchecked")
    private List<Class<? extends JolokiaService<?>>> getServiceTypes() {
        List<Class<? extends JolokiaService<?>>> ret = new ArrayList<>();
        for (Class<?> type : SERVICE_TYPE_ORDER) {
            ret.add((Class<? extends JolokiaService<?>>) type);
        }
        for (Class<? extends JolokiaService<?>> staticType : staticServices.keySet()) {
            if (!ret.contains(staticType)) {
                ret.add(staticType);
            }
        }
        return ret;
    }

    /**
     * Return the details of this agent
     * @return the nifty details
     */
    public AgentDetails getAgentDetails() {
        return agentDetails;
    }

    /**
     * Register a MBean under a certain name to the platform MBeanServer.
     *
     * This method delegates to the {@link MBeanRegistry}.
     *
     * @param pMBean MBean to register
     * @param pOptionalName optional name under which the bean should be registered. If not provided,
     * it depends on whether the MBean to register implements {@link javax.management.MBeanRegistration} or
     * not.
     *
     * @return the name under which the MBean is registered.
     */
    public final ObjectName registerMBean(Object pMBean, String... pOptionalName)
            throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        return mbeanRegistry.registerMBean(pMBean, pOptionalName);
    }

    /**
     * Unregister an MBean which has been registered formerly via the registry
     *
     * @param pObjectName MBean to unregister
     */
    public final void unregisterMBean(ObjectName pObjectName) throws MBeanRegistrationException {
        mbeanRegistry.unregisterMBean(pObjectName);
    }

    private void configureEnabledServices() {
        String enabledServices = configuration.getConfig(ConfigKey.ENABLED_SERVICES);
        String disabledServices = configuration.getConfig(ConfigKey.DISABLED_SERVICES);

        if (disabledServices != null && !disabledServices.trim().isEmpty()) {
            this.disabledServices = Arrays.stream(disabledServices.split("\\s*,\\s*"))
                .map(String::trim).collect(Collectors.toUnmodifiableSet());
        } else if (enabledServices != null && !enabledServices.trim().isEmpty()) {
            this.enabledServices = Arrays.stream(enabledServices.split("\\s*,\\s*"))
                .map(String::trim).collect(Collectors.toUnmodifiableSet());
        }
    }

}
