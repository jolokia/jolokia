package org.jolokia.backend;

import java.io.IOException;
import java.util.UUID;

import javax.management.*;

import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.config.ConfigKey;
import org.jolokia.converter.json.JsonConvertOptions;
import org.jolokia.history.HistoryStore;
import org.jolokia.request.JmxRequest;
import org.jolokia.service.JolokiaContext;
import org.jolokia.util.DebugStore;
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
public class BackendManager {

    // Overall Jolokia context
    private final JolokiaContext jolokiaCtx;

    // Hard limits for conversion
    private JsonConvertOptions.Builder convertOptionsBuilder;

    // History handler
    private HistoryStore historyStore;

    // Storage for storing debug information
    private DebugStore debugStore;

    // Initialize used for late initialization
    // ("volatile: because we use double-checked locking later on
    // --> http://www.cs.umd.edu/~pugh/java/memoryModel/DoubleCheckedLocking.html)
    private volatile Initializer initializer;

    /**
     * Construct a new backend manager with the given configuration.
     *
     * @param pJolokiaCtx jolokia context for accessing internal services
     * @param pLazy whether the initialisation should be done lazy
     */
    public BackendManager(JolokiaContext pJolokiaCtx, boolean pLazy) {
        jolokiaCtx = pJolokiaCtx;

        if (pLazy) {
            initializer = new Initializer();
        } else {
            init(pJolokiaCtx);
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

        boolean debug = jolokiaCtx.isDebug();

        long time = 0;
        if (debug) {
            time = System.currentTimeMillis();
        }
        JSONObject json = null;
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
            jolokiaCtx.debug("Execution time: " + (System.currentTimeMillis() - time) + " ms");
            jolokiaCtx.debug("Response: " + json);
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
                    (JSONObject) jolokiaCtx.getConverters().getToJsonConverter().convertToJson(pExp,null,opts);
            return expObj;

        } catch (AttributeNotFoundException e) {
            // Cannot happen, since we dont use a path
            return null;
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
        return jolokiaCtx.isRemoteAccessAllowed(pRemoteHost, pRemoteAddr);
    }

    /**
     * Check whether CORS access is allowed for the given origin.
     *
     * @param pOrigin origin URL which needs to be checked
     * @return true if icors access is allowed
     */
    public boolean isCorsAccessAllowed(String pOrigin) {
        return jolokiaCtx.isCorsAccessAllowed(pOrigin);
    }

    // ==========================================================================================================

    // Initialized used for late initialisation as it is required for the agent when used
    // as startup options
    private final class Initializer {
        void init() {
            BackendManager.this.init(jolokiaCtx);
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
    private void init(JolokiaContext pCtx) {
        // Init limits
        initLimits(pCtx);

        // Backendstore for remembering agent state
        initStores(pCtx);
    }

    private void initLimits(JolokiaContext pContext) {
        // Max traversal depth
        if (pContext != null) {
            convertOptionsBuilder = new JsonConvertOptions.Builder(
                    getNullSaveIntLimit(pContext.getConfig(MAX_DEPTH)),
                    getNullSaveIntLimit(pContext.getConfig(MAX_COLLECTION_SIZE)),
                    getNullSaveIntLimit(pContext.getConfig(MAX_OBJECTS))
            );
        } else {
            convertOptionsBuilder = new JsonConvertOptions.Builder();
        }
    }

    private int getNullSaveIntLimit(String pValue) {
        return pValue != null ? Integer.parseInt(pValue) : 0;
    }

    // call the an appropriate request dispatcher
    private JSONObject callRequestDispatcher(JmxRequest pJmxReq)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException, IOException, NotChangedException {
        Object retValue = null;
        boolean useValueWithPath = false;
        boolean found = false;
        for (RequestDispatcher dispatcher : jolokiaCtx.getRequestDispatchers()) {
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
                jolokiaCtx.getConverters().getToJsonConverter()
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
    private void initStores(JolokiaContext pCtx) {
        int maxEntries = pCtx.getConfigAsInt(HISTORY_MAX_ENTRIES);
        historyStore = new HistoryStore(maxEntries);

        try {
            initMBeans(historyStore);
        } catch (NotCompliantMBeanException e) {
            internalError("Error registering config MBean: " + e, e);
        } catch (MBeanRegistrationException e) {
            internalError("Cannot register MBean: " + e, e);
        } catch (MalformedObjectNameException e) {
            internalError("Invalid name for config MBean: " + e, e);
        }
    }


    private void initMBeans(HistoryStore pHistoryStore)
            throws MalformedObjectNameException, MBeanRegistrationException, NotCompliantMBeanException {

        // Register the Config MBean
        String oName = createObjectNameWithQualifier(Config.OBJECT_NAME);
        try {
            Config config = new Config(pHistoryStore,oName);
            jolokiaCtx.getMBeanServerHandler().registerMBean(config,oName);
        } catch (InstanceAlreadyExistsException exp) {
            String alternativeOName = oName + ",uuid=" + UUID.randomUUID();
            try {
                // Another instance has already started a Jolokia agent within the JVM. We are trying to add the MBean nevertheless with
                // a dynamically generated ObjectName. Of course, it would be good to have a more semantic meaning instead of
                // a random number, but this can already be performed with a qualifier
                jolokiaCtx.info(oName + " is already registered. Adding it with " + alternativeOName + ", but you should revise your setup in " +
                         "order to either use a qualifier or ensure, that only a single agent gets registered (otherwise history functionality might not work)");
                Config config = new Config(pHistoryStore,alternativeOName);
                jolokiaCtx.getMBeanServerHandler().registerMBean(config,alternativeOName);
            } catch (InstanceAlreadyExistsException e) {
                jolokiaCtx.error("Cannot even register fallback MBean with name " + alternativeOName + ". Should never happen. Really.", e);
            }
        }
    }

    private String createObjectNameWithQualifier(String pOName) {
        String qualifier = jolokiaCtx.getConfig(ConfigKey.MBEAN_QUALIFIER);
        return pOName + (qualifier != null ? "," + qualifier : "");
    }
    // Final private error log for use in the constructor above
    private void internalError(String message, Throwable t) {
        jolokiaCtx.error(message, t);
        debugStore.log(message, t);
    }
}
