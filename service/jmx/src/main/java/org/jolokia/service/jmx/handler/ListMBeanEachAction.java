/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.service.jmx.handler;

import java.io.IOException;
import java.util.Deque;
import java.util.Map;
import java.util.SortedSet;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.jolokia.json.JSONObject;
import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.request.JolokiaListRequest;
import org.jolokia.server.core.service.api.DataUpdater;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.service.jmx.api.CacheKeyProvider;
import org.jolokia.service.jmx.handler.list.MBeanInfoData;

/**
 * <p>Class for handling list queries.</p>
 *
 * <p>This class is responsible for constructing <em>smart list responses</em> which are supported by Jolokia
 * protocol version 8.0 (see <a href="https://github.com/jolokia/jolokia/issues/564">#564</a>).</p>
 */
class ListMBeanEachAction implements MBeanServerAccess.MBeanEachCallback, MBeanServerAccess.MBeanAction<Void> {

    // Meta data which will get collected
    private final MBeanInfoData infoData;
    private final JolokiaContext context;

    private final SortedSet<DataUpdater> customUpdaters;
    private final SortedSet<CacheKeyProvider> cacheKeyProviders;

    /**
     * Handler used during iterations whe collecting MBean Meta data
     *
     * @param pRequest          incoming Jolokia LIST request
     * @param pPathStack        optional stack for picking out a certain path from the list tree
     * @param pProvider         provider to prepend to any domain (if not null)
     * @param pContext          {@link JolokiaContext} for filtering MBeans
     */
    public ListMBeanEachAction(JolokiaListRequest pRequest, Deque<String> pPathStack, String pProvider, JolokiaContext pContext) {
        context = pContext;
        int maxDepth = pRequest.getParameterAsInt(ConfigKey.MAX_DEPTH);
        boolean useCanonicalName = pRequest.getParameterAsBool(ConfigKey.CANONICAL_NAMING);
        boolean listKeys = pRequest.getParameterAsBool(ConfigKey.LIST_KEYS);
        boolean listCache = pRequest.getParameterAsBool(ConfigKey.LIST_CACHE);
        boolean listInterfaces = pRequest.getParameterAsBool(ConfigKey.LIST_INTERFACES);

        // TOCHECK: MBeanInfoData can be filled with pre-cached, long-lived MBeans
        infoData = new MBeanInfoData(maxDepth, pPathStack, useCanonicalName, listKeys, listCache, listInterfaces, pProvider);

        customUpdaters = context.getServices(DataUpdater.class);
        cacheKeyProviders = context.getServices(CacheKeyProvider.class);
    }

    /**
     * Add the given MBean to the collected Meta-Data for an iteration
     *
     * @param pConn connection from where to obtain the meta data
     * @param pInstance object instance of the bean
     * @throws ReflectionException
     * @throws InstanceNotFoundException
     * @throws IOException
     */
    public void callback(MBeanServerConnection pConn, ObjectInstance pInstance)
            throws ReflectionException, InstanceNotFoundException, IOException {
        lookupMBeanInfo(pConn, pInstance);
    }

    /**
     * Add the MBeanInfo for a single MBean
     *
     * @param pConn     MBeanServer on which the action should be performed
     * @param pName     an {@link ObjectName} interpreted specifically by the action
     * @param extraArgs any extra args given as context from the outside
     * @return
     * @throws ReflectionException
     * @throws InstanceNotFoundException
     * @throws IOException
     */
    public Void execute(MBeanServerConnection pConn, ObjectName pName, Object... extraArgs)
            throws ReflectionException, InstanceNotFoundException, IOException {
        lookupMBeanInfo(pConn, new ObjectInstance(pName, null));
        return null;
    }

    private void lookupMBeanInfo(MBeanServerConnection pConn, ObjectInstance pInstance) throws InstanceNotFoundException, ReflectionException, IOException {
        ObjectName objectName = pInstance.getObjectName();
        if (context.isObjectNameHidden(objectName)) {
            return;
        }
        if (!infoData.handleFirstOrSecondLevel(objectName)) {
            try {
                infoData.addMBeanInfo(pConn, pInstance, customUpdaters, cacheKeyProviders);
            } catch (IOException exp) {
                infoData.handleException(objectName, exp);
            } catch (InstanceNotFoundException exp) {
                infoData.handleException(objectName, exp);
            } catch (IllegalStateException exp) {
                infoData.handleException(objectName, exp);
            } catch (IntrospectionException exp) {
                throw new IllegalArgumentException("Cannot extra MBeanInfo for " + objectName + ": " + exp, exp);
            }
        }
    }


    /**
     * Get the overall result and add it to given value. The values from
     * the map of this handlers are copied top level in a given base map
     * (if any was given).
     *
     * @param pBaseMap the base map to merge in the result
     * @return the meta data suitable for JSON serialization
     */
    public Object getResult(JSONObject pBaseMap) {
        Object result = infoData.applyPath();
        if (pBaseMap != null && result instanceof JSONObject resultMap) {
            // Its not a final value, so we merge it in at the top level
            for (Map.Entry<String, Object> entry : resultMap.entrySet()) {
                pBaseMap.put(entry.getKey(), entry.getValue());
            }
            return pBaseMap;
        } else {
            return result;
        }
    }

}
