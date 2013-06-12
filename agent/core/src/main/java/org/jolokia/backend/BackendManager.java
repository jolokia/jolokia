package org.jolokia.backend;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import javax.management.*;

import org.jolokia.backend.dispatcher.DispatchResult;
import org.jolokia.backend.dispatcher.RequestDispatcher;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.config.ConfigKey;
import org.jolokia.converter.json.JsonConvertOptions;
import org.jolokia.history.History;
import org.jolokia.request.JmxRequest;
import org.jolokia.service.JolokiaContext;
import org.jolokia.util.JmxUtil;
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

    // Objectname for updating the history
    private static final ObjectName HISTORY_OBJECTNAME = JmxUtil.newObjectName(History.OBJECT_NAME);

    // Overall Jolokia context
    private final JolokiaContext jolokiaCtx;

    // Dispatcher for doing the requests
    private final RequestDispatcher requestDispatcher;

    // Hard limits for conversion
    private JsonConvertOptions.Builder convertOptionsBuilder;

    // Initialize used for late initialization
    // ("volatile: because we use double-checked locking later on
    // --> http://www.cs.umd.edu/~pugh/java/memoryModel/DoubleCheckedLocking.html)
    private volatile Initializer initializer;

    /**
     * Construct a new backend manager with the given configuration.
     *
     * @param pJolokiaCtx jolokia context for accessing internal services
     * @param pRequestDispatcher
     */
    public BackendManager(JolokiaContext pJolokiaCtx, RequestDispatcher pRequestDispatcher) {
        jolokiaCtx = pJolokiaCtx;
        requestDispatcher = pRequestDispatcher;

        // TODO: Left here for reference. Mechanism must be moved to the place
        // where Detectors have to be initialized.
        if (false) {
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
            updateHistory(pJmxReq, json);
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

    // TODO: Maybe move to an interceptor ?

    /**
     * Update history
     * @param pJmxReq request obtained
     * @param pJson result as included in the response
     */
    private void updateHistory(JmxRequest pJmxReq, JSONObject pJson) {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        try {
            mBeanServer.invoke(HISTORY_OBJECTNAME,
                               "updateAndAdd",
                               new Object[] { pJmxReq, pJson},
                               new String[] { JmxRequest.class.getName(), JSONObject.class.getName() });
        } catch (InstanceNotFoundException e) {
            // Ignore, no history MBean is enabled, so no update
        } catch (MBeanException e) {
            throw new IllegalStateException("Internal: Cannot update History store",e);
        } catch (ReflectionException e) {
            throw new IllegalStateException("Internal: Cannot call History MBean via reflection",e);
        }
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

        // Max traversal depth
        if (pCtx != null) {
            convertOptionsBuilder = new JsonConvertOptions.Builder(
                    getNullSaveIntLimit(pCtx.getConfig(MAX_DEPTH)),
                    getNullSaveIntLimit(pCtx.getConfig(MAX_COLLECTION_SIZE)),
                    getNullSaveIntLimit(pCtx.getConfig(MAX_OBJECTS))
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
        DispatchResult result = requestDispatcher.dispatch(pJmxReq);
        if (result == null) {
            throw new IllegalStateException("Internal error: No dispatcher found for handling " + pJmxReq);
        }

        JsonConvertOptions opts = getJsonConvertOptions(pJmxReq);

        Object jsonResult =
                jolokiaCtx.getConverters().getToJsonConverter()
                          .convertToJson(result.getValue(), result.getPathParts(), opts);

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
}
