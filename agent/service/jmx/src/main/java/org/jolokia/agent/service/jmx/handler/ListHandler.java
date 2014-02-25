package org.jolokia.agent.service.jmx.handler;


import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.jolokia.core.util.jmx.MBeanServerExecutor;
import org.jolokia.core.backend.NotChangedException;
import org.jolokia.core.config.ConfigKey;
import org.jolokia.agent.service.jmx.handler.list.MBeanInfoData;
import org.jolokia.core.request.JolokiaListRequest;
import org.jolokia.core.service.JolokiaContext;
import org.jolokia.core.util.*;
import org.json.simple.JSONObject;

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
 * Handler for obtaining a list of all available MBeans and its attributes
 * and operations.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class ListHandler extends CommandHandler<JolokiaListRequest> {

    // Realm used as a prefix
    private String realm;

    /** {@inheritDoc} */
    public RequestType getType() {
        return RequestType.LIST;
    }

    /**
     * Constructor
     *
     * @param pContext jolokia context
     * @param pRealm realm to prepend to all objects names in the returned meta data
     */
    public ListHandler(JolokiaContext pContext, String pRealm) {
        super(pContext);
        this.realm = pRealm;
    }

    /**
     * Return true since a list handler needs to merge all information
     *
     * @return always true
     */
    @Override
    public boolean handleAllServersAtOnce(JolokiaListRequest pRequest) {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected void checkForRestriction(JolokiaListRequest pRequest) {
        checkType();
    }

    // pPreviousResult must be a Map according to the "list" data format specification
    /** {@inheritDoc} */
    @Override
    public Object doHandleRequest(MBeanServerExecutor pServerManager, JolokiaListRequest pRequest, Object pPreviousResult)
            throws IOException, NotChangedException {
        // Throw an exception if list has not changed
        checkForModifiedSince(pServerManager, pRequest);

        Stack<String> originalPathStack = EscapeUtil.reversePath(pRequest.getPathParts());
        int maxDepth = pRequest.getParameterAsInt(ConfigKey.MAX_DEPTH);
        boolean useCanonicalName = pRequest.getParameterAsBool(ConfigKey.CANONICAL_NAMING);

        ObjectName oName = null;
        try {
            Stack<String> pathStack = (Stack<String>) originalPathStack.clone();
            oName = objectNameFromPath(pathStack);

            if (oName != null) {
                if (RealmUtil.matchesRealm(realm, oName)) {
                    oName = RealmUtil.extractRealm(oName).getObjectName();
                } else {
                    return pPreviousResult != null ? pPreviousResult : new JSONObject();
                }
            }

            ListMBeanEachAction action = new ListMBeanEachAction(maxDepth,pathStack,useCanonicalName,realm);
            return executeListAction(pServerManager, (Map) pPreviousResult, oName, action);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid path within the MBean part given. (Path: " + pRequest.getPath() + ")",e);
        } catch (InstanceNotFoundException e) {
            throw new IllegalArgumentException("No MBean '" + oName + "' found",e);
        } catch (JMException e) {
            throw new IllegalStateException("Internal error while retrieving list: " + e, e);
        }
    }

    private Object executeListAction(MBeanServerExecutor pServerManager, Map pPreviousResult, ObjectName pName, ListMBeanEachAction pAction)
            throws IOException, ReflectionException, MBeanException, AttributeNotFoundException, InstanceNotFoundException {
        if (pName == null || pName.isPattern()) {
            pServerManager.each(pName, pAction);
        } else {
            pServerManager.call(pName, pAction);
        }
        return pAction.getResult((Map) pPreviousResult);
    }

    /** {@inheritDoc} */
    @Override
    public Object doHandleRequest(MBeanServerConnection server, JolokiaListRequest request)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException {
        throw new UnsupportedOperationException("Internal: Method must not be called when all MBeanServers are handled at once");
    }

    // ==========================================================================================================

    /**
     * Prepare an objectname patttern from a path (or "null" if no path is given)
     * @param pPathStack path
     * @return created object name (either plain or a pattern)
     */
    private ObjectName objectNameFromPath(Stack<String> pPathStack) throws MalformedObjectNameException {
        if (pPathStack.empty()) {
            return null;
        }
        Stack<String> path = (Stack<String>) pPathStack.clone();
        String domain = path.pop();
        if (path.empty()) {
            return new ObjectName(domain + ":*");
        }
        String props = path.pop();
        ObjectName mbean = new ObjectName(domain + ":" + props);
        if (mbean.isPattern()) {
            throw new IllegalArgumentException("Cannot use an MBean pattern as path (given MBean: " + mbean + ")");
        }
        return mbean;
    }

    // Class for handling list queries
    private static class ListMBeanEachAction implements MBeanServerExecutor.MBeanEachCallback, MBeanServerExecutor.MBeanAction<Void> {

        // Meta data which will get collected
        private final MBeanInfoData infoData;

        /**
         * Handler used during iterations whe collecting MBean Meta data
         *
         * @param pMaxDepth max depth for the list tree to return
         * @param pPathStack optional stack for picking out a certain path from the list tree
         * @param pUseCanonicalName whether to use a canonical naming for the MBean property lists or the original
         * @param pRealm realm to prepend to any domain (if not null)
         */
        public ListMBeanEachAction(int pMaxDepth, Stack<String> pPathStack, boolean pUseCanonicalName, String pRealm) {
            infoData = new MBeanInfoData(pMaxDepth,pPathStack,pUseCanonicalName,pRealm);
        }

        /**
         * Add the given MBean to the collected Meta-Data for an iteration
         *
         * @param pConn connection from where to obtain the meta data
         * @param pName object name of the bean
         * @throws ReflectionException
         * @throws InstanceNotFoundException
         * @throws IOException
         */
        public void callback(MBeanServerConnection pConn, ObjectName pName)
                throws ReflectionException, InstanceNotFoundException, IOException, MBeanException {
            lookupMBeanInfo(pConn, pName);
        }

        /**
         * Add the MBeanInfo for a single MBean
         *
         * @param pConn MBeanServer on which the action should be performed
         * @param pName an objectname interpreted specifically by the action
         * @param extraArgs any extra args given as context from the outside
         * @return
         * @throws ReflectionException
         * @throws InstanceNotFoundException
         * @throws IOException
         * @throws MBeanException
         * @throws AttributeNotFoundException
         */
        public Void execute(MBeanServerConnection pConn, ObjectName pName, Object... extraArgs)
                throws ReflectionException, InstanceNotFoundException, IOException, MBeanException, AttributeNotFoundException {
            lookupMBeanInfo(pConn, pName);
            return null;
        }

        private void lookupMBeanInfo(MBeanServerConnection pConn, ObjectName pName) throws InstanceNotFoundException, ReflectionException, IOException {
            if (!infoData.handleFirstOrSecondLevel(pName)) {
                try {
                    MBeanInfo mBeanInfo = pConn.getMBeanInfo(pName);
                    infoData.addMBeanInfo(mBeanInfo, pName);
                } catch (IOException exp) {
                    infoData.handleException(pName, exp);
                } catch (IllegalStateException exp) {
                    infoData.handleException(pName, exp);
                } catch (IntrospectionException exp) {
                    throw new IllegalArgumentException("Cannot extra MBeanInfo for " + pName + ": " + exp,exp);
                }
            }
        }


        /**
         * Get the overall result and add it to given value. The values from
         * the map of this handlers are copied top level in a given base map
         * (if any was given).
         *
         * @return the meta data suitable for JSON serialization
         * @param pBaseMap the base map to merge in the result
         */
        public Object getResult(Map pBaseMap) {

            Object result = infoData.applyPath();
            if (pBaseMap != null && result instanceof Map) {
                // Its not a final value, so we merge it in at the top level
                Map resultMap = (Map) result;
                for (Map.Entry entry : (Set<Map.Entry>) resultMap.entrySet()) {
                    pBaseMap.put(entry.getKey(),entry.getValue());
                }
                return pBaseMap;
            } else {
                return result;
            }
        }
    }
}
