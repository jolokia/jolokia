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

package org.jolokia.server.core.util;

import java.lang.management.ManagementFactory;
import java.util.*;

import javax.management.*;

import org.jolokia.server.core.config.*;
import org.jolokia.server.core.restrictor.AllowAllRestrictor;
import org.jolokia.server.core.service.api.*;
import org.jolokia.server.core.service.impl.StdoutLogHandler;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.server.core.util.jmx.*;

/**
 * @author roland
 * @since 19.04.13
 */
public class TestJolokiaContext implements JolokiaContext {

    // Switch on for more debugging
    private static final boolean DEBUG = false;

    Map<Class<?>, SortedSet<?>> services;
    org.jolokia.core.api.LogHandler logHandler;
    Restrictor restrictor;
    Configuration config;
    ServerHandle handle;
    private final AgentDetails agentDetails;
    private final SecurityDetails securityDetails;
    private final Set<ObjectName> mbeans;
    private final MBeanServerAccess mBeanServerAccess;

    public TestJolokiaContext() {
        this(null,null,null,null,null);
        services.put(Serializer.class,new TreeSet<Serializer>(Collections.singletonList(new TestSerializer())));
    }

    private TestJolokiaContext(Configuration pConfig,
                               Restrictor pRestrictor,
                               org.jolokia.core.api.LogHandler pLogHandler,
                               Map<Class<?>, SortedSet<?>> pServices,
                               AgentDetails pAgentDetails) {
        this.config = pConfig != null ? pConfig : new StaticConfiguration();
        this.logHandler = pLogHandler != null ? pLogHandler : new StdoutLogHandler(DEBUG);
        this.restrictor = pRestrictor != null ? pRestrictor : new AllowAllRestrictor();
        this.services = pServices != null ? pServices : new HashMap<>();
        String agentId = pConfig != null ? pConfig.getConfig(ConfigKey.AGENT_ID) : null;
        this.agentDetails = pAgentDetails != null ? pAgentDetails : new AgentDetails(agentId != null ? agentId : UUID.randomUUID().toString());
        this.securityDetails = new SecurityDetails();
        mbeans = new HashSet<>();
        mBeanServerAccess = new SingleMBeanServerAccess(ManagementFactory.getPlatformMBeanServer());
    }

    public void init() {
        for (Class<?> serviceClass : services.keySet()) {
            for (Object jolokiaService : services.get(serviceClass)) {
                ((JolokiaService<?>) jolokiaService).init(this);
            }
        }
    }

    public Map<Class<?>, SortedSet<?>> getServices() {
        return services;
    }

    public <T extends JolokiaService<?>> SortedSet<T> getServices(Class<T> pType) {
        @SuppressWarnings("unchecked")
        SortedSet<T> ret = (SortedSet<T>) services.get(pType);
        return ret != null ? new TreeSet<>(ret) : new TreeSet<>();
    }

    public <T extends JolokiaService<?>> T getService(Class<T> pType) {
        SortedSet<T> services = getServices(pType);
        return !services.isEmpty() ? services.first() : null;
    }

    public <T extends JolokiaService<?>> T getMandatoryService(Class<T> pType) {
        SortedSet<T> services = getServices(pType);
        if (services.size() > 1) {
            throw new IllegalStateException("More than one service of type " + pType + ": " + services);
        } else if (services.isEmpty()) {
            throw new IllegalStateException("No service of type " + pType);
        }
        return services.first();
    }

    public ObjectName registerMBean(Object pMBean, String... pOptionalName)
            throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        ObjectInstance instance = mbeanServer.registerMBean(pMBean, pOptionalName.length > 0 ? JmxUtil.newObjectName(pOptionalName[0]) : null);
        mbeans.add(instance.getObjectName());
        return instance.getObjectName();
    }

    public void unregisterMBean(ObjectName pObjectName) throws MBeanRegistrationException {
        if (mbeans.contains(pObjectName)) {
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            try {
                mbeanServer.unregisterMBean(pObjectName);
                mbeans.remove(pObjectName);
            } catch (InstanceNotFoundException e) {
                throw new IllegalStateException("No MBean " + pObjectName + " registered",e);
            }
        }
    }


    public MBeanServerAccess getMBeanServerAccess() {
        return mBeanServerAccess;
    }

    public String getConfig(ConfigKey pKey) {
        return config.getConfig(pKey);
    }

    public Set<ConfigKey> getConfigKeys() {
        return config.getConfigKeys();
    }

    public AgentDetails getAgentDetails() {
        return agentDetails;
    }

    @Override
    public SecurityDetails getSecurityDetails() {
        return securityDetails;
    }

    public void debug(String message) {
        logHandler.debug(message);
    }

    public void info(String message) {
        logHandler.info(message);
    }

    public void error(String message, Throwable t) {
        logHandler.error(message, t);
    }

    public boolean isDebug() {
        return logHandler.isDebug();
    }

    public boolean isHttpMethodAllowed(HttpMethod pMethod) {
        return restrictor.isHttpMethodAllowed(pMethod);
    }

    public boolean isTypeAllowed(RequestType pType) {
        return restrictor.isTypeAllowed(pType);
    }

    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return restrictor.isAttributeReadAllowed(pName, pAttribute);
    }

    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return restrictor.isAttributeWriteAllowed(pName, pAttribute);
    }

    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return restrictor.isOperationAllowed(pName, pOperation);
    }

    public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
        return restrictor.isRemoteAccessAllowed(pHostOrAddress);
    }

    public boolean isOriginAllowed(String pOrigin,boolean pIsStrictCheck) {
        return restrictor.isOriginAllowed(pOrigin,pIsStrictCheck);
    }

    public boolean isObjectNameHidden(ObjectName name) {
        return restrictor.isObjectNameHidden(name);
    }

    public boolean ignoreScheme() {
        return restrictor.ignoreScheme();
    }

    public boolean isServiceEnabled(String serviceClassName) {
        return true;
    }

    @Override
    public String dateFormat() {
        return ConfigKey.DATE_FORMAT.getDefaultValue();
    }

    @Override
    public TimeZone dateFormatTimeZone() {
        return TimeZone.getTimeZone(ConfigKey.DATE_FORMAT_ZONE.getDefaultValue());
    }

    // Should be called when MBeans were registered for the test. Not part of the JolokiaContext interface
    public void destroy() throws MBeanRegistrationException, InstanceNotFoundException {
        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        for (ObjectName objectName : mbeans) {
            mbeanServer.unregisterMBean(objectName);
        }
    }

    // ==================================================================

    public static class Builder {

        private org.jolokia.core.api.LogHandler logHandler;
        private Restrictor restrictor;
        private Configuration config;
        private final Map<Class<?>,SortedSet<?>> services = new HashMap<>();
        private AgentDetails agentDetails;

        public Builder config(Configuration config) {
            this.config = config;
            return this;
        }

        public Builder config(Object... keyAndValues) {
            this.config = new StaticConfiguration(keyAndValues);
            return this;
        }

        public Builder restrictor(Restrictor pRestrictor) {
            this.restrictor = pRestrictor;
            return this;
        }

        public Builder logHandler(org.jolokia.core.api.LogHandler pLogHandler) {
            logHandler = pLogHandler;
            return this;
        }

        public Builder agentDetails(AgentDetails pAgentDetails) {
            agentDetails = pAgentDetails;
            return this;
        }

        @SafeVarargs
        public final <T extends JolokiaService<?>> Builder services(Class<T> pType, T... pServices) {
            SortedSet<T> serviceSet = new TreeSet<>(Arrays.asList(pServices));
            services.put(pType, serviceSet);
            return this;
        }

        public <T extends JolokiaService<?>> Builder services(Class<T> pType, SortedSet<T> pServices) {
            services.put(pType, pServices);
            return this;
        }

        public TestJolokiaContext build() {
            return new TestJolokiaContext(
                    config,
                    restrictor,
                    logHandler,
                    services,
                    agentDetails
            );
        }

    }

}
