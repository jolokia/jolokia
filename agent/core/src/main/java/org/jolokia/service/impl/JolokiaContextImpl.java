package org.jolokia.service.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.ObjectName;

import org.jolokia.backend.*;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.converter.Converters;
import org.jolokia.detector.ServerHandle;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.service.JolokiaContext;
import org.jolokia.util.*;

/**
 * @author roland
 * @since 09.04.13
 */
public class JolokiaContextImpl implements JolokiaContext {
    private Configuration configuration;
    private LogHandler logHandler;
    private Restrictor restrictor;

    private Converters converters;

    // Dispatches request to local MBeanServer
    private LocalRequestDispatcher localDispatcher;

    // Handler for finding and merging the various MBeanHandler
    private MBeanServerHandler mBeanServerHandler;

    // List of RequestDispatchers to consult
    private List<RequestDispatcher> requestDispatchers;

    public JolokiaContextImpl(Configuration pConfig, LogHandler pLogHandler, Restrictor pRestrictor) {
        configuration = pConfig;
        logHandler = pLogHandler;

        // Access restrictor
        restrictor = pRestrictor != null ? pRestrictor : new AllowAllRestrictor();

        // Central objects
        converters = new Converters();

        // Get all MBean servers we can find. This is done by a dedicated
        // handler object
        mBeanServerHandler = new MBeanServerHandler(pConfig,pLogHandler);

        // Create and remember request dispatchers
        // TODO: Not fully intialized, will switch to lookup anyway
        localDispatcher = new LocalRequestDispatcher(this);
        requestDispatchers = createRequestDispatchers(pConfig.getConfig(ConfigKey.DISPATCHER_CLASSES),
                                                      this);
        requestDispatchers.add(localDispatcher);

        //int maxDebugEntries = configuration.getAsInt(ConfigKey.DEBUG_MAX_ENTRIES);
        //debugStore = new DebugStore(maxDebugEntries, configuration.getAsBoolean(ConfigKey.DEBUG));
    }



    // Construct configured dispatchers by reflection. Returns always
    // a list, an empty one if no request dispatcher should be created
    private List<RequestDispatcher> createRequestDispatchers(String pClasses,
                                                             JolokiaContext pContext) {
        List<RequestDispatcher> ret = new ArrayList<RequestDispatcher>();
        if (pClasses != null && pClasses.length() > 0) {
            String[] names = pClasses.split("\\s*,\\s*");
            for (String name : names) {
                ret.add(createDispatcher(name, pContext));
            }
        }
        return ret;
    }

    // Create a single dispatcher
    private RequestDispatcher createDispatcher(String pDispatcherClass,
                                               JolokiaContext pContext) {
        try {
            Class clazz = this.getClass().getClassLoader().loadClass(pDispatcherClass);
            Constructor constructor = clazz.getConstructor(JolokiaContext.class);
            return (RequestDispatcher)
                    constructor.newInstance(pContext);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Couldn't load class " + pDispatcherClass + ": " + e,e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + pDispatcherClass + " has invalid constructor: " + e,e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Constructor of " + pDispatcherClass + " couldn't be accessed: " + e,e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException(e);
        } catch (InstantiationException e) {
            throw new IllegalArgumentException(pDispatcherClass + " couldn't be instantiated: " + e,e);
        }
    }


    public String getConfig(ConfigKey pOption) {
        return configuration.getConfig(pOption);
    }

    public Set<ConfigKey> getConfigKeys() {
        return configuration.getConfigKeys();
    }

    public List<RequestDispatcher> getRequestDispatchers() {
        return requestDispatchers;
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
