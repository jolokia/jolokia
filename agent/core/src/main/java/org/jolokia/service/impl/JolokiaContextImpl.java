package org.jolokia.service.impl;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.*;

import org.jolokia.backend.*;
import org.jolokia.backend.dispatcher.*;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.converter.Converters;
import org.jolokia.detector.ServerHandle;
import org.jolokia.request.JmxRequest;
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

    // manager for dispatching requests
    private final RequestDispatchManager requestDispatchManager;

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
        mBeanServerHandler = new MBeanServerHandler(pConfig,pLogHandler);

        // Create and remember request dispatchers
        // TODO: Not fully initialized, will switch to lookup anyway
        LocalRequestDispatcher localDispatcher = new LocalRequestDispatcher(this);
        List<RequestDispatcher> requestDispatchers = createRequestDispatchers(pConfig.getConfig(ConfigKey.DISPATCHER_CLASSES),
                                                                              this);
        requestDispatchers.add(localDispatcher);
        requestDispatchManager = new RequestDispatchManager(requestDispatchers);

        //int maxDebugEntries = configuration.getAsInt(ConfigKey.DEBUG_MAX_ENTRIES);
        //debugStore = new DebugStore(maxDebugEntries, configuration.getAsBoolean(ConfigKey.DEBUG));
    }

    public void destroy() throws JMException {
        mBeanServerHandler.destroy();
        requestDispatchManager.destroy();
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

    public DispatchResult dispatch(JmxRequest request) throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        return requestDispatchManager.dispatch(request);
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
