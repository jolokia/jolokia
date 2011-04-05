package org.jolokia.backend;

import org.jolokia.converter.StringToObjectConverter;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.detector.ServerHandle;
import org.jolokia.history.HistoryStore;
import org.jolokia.restrictor.*;
import org.jolokia.util.*;
import org.jolokia.request.JmxRequest;
import org.json.simple.JSONObject;

import javax.management.*;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jolokia.util.ConfigKey.*;

/*
 *  Copyright 2009-2010 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


/**
 * <p>
 *   Backendmanager for dispatching to various backends based on a given
 *   {@link JmxRequest}. This is the entry class for protocol stack handler, which
 *   take a Jolokia request in some format, creates {@link JmxRequest} objects from
 *   the input, calls this backend manager and returns its answer in an appropriate format.
 * </p>
 * <p>
 *   A client of this class needs to provide the following input for the constructor:
 * </p>
 * <ul>
 *   <li>
 *     A configuration described in a map with {@link ConfigKey} enumeration values as keys and strings
 *     a values. {@link ConfigKey} described all possible value which can influence the operation of
 *     this backend manager. The client is responsible to extract request options ({@link ConfigKey#isRequestConfig()} == true)
 *     and pass them in via this Map.
 *   </li>
 *   <li>
 *     An implementation of a {@link LogHandler} used for logging within this BackendManager
 *   </li>
 *   <li>
 *     An optional {@link Restrictor} implementation for restricting access. If given, this manager will
 *     thrown an {@link SecurityException} if the request doesn't pass the restrictor. For restriction on
 *     the IP address and the HTTP method used the client of this class is responsible itself, since the backend
 *     manager is agnostic to the transport protocol. See {@link PolicyRestrictor} for an implementation
 *     of a restrictor which uses a policy file.
 *   </li>
 * </ul>
 * <p>
 *   The single entry point of this backend manager is {@link #handleRequest(JmxRequest)} which takes a prepared
 *   {@link JmxRequest} and returns a typeless JSONObject which can be directly used as return value for JSON based
 *   protocol stacks. A usage example for this class can be found in <code>HttpHandler</code> in the module
 *   <code>jolokia-protocol-classic</code. 
 * </p>
 *
 * @author roland
 * @since Nov 11, 2009
 */
public class BackendManager {

    // Dispatches request to local MBeanServer
    private LocalRequestDispatcher localDispatcher;

    // Converter for converting various attribute object types
    // a JSON representation
    private ObjectToJsonConverter objectToJsonConverter;

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

    /**
     * Construct a new backend manager with the given configuration and which allows
     * every operation (i.e. no restrictor applies)
     *
     * @param pConfig configuration map used for tuning this handler's behaviour
     * @param pLogHandler logger
     */
    public BackendManager(Map<ConfigKey,String> pConfig, LogHandler pLogHandler) {
        this(pConfig,pLogHandler,null);
    }

    /**
     * Construct a new backend manager with the given configuration.
     *
     * @param pConfig configuration map used for tuning this handler's behaviour
     * @param pLogHandler logger
     * @param pRestrictor a restrictor for limiting access. Can be <code>null</code> in which case
     *        every operation is allowed
     */
    public BackendManager(Map<ConfigKey, String> pConfig, LogHandler pLogHandler, Restrictor pRestrictor) {

        // Central objects
        StringToObjectConverter stringToObjectConverter = new StringToObjectConverter();
        objectToJsonConverter = new ObjectToJsonConverter(stringToObjectConverter,pConfig);

        // Access restrictor
        restrictor = pRestrictor != null ? pRestrictor : new AllowAllRestrictor();

        // Log handler for putting out debug
        logHandler = pLogHandler;

        // Create and remember request dispatchers
        localDispatcher = new LocalRequestDispatcher(objectToJsonConverter,
                                                     stringToObjectConverter,
                                                     restrictor,
                                                     pConfig.get(ConfigKey.MBEAN_QUALIFIER),
                                                     logHandler);
        ServerHandle serverHandle = localDispatcher.getServerInfo();
        requestDispatchers = createRequestDispatchers(DISPATCHER_CLASSES.getValue(pConfig),
                                                      objectToJsonConverter,stringToObjectConverter, serverHandle,restrictor);
        requestDispatchers.add(localDispatcher);

        // Backendstore for remembering agent state
        initStores(pConfig);
    }

    /**
     * <p>
     *   Handle a single JMXRequest. It takes a {@link JmxRequest} object as input, performs
     *   the requested operation on the backend (e.g. on the local MBeanServer or via a proxy),
     *   and returns the answer in a typeless Map. The format of this response is different for
     *   different types od requests and is defined in the
     *   <a href="http://www.jolokia.org/">Jolokia reference manual</a>.
     * </p>
     * <p>
     *   The response status is set to 200 if the request was successful, error handling has to
     *   be done outside this backend manager by catching the appropriate exceptions and
     *   converting them to the proper answer.
     * </p>
     * <p>
     *   The classic Jolokia protocol handler calls this method for bulk requests multiple times
     *   and creates error messages as described in the <a href="">reference manual</a>, too. The resulting
     *   object is returned directly to the client.
     * </p>
     *
     * @param pJmxReq request to perform
     * @return the response as a map.
     * @throws InstanceNotFoundException if a referenced MBean could not be found
     * @throws AttributeNotFoundException if a referenced attribute doesnot exist on the target MBean
     * @throws ReflectionException if something failed during reflection based serialization of the object
     * @throws MBeanException general exception occured during JMX operation
     */
    public JSONObject handleRequest(JmxRequest pJmxReq) throws InstanceNotFoundException, AttributeNotFoundException,
            ReflectionException, MBeanException, IOException {

        boolean debug = isDebug();

        long time = 0;
        if (debug) {
            time = System.currentTimeMillis();
        }
        JSONObject json = callRequestDispatcher(pJmxReq);

        // Update global history store
        historyStore.updateAndAdd(pJmxReq,json);
        if (debug) {
            debug("Execution time: " + (System.currentTimeMillis() - time) + " ms");
            debug("Response: " + json);
        }
        // Ok, we did it ...
        json.put("status",200 /* success */);
        return json;
    }

    // =========================================================================================================

    // Construct configured dispatchers by reflection. Returns always
    // a list, an empty one if no request dispatcher should be created
    private List<RequestDispatcher> createRequestDispatchers(String pClasses,
                                                             ObjectToJsonConverter pObjectToJsonConverter,
                                                             StringToObjectConverter pStringToObjectConverter,
                                                             ServerHandle pServerHandle, Restrictor pRestrictor) {
        List<RequestDispatcher> ret = new ArrayList<RequestDispatcher>();
        if (pClasses != null && pClasses.length() > 0) {
            String[] names = pClasses.split("\\s*,\\s*");
            for (String name : names) {
                ret.add(createDispatcher(name, pObjectToJsonConverter, pStringToObjectConverter, pServerHandle, pRestrictor));
            }
        }
        return ret;
    }

    // Create a single dispatcher
    private RequestDispatcher createDispatcher(String pDispatcherClass, ObjectToJsonConverter pObjectToJsonConverter, StringToObjectConverter pStringToObjectConverter, ServerHandle pServerHandle, Restrictor pRestrictor) {
        try {
            Class clazz = this.getClass().getClassLoader().loadClass(pDispatcherClass);
            Constructor constructor = clazz.getConstructor(ObjectToJsonConverter.class,
                                                           StringToObjectConverter.class,
                                                           ServerHandle.class,
                                                           Restrictor.class);
            return (RequestDispatcher)
                    constructor.newInstance(pObjectToJsonConverter,
                                            pStringToObjectConverter,
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
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException {
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
        return objectToJsonConverter.convertToJson(retValue,pJmxReq,useValueWithPath);
    }

    // init various application wide stores for handling history and debug output.
    private void initStores(Map<ConfigKey, String> pConfig) {
        int maxEntries = getIntConfigValue(pConfig,HISTORY_MAX_ENTRIES);
        int maxDebugEntries = getIntConfigValue(pConfig,DEBUG_MAX_ENTRIES);

        String doDebug = DEBUG.getValue(pConfig);
        boolean debug = false;
        if (doDebug != null && Boolean.valueOf(doDebug)) {
            debug = true;
        }


        historyStore = new HistoryStore(maxEntries);
        debugStore = new DebugStore(maxDebugEntries,debug);

        try {
            localDispatcher.init(historyStore,debugStore);
        } catch (NotCompliantMBeanException e) {
            error("Error registering config MBean: " + e,e);
        } catch (MBeanRegistrationException e) {
            error("Cannot register MBean: " + e,e);
        } catch (MalformedObjectNameException e) {
            error("Invalid name for config MBean: " + e,e);
        } catch (InstanceAlreadyExistsException e) {
            error("Config MBean already exists: " + e,e);
        }
    }

    private int getIntConfigValue(Map<ConfigKey, String> pConfig, ConfigKey pKey) {
        int maxDebugEntries;
        try {
            maxDebugEntries = Integer.parseInt(pKey.getValue(pConfig));
        } catch (NumberFormatException exp) {
            maxDebugEntries = Integer.parseInt(pKey.getDefaultValue());
        }
        return maxDebugEntries;
    }

    // Remove MBeans again.
    public void destroy() {
        try {
            localDispatcher.destroy();
        } catch (JMException e) {
            error("Cannot unregister MBean: " + e,e);
        }
    }


    public boolean isRemoteAccessAllowed(String pRemoteHost, String pRemoteAddr) {
        return restrictor.isRemoteAccessAllowed(pRemoteHost,pRemoteAddr);
    }

    public void info(String msg) {
        logHandler.info(msg);
        if (debugStore != null) {
            debugStore.log(msg);
        }
    }

    public void debug(String msg) {
        logHandler.debug(msg);
        if (debugStore != null) {
            debugStore.log(msg);
        }
    }

    public final void error(String message, Throwable t) {
        logHandler.error(message,t);
        if (debugStore != null) {
            debugStore.log(message, t);
        }
    }

    public boolean isDebug() {
        return debugStore != null && debugStore.isDebug();
    }

    /**
     * Set the log handler used for log handling
     *
     * @param pLogHandler log handler to use
     */
    public void setLogHandler(LogHandler pLogHandler) {
        logHandler = pLogHandler;
    }


}
