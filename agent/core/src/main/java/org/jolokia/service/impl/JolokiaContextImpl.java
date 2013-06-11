package org.jolokia.service.impl;

import java.util.Set;

import javax.management.JMException;
import javax.management.ObjectName;

import org.jolokia.backend.MBeanServerHandler;
import org.jolokia.backend.MBeanServerHandlerImpl;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.converter.Converters;
import org.jolokia.detector.ServerHandle;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.service.JolokiaContext;
import org.jolokia.util.*;

/**
 * Central implementation of the {@link JolokiaContext}
 *
 * @author roland
 * @since 09.04.13
 */
public class JolokiaContextImpl implements JolokiaContext {

    // Overall configuration for which this context is a delegate
    private Configuration configuration;

    // Logger to use
    private LogHandler logHandler;

    // The restrictor to delegate to
    private Restrictor restrictor;

    // Converts for object serialization
    private Converters converters;

    // Handler for finding and merging the various MBeanHandler
    private MBeanServerHandler mBeanServerHandler;

    public JolokiaContextImpl(Configuration pConfig,
                              LogHandler pLogHandler,
                              Restrictor pRestrictor) {
        configuration = pConfig;
        logHandler = pLogHandler;

        // Access restrictor
        restrictor = pRestrictor != null ? pRestrictor : new AllowAllRestrictor();

        // Central objects
        // TODO: Lookup
        converters = new Converters();

        // Get all MBean servers we can find. This is done by a dedicated
        // handler object
        mBeanServerHandler = new MBeanServerHandlerImpl(pConfig,pLogHandler);

        //int maxDebugEntries = configuration.getAsInt(ConfigKey.DEBUG_MAX_ENTRIES);
        //debugStore = new DebugStore(maxDebugEntries, configuration.getAsBoolean(ConfigKey.DEBUG));
    }

    public void destroy() throws JMException {
        mBeanServerHandler.destroy();
    }


    public String getConfig(ConfigKey pOption) {
        return configuration.getConfig(pOption);
    }

    public Set<ConfigKey> getConfigKeys() {
        return configuration.getConfigKeys();
    }

    public MBeanServerHandler getMBeanServerHandler() {
        return mBeanServerHandler;
    }

    public Converters getConverters() {
        return converters;
    }

    public ServerHandle getServerHandle() {
        return mBeanServerHandler.getServerHandle();
    }

    public boolean isDebug() {
        return logHandler.isDebug();
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

    public boolean isHttpMethodAllowed(HttpMethod pMethod) {
        return restrictor.isHttpMethodAllowed(pMethod);
    }

    public boolean isTypeAllowed(RequestType pType) {
        return restrictor.isTypeAllowed(pType);
    }

    public boolean isAttributeReadAllowed(ObjectName pName, String pAttribute) {
        return restrictor.isAttributeReadAllowed(pName,pAttribute);
    }

    public boolean isAttributeWriteAllowed(ObjectName pName, String pAttribute) {
        return restrictor.isAttributeWriteAllowed(pName,pAttribute);
    }

    public boolean isOperationAllowed(ObjectName pName, String pOperation) {
        return restrictor.isOperationAllowed(pName,pOperation);
    }

    public boolean isRemoteAccessAllowed(String... pHostOrAddress) {
        return restrictor.isRemoteAccessAllowed(pHostOrAddress);
    }

    public boolean isCorsAccessAllowed(String pOrigin) {
        return restrictor.isCorsAccessAllowed(pOrigin);
    }
}
