/*
 * Copyright 2009-2024 Roland Huss
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
import java.util.Set;
import java.util.SortedSet;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.util.jmx.MBeanServerAccess;
import org.jolokia.service.jmx.api.CacheKeyProvider;
import org.jolokia.server.core.service.api.DataUpdater;
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
     * @param pMaxDepth         max depth for the list tree to return
     * @param pPathStack        optional stack for picking out a certain path from the list tree
     * @param pUseCanonicalName whether to use a canonical naming for the MBean property lists or the original
     * @param pListKeys         whether to dissect {@link ObjectName#getKeyPropertyList()} into MBean information
     * @param pListCache        whether optimized {@code list()} operation is performed
     * @param listInterfaces
     * @param pProvider         provider to prepend to any domain (if not null)
     * @param pContext          {@link JolokiaContext} for filtering MBeans
     */
    public ListMBeanEachAction(int pMaxDepth, Deque<String> pPathStack, boolean pUseCanonicalName,
                               boolean pListKeys, boolean pListCache, boolean listInterfaces, String pProvider, JolokiaContext pContext) {
        context = pContext;
        // TOCHECK: MBeanInfoData can be filled with pre-cached, long-lived MBeans
        infoData = new MBeanInfoData(pMaxDepth, pPathStack, pUseCanonicalName, pListKeys, pListCache, listInterfaces, pProvider);
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
     * @param pName     an objectname interpreted specifically by the action
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
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Object getResult(Map pBaseMap) {

        Object result = infoData.applyPath();
        if (pBaseMap != null && result instanceof Map) {
            // Its not a final value, so we merge it in at the top level
            Map resultMap = (Map) result;
            for (Map.Entry entry : (Set<Map.Entry>) resultMap.entrySet()) {
                pBaseMap.put(entry.getKey(), entry.getValue());
            }
            return pBaseMap;
        } else {
            return result;
        }
    }
}
