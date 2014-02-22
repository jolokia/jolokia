package org.jolokia.service.impl;

import java.util.Set;
import java.util.SortedSet;

import javax.management.*;

import org.jolokia.service.ServerHandle;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.service.AgentDetails;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.service.JolokiaContext;
import org.jolokia.service.JolokiaService;
import org.jolokia.util.*;

/**
 * Central implementation of the {@link JolokiaContext}
 *
 * @author roland
 * @since 09.04.13
 */
public class JolokiaContextImpl implements JolokiaContext {

    // Server handle
    private ServerHandle serverHandle;

    // Service manager associated with the context
    private JolokiaServiceManagerImpl serviceManager;

    /**
     * New context associated with the given service manager
     *
     * @param pServiceManager service manager, used later for looking up services
     */
    JolokiaContextImpl(JolokiaServiceManagerImpl pServiceManager) {
        serviceManager = pServiceManager;

        // Initially the server handle is a fallback server handle
        serverHandle = ServerHandle.NULL_SERVER_HANDLE;
    }

    /** {@inheritDoc} */
    public ServerHandle getServerHandle() {
        return serverHandle;
    }

    /** {@inheritDoc} */
    public ObjectName registerMBean(Object pMBean, String... pOptionalName)
            throws MalformedObjectNameException, NotCompliantMBeanException, InstanceAlreadyExistsException, MBeanRegistrationException {
        return serviceManager.registerMBean(pMBean,pOptionalName);
    }

    /** {@inheritDoc} */
    public void setServerHandle(ServerHandle pServerHandle) {
        serverHandle = pServerHandle;
        AgentDetails details = getAgentDetails();
        details.setServerInfo(pServerHandle.getVendor(),pServerHandle.getProduct(),pServerHandle.getVersion());
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
    public boolean isCorsAccessAllowed(String pOrigin) {
        return getRestrictor().isCorsAccessAllowed(pOrigin);
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
