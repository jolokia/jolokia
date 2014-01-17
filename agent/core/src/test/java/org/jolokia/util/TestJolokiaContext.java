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

package org.jolokia.util;

import java.lang.management.ManagementFactory;
import java.util.*;

import javax.management.*;

import org.jolokia.backend.ServerHandle;
import org.jolokia.config.*;
import org.jolokia.converter.Converters;
import org.jolokia.converter.JmxSerializer;
import org.jolokia.discovery.AgentDetails;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.service.JolokiaContext;
import org.jolokia.service.JolokiaService;

/**
 * @author roland
 * @since 19.04.13
 */
public class TestJolokiaContext implements JolokiaContext {

    // Switch on for more debuggin
    private static final boolean DEBUG = false;
    
    Map<Class,SortedSet> services;
    LogHandler logHandler;
    Restrictor restrictor;
    Configuration config;
    ServerHandle handle;
    private AgentDetails agentDetails;
    private Set<ObjectName> mbeans;

    public TestJolokiaContext() {
        this(null,null,null,null,null,null);
        services.put(JmxSerializer.class,new TreeSet<JmxSerializer>(Arrays.asList(new Converters())));
    }

    private TestJolokiaContext(Configuration pConfig,
                               Restrictor pRestrictor,
                               LogHandler pLogHandler,
                               ServerHandle pHandle,
                               Map<Class, SortedSet> pServices,
                               AgentDetails pAgentDetails) {
        this.config = pConfig != null ? pConfig : new StaticConfiguration();
        this.logHandler = pLogHandler != null ? pLogHandler : new StdoutLogHandler(DEBUG);
        this.restrictor = pRestrictor != null ? pRestrictor : new AllowAllRestrictor();
        this.services = pServices != null ? pServices : new HashMap();
        String agentId = pConfig != null ? pConfig.getConfig(ConfigKey.AGENT_ID) : null;
        this.agentDetails = pAgentDetails != null ? pAgentDetails : new AgentDetails(agentId != null ? agentId : UUID.randomUUID().toString());
        if (pHandle != null) {
            handle = pHandle;
        } else {
            handle = new ServerHandle("vendor","product","version");
        }
        mbeans = new HashSet<ObjectName>();
    }

    public void init() {
        for (Class serviceClass : services.keySet()) {
            for (Object jolokiaService : services.get(serviceClass)) {
                ((JolokiaService) jolokiaService).init(this);
            }
        }
    }
    public <T extends JolokiaService> SortedSet<T> getServices(Class<T> pType) {
        SortedSet<T> ret = services.get(pType);
        return ret != null ? new TreeSet<T>(ret) : new TreeSet<T>();
    }

    public <T extends JolokiaService> T getService(Class<T> pType) {
        SortedSet<T> services = getServices(pType);
        return services.size() > 0 ? services.first() : null;
    }

    public ServerHandle getServerHandle() {
        return handle;
    }

    public ObjectName registerMBean(Object pMBean, String... pOptionalName)
            throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
        ObjectInstance instance = mbeanServer.registerMBean(pMBean, pOptionalName.length > 0 ? JmxUtil.newObjectName(pOptionalName[0]) : null);
        mbeans.add(instance.getObjectName());
        return instance.getObjectName();
    }

    public String getConfig(ConfigKey pKey) {
        return config.getConfig(pKey);
    }

    public Set<ConfigKey> getConfigKeys() {
        return config.getConfigKeys();
    }

    public void setServerHandle(ServerHandle pHandle) {
        handle = pHandle;
    }

    public AgentDetails getAgentDetails() {
        return agentDetails;
    }

    public void debug(String message) {
        logHandler.debug(message);
    }

    public void info(String message) {
        logHandler.info(message);
    }

    public void error(String message, Throwable t) {
        logHandler.error(message,t);
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

    public boolean isCorsAccessAllowed(String pOrigin) {
        return restrictor.isCorsAccessAllowed(pOrigin);
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

        private LogHandler logHandler;
        private Restrictor restrictor;
        private Configuration config;
        private ServerHandle handle;
        private Map<Class,SortedSet> services = new HashMap<Class, SortedSet>();
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

        public Builder serverHandle(ServerHandle pServerHandle) {
            this.handle = pServerHandle;
            return this;
        }

        public Builder logHandler(LogHandler pLogHandler) {
            logHandler = pLogHandler;
            return this;
        }

        public Builder agentDetails(AgentDetails pAgentDetails) {
            agentDetails = pAgentDetails;
            return this;
        }
        public <T extends JolokiaService> Builder services(Class<T> pType, T ... pServices) {
            SortedSet<T> serviceSet = new TreeSet(Arrays.asList(pServices));
            services.put(pType, serviceSet);
            return this;
        }

        public <T extends JolokiaService> Builder services(Class<T> pType, SortedSet<T> pServices) {
            services.put(pType, pServices);
            return this;
        }

        public TestJolokiaContext build() {
            return new TestJolokiaContext(
                    config,
                    restrictor,
                    logHandler,
                    handle,
                    services,
                    agentDetails
            );
        }

    }

}
