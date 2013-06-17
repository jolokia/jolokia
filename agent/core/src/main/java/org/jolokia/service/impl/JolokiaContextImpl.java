package org.jolokia.service.impl;

import java.util.Set;
import java.util.SortedSet;

import javax.management.ObjectName;

import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.converter.Converters;
import org.jolokia.detector.ServerHandle;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.service.*;
import org.jolokia.util.*;

/**
 * Central implementation of the {@link JolokiaContext}
 *
 * @author roland
 * @since 09.04.13
 */
public class JolokiaContextImpl implements JolokiaContext {

    // Converts for object serialization
    private Converters converters;

    // Server handle
    private ServerHandle serverHandle;

    // Service manager associated with the context
    private JolokiaServiceManager serviceManager;

    JolokiaContextImpl(JolokiaServiceManager pServiceManager) {
        serviceManager = pServiceManager;

        // Central objects
        // TODO: Lookup
        converters = new Converters();

        // Initially the server handle is a fallback server handle
        serverHandle = ServerHandle.NULL_SERVER_HANDLE;
    }

    public ServerHandle getServerHandle() {
        return serverHandle;
    }

    // Used for setting the server handle in retrospective
    public void setServerHandle(ServerHandle pServerHandle) {
        serverHandle = pServerHandle;
    }


    public String getConfig(ConfigKey pOption) {
        return getConfiguration().getConfig(pOption);
    }

    public Set<ConfigKey> getConfigKeys() {
        return getConfiguration().getConfigKeys();
    }

    public Converters getConverters() {
        return converters;
    }

    public <T extends JolokiaService> SortedSet<T> getServices(Class<T> pType) {
        return serviceManager.getServices(pType);
    }

    public boolean isDebug() {
        return getLog().isDebug();
    }

    public void debug(String message) {
        getLog().debug(message);
    }

    public void info(String message) {
        getLog().info(message);
    }

    public void error(String message, Throwable t) {
        getLog().error(message, t);
    }

    public boolean isHttpMethodAllowed(HttpMethod pMethod) {
        return getRestrictor().isHttpMethodAllowed(pMethod);
    }

    public boolean isTypeAllowed(RequestType pType) {
        return getRestrictor().isTypeAllowed(pType);
    }

    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return getRestrictor().isAttributeReadAllowed(pName, pAttribute);
    }

    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return getRestrictor().isAttributeWriteAllowed(pName, pAttribute);
    }

    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return getRestrictor().isOperationAllowed(pName, pOperation);
    }

    public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
        return getRestrictor().isRemoteAccessAllowed(pHostOrAddress);
    }

    public boolean isCorsAccessAllowed(String pOrigin) {
        return getRestrictor().isCorsAccessAllowed(pOrigin);
    }

    private Configuration getConfiguration() {
        return serviceManager.getConfiguration();
    }

    private Restrictor getRestrictor() {
        return serviceManager.getRestrictor();
    }

    private LogHandler getLog() {
        return serviceManager.getLogHandler();
    }
}
