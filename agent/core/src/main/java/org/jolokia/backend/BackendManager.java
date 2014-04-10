package org.jolokia.backend;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.*;

import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.config.ConfigKey;
import org.jolokia.config.Configuration;
import org.jolokia.converter.Converters;
import org.jolokia.converter.json.JsonConvertOptions;
import org.jolokia.detector.ServerHandle;
import org.jolokia.discovery.AgentDetails;
import org.jolokia.discovery.AgentDetailsHolder;
import org.jolokia.history.HistoryStore;
import org.jolokia.request.JmxRequest;
import org.jolokia.restrictor.AllowAllRestrictor;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.*;
import org.json.simple.JSONObject;

import static org.jolokia.config.ConfigKey.*;

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
 * Backendmanager for dispatching to various backends based on a given
 * {@link JmxRequest}
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class BackendManager implements AgentDetailsHolder {

    // Dispatches request to local MBeanServer
    private LocalRequestDispatcher localDispatcher;

    // Converter for converting various attribute object types
    // a JSON representation
    private Converters converters;

    // Hard limits for conversion
    private JsonConvertOptions.Builder convertOptionsBuilder;

    // Handling access restrictions
    private Restrictor restrictor;

    // History handler
    private HistoryStore historyStore;

    // Storage for storing debug information
    private DebugStore debugStore;

    // Loghandler for dispatching logs
    private LogHandler logHandler;

    // List of RequestDispatchers to consult
    private List<RequestDispatcher> requestDispatchers;

    // Initialize used for late initialization
    // ("volatile: because we use double-checked locking later on
    // --> http://www.cs.umd.edu/~pugh/java/memoryModel/DoubleCheckedLocking.html)
    private volatile Initializer initializer;

    // Details about the agent inclding the server handle
    private AgentDetails agentDetails;

    /**
     * Construct a new backend manager with the given configuration and which allows
     * every operation (no restrictor)
     *
     * @param pConfig configuration used for tuning this handler's behaviour
     * @param pLogHandler logger
     */
    public BackendManager(Configuration pConfig, LogHandler pLogHandler) {
        this(pConfig, pLogHandler, null);
    }

    /**
     * Construct a new backend manager with the given configuration.
     *
     * @param pConfig configuration used for tuning this handler's behaviour
     * @param pLogHandler logger
     * @param pRestrictor a restrictor for limiting access. Can be null in which case every operation is allowed
     */
    public BackendManager(Configuration pConfig, LogHandler pLogHandler, Restrictor pRestrictor) {
        this(pConfig,pLogHandler,pRestrictor,false);
    }

    /**
     * Construct a new backend manager with the given configuration.
     *
     * @param pConfig configuration map used for tuning this handler's behaviour
     * @param pLogHandler logger
     * @param pRestrictor a restrictor for limiting access. Can be null in which case every operation is allowed
     * @param pLazy whether the initialisation should be done lazy
     */
    public BackendManager(Configuration pConfig, LogHandler pLogHandler, Restrictor pRestrictor, boolean pLazy) {

        // Access restrictor
        restrictor = pRestrictor != null ? pRestrictor : new AllowAllRestrictor();

        // Log handler for putting out debug
        logHandler = pLogHandler;

        // Details about the agent, used for discovery
        agentDetails = new AgentDetails(pConfig);

        if (pLazy) {
            initializer = new Initializer(pConfig);
        } else {
            init(pConfig);
            initializer = null;
        }
    }

    /**
     * Handle a single JMXRequest. The response status is set to 200 if the request
     * was successful
     *
     * @param pJmxReq request to perform
     * @return the already converted answer.
     * @throws InstanceNotFoundException
     * @throws AttributeNotFoundException
     * @throws ReflectionException
     * @throws MBeanException
     */
    public JSONObject handleRequest(JmxRequest pJmxReq) throws InstanceNotFoundException, AttributeNotFoundException,
            ReflectionException, MBeanException, IOException {
        lazyInitIfNeeded();

        boolean debug = isDebug();

        long time = 0;
        if (debug) {
            time = System.currentTimeMillis();
        }
        JSONObject json;
        try {
            json = callRequestDispatcher(pJmxReq);

            // Update global history store, add timestamp and possibly history information to the request
            historyStore.updateAndAdd(pJmxReq,json);
            json.put("status",200 /* success */);
        } catch (NotChangedException exp) {
            // A handled indicates that its value hasn't changed. We return an status with
            //"304 Not Modified" similar to the HTTP status code (http://en.wikipedia.org/wiki/HTTP_status)
            json = new JSONObject();
            json.put("request",pJmxReq.toJSON());
            json.put("status",304);
            json.put("timestamp",System.currentTimeMillis() / 1000);
        }

        if (debug) {
            debug("Execution time: " + (System.currentTimeMillis() - time) + " ms");
            debug("Response: " + json);
        }

        return json;
    }

    /**
     * Convert a Throwable to a JSON object so that it can be included in an error response
     *
     * @param pExp throwable to convert
     * @param pJmxReq the request from where to take the serialization options
     * @return the exception.
     */
    public Object convertExceptionToJson(Throwable pExp, JmxRequest pJmxReq)  {
        JsonConvertOptions opts = getJsonConvertOptions(pJmxReq);
        try {
            JSONObject expObj =
                    (JSONObject) converters.getToJsonConverter().convertToJson(pExp,null,opts);
            return expObj;

        } catch (AttributeNotFoundException e) {
            // Cannot happen, since we dont use a path
            return null;
        }
    }

    /**
     * Remove MBeans
     */
    public void destroy() {
        try {
            localDispatcher.destroy();
        } catch (JMException e) {
            error("Cannot unregister MBean: " + e,e);
        }
    }

    /**
     * Check whether remote access from the given client is allowed.
     *
     * @param pRemoteHost remote host to check against
     * @param pRemoteAddr alternative IP address
     * @return true if remote access is allowed
     */
    public boolean isRemoteAccessAllowed(String pRemoteHost, String pRemoteAddr) {
        return restrictor.isRemoteAccessAllowed(pRemoteHost, pRemoteAddr);
    }

    /**
     * Check whether CORS access is allowed for the given origin.
     *
     * @param pOrigin origin URL which needs to be checked
     * @param pStrictChecking whether to a strict check (i.e. server side check)
     * @return true if if cors access is allowed
     */
    public boolean isOriginAllowed(String pOrigin,boolean pStrictChecking) {
        return restrictor.isOriginAllowed(pOrigin, pStrictChecking);
    }

    /**
     * Log at info level
     *
     * @param msg to log
     */
    public void info(String msg) {
        logHandler.info(msg);
        if (debugStore != null) {
            debugStore.log(msg);
        }
    }

    /**
     * Log at debug level
     *
     * @param msg message to log
     */
    public void debug(String msg) {
        logHandler.debug(msg);
        if (debugStore != null) {
            debugStore.log(msg);
        }
    }

    /**
     * Log at error level.
     *
     * @param message message to log
     * @param t ecxeption occured
     */
    public void error(String message, Throwable t) {
        // Must not be final so that we can mock it in EasyMock for our tests
        logHandler.error(message, t);
        if (debugStore != null) {
            debugStore.log(message, t);
        }
    }

    /**
     * Whether debug is switched on
     *
     * @return true if debug is switched on
     */
    public boolean isDebug() {
        return debugStore != null && debugStore.isDebug();
    }


    /**
     * Get the details for the agent which can be updated or used
     *
     * @return agent details
     */
    public AgentDetails getAgentDetails() {
        return agentDetails;
    }



    // ==========================================================================================================

    // Initialized used for late initialisation as it is required for the agent when used
    // as startup options
    private final class Initializer {

        private Configuration config;

        private Initializer(Configuration pConfig) {
            config = pConfig;
        }

        void init() {
            BackendManager.this.init(config);
        }
    }

    // Run initialized if not already done
    private void lazyInitIfNeeded() {
        if (initializer != null) {
            synchronized (this) {
                if (initializer != null) {
                    initializer.init();
                    initializer = null;
                }
            }
        }
    }

    // Initialize this object;
    private void init(Configuration pConfig) {
        // Central objects
        converters = new Converters();
        initLimits(pConfig);

        // Create and remember request dispatchers
        localDispatcher = new LocalRequestDispatcher(converters,
                                                     restrictor,
                                                     pConfig,
                                                     logHandler);
        ServerHandle serverHandle = localDispatcher.getServerHandle();
        requestDispatchers = createRequestDispatchers(pConfig.get(DISPATCHER_CLASSES),
                                                      converters,serverHandle,restrictor);
        requestDispatchers.add(localDispatcher);

        // Backendstore for remembering agent state
        initMBeans(pConfig);

        agentDetails.setServerInfo(serverHandle.getVendor(),serverHandle.getProduct(),serverHandle.getVersion());
    }

    private void initLimits(Configuration pConfig) {
        // Max traversal depth
        if (pConfig != null) {
            convertOptionsBuilder = new JsonConvertOptions.Builder(
                    getNullSaveIntLimit(pConfig.get(MAX_DEPTH)),
                    getNullSaveIntLimit(pConfig.get(MAX_COLLECTION_SIZE)),
                    getNullSaveIntLimit(pConfig.get(MAX_OBJECTS))
            );
        } else {
            convertOptionsBuilder = new JsonConvertOptions.Builder();
        }
    }

    private int getNullSaveIntLimit(String pValue) {
        return pValue != null ? Integer.parseInt(pValue) : 0;
    }

    // Construct configured dispatchers by reflection. Returns always
    // a list, an empty one if no request dispatcher should be created
    private List<RequestDispatcher> createRequestDispatchers(String pClasses,
                                                             Converters pConverters,
                                                             ServerHandle pServerHandle,
                                                             Restrictor pRestrictor) {
        List<RequestDispatcher> ret = new ArrayList<RequestDispatcher>();
        if (pClasses != null && pClasses.length() > 0) {
            String[] names = pClasses.split("\\s*,\\s*");
            for (String name : names) {
                ret.add(createDispatcher(name, pConverters, pServerHandle, pRestrictor));
            }
        }
        return ret;
    }

    // Create a single dispatcher
    private RequestDispatcher createDispatcher(String pDispatcherClass,
                                               Converters pConverters,
                                               ServerHandle pServerHandle, Restrictor pRestrictor) {
        try {
            Class clazz = this.getClass().getClassLoader().loadClass(pDispatcherClass);
            Constructor constructor = clazz.getConstructor(Converters.class,
                                                           ServerHandle.class,
                                                           Restrictor.class);
            return (RequestDispatcher)
                    constructor.newInstance(pConverters,
                                            pServerHandle,
                                            pRestrictor);
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

    // call the an appropriate request dispatcher
    private JSONObject callRequestDispatcher(JmxRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        Object retValue = null;
        boolean useValueWithPath = false;
        boolean found = false;
        for (RequestDispatcher dispatcher : requestDispatchers) {
            if (dispatcher.canHandle(pJmxReq)) {
                retValue = dispatcher.dispatchRequest(pJmxReq);
                useValueWithPath = dispatcher.useReturnValueWithPath(pJmxReq);
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalStateException("Internal error: No dispatcher found for handling " + pJmxReq);
        }

        JsonConvertOptions opts = getJsonConvertOptions(pJmxReq);

        Object jsonResult =
                converters.getToJsonConverter()
                          .convertToJson(retValue, useValueWithPath ? pJmxReq.getPathParts() : null, opts);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("value",jsonResult);
        jsonObject.put("request",pJmxReq.toJSON());
        return jsonObject;
    }

    private JsonConvertOptions getJsonConvertOptions(JmxRequest pJmxReq) {
        return convertOptionsBuilder.
                    maxDepth(pJmxReq.getParameterAsInt(ConfigKey.MAX_DEPTH)).
                    maxCollectionSize(pJmxReq.getParameterAsInt(ConfigKey.MAX_COLLECTION_SIZE)).
                    maxObjects(pJmxReq.getParameterAsInt(ConfigKey.MAX_OBJECTS)).
                    faultHandler(pJmxReq.getValueFaultHandler()).
                    build();
    }

    // init various application wide stores for handling history and debug output.
    private void initMBeans(Configuration pConfig) {
        int maxEntries = pConfig.getAsInt(HISTORY_MAX_ENTRIES);
        int maxDebugEntries = pConfig.getAsInt(DEBUG_MAX_ENTRIES);


        historyStore = new HistoryStore(maxEntries);
        debugStore = new DebugStore(maxDebugEntries, pConfig.getAsBoolean(DEBUG));

        try {
            localDispatcher.initMBeans(historyStore, debugStore);
        } catch (NotCompliantMBeanException e) {
            intError("Error registering config MBean: " + e, e);
        } catch (MBeanRegistrationException e) {
            intError("Cannot register MBean: " + e, e);
        } catch (MalformedObjectNameException e) {
            intError("Invalid name for config MBean: " + e, e);
        }
    }

    // Final private error log for use in the constructor above
    private void intError(String message,Throwable t) {
        logHandler.error(message, t);
        debugStore.log(message, t);
    }
}
