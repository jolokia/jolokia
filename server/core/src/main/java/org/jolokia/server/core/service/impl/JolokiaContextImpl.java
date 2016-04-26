package org.jolokia.server.core.service.impl;

import java.util.Set;
import java.util.SortedSet;

import javax.management.*;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.config.Configuration;
import org.jolokia.server.core.service.api.*;
import org.jolokia.server.core.util.HttpMethod;
import org.jolokia.server.core.util.RequestType;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;

/**
 * Central implementation of the {@link JolokiaContext}
 *
 * @author roland
 * @since 09.04.13
 */
public class JolokiaContextImpl implements JolokiaContext {

    // Service manager associated with the context
    private JolokiaServiceManagerImpl serviceManager;

    /**
     * New context associated with the given service manager
     *
     * @param pServiceManager service manager, used later for looking up services
     */
    JolokiaContextImpl(JolokiaServiceManagerImpl pServiceManager) {
        serviceManager = pServiceManager;
    }

    /** {@inheritDoc} */
    public ObjectName registerMBean(Object pMBean, String... pOptionalName)
            throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        return serviceManager.registerMBean(pMBean,pOptionalName);
    }

    /** {@inheritDoc} */
    public void unregisterMBean(ObjectName pObjectName) throws MBeanRegistrationException {
        serviceManager.unregisterMBean(pObjectName);
    }

    /** {@inheritDoc} */
    public MBeanServerAccess getMBeanServerAccess() {
        return serviceManager.getMBeanServerAccess();
    }

    /** {@inheritDoc} */
    public AgentDetails getAgentDetails() {
        return serviceManager.getAgentDetails();
    }

    /** {@inheritDoc} */
    public String getConfig(ConfigKey pOption) {
        return getConfiguration().getConfig(pOption);
    }

    /** {@inheritDoc} */
    public Set<ConfigKey> getConfigKeys() {
        return getConfiguration().getConfigKeys();
    }

    /** {@inheritDoc} */
    public <T extends JolokiaService> SortedSet<T> getServices(Class<T> pType) {
        return serviceManager.getServices(pType);
    }

    /** {@inheritDoc} */
    public <T extends JolokiaService> T getService(Class<T> pType) {
        return serviceManager.getService(pType);
    }

    /** {@inheritDoc} */
    public <T extends JolokiaService> T getMandatoryService(Class<T> pType) {
        T service = serviceManager.getService(pType);
        if (service == null) {
            throw new IllegalStateException("No service of type " + pType + " found");
        }
        return service;
    }

    /** {@inheritDoc} */
    public boolean isDebug() {
        return getLog().isDebug();
    }

    /** {@inheritDoc} */
    public void debug(String message) {
        getLog().debug(message);
    }

    /** {@inheritDoc} */
    public void info(String message) {
        getLog().info(message);
    }

    /** {@inheritDoc} */
    public void error(String message, Throwable t) {
        getLog().error(message, t);
    }

    /** {@inheritDoc} */
    public boolean isHttpMethodAllowed(HttpMethod pMethod) {
        return getRestrictor().isHttpMethodAllowed(pMethod);
    }

    /** {@inheritDoc} */
    public boolean isTypeAllowed(RequestType pType) {
        return getRestrictor().isTypeAllowed(pType);
    }

    /** {@inheritDoc} */
    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return getRestrictor().isAttributeReadAllowed(pName, pAttribute);
    }

    /** {@inheritDoc} */
    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return getRestrictor().isAttributeWriteAllowed(pName, pAttribute);
    }

    /** {@inheritDoc} */
    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return getRestrictor().isOperationAllowed(pName, pOperation);
    }

    /** {@inheritDoc} */
    public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
        return getRestrictor().isRemoteAccessAllowed(pHostOrAddress);
    }

    /** {@inheritDoc} */
    public boolean isOriginAllowed(String pOrigin,boolean pStrictCheck) {
        return getRestrictor().isOriginAllowed(pOrigin,pStrictCheck);
    }

    /** {@inheritDoc} */
    private Configuration getConfiguration() {
        return serviceManager.getConfiguration();
    }

    /** {@inheritDoc} */
    private Restrictor getRestrictor() {
        return serviceManager.getRestrictor();
    }

    /** {@inheritDoc} */
    private LogHandler getLog() {
        return serviceManager.getLogHandler();
    }
}
