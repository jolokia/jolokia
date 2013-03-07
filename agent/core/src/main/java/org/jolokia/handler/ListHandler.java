package org.jolokia.handler;


import java.io.IOException;
import java.util.Stack;

import javax.management.*;

import org.jolokia.backend.executor.MBeanServerExecutor;
import org.jolokia.backend.executor.NotChangedException;
import org.jolokia.config.ConfigKey;
import org.jolokia.handler.list.MBeanInfoData;
import org.jolokia.request.JmxListRequest;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.*;

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
public class ListHandler extends JsonRequestHandler<JmxListRequest> {


    /** {@inheritDoc} */
    public RequestType getType() {
        return RequestType.LIST;
    }

    /**
     * Constructor
     *
     * @param pRestrictor restrictor to apply
     */
    public ListHandler(Restrictor pRestrictor) {
        super(pRestrictor);
    }

    /**
     * Return true since a list handler needs to merge all information
     *
     * @return always true
     */
    @Override
    public boolean handleAllServersAtOnce(JmxListRequest pRequest) {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    protected void checkForRestriction(JmxListRequest pRequest) {
        checkType();
    }

    /** {@inheritDoc} */
    @Override
    public Object doHandleRequest(MBeanServerExecutor pServerManager, JmxListRequest pRequest)
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

            ListMBeanEachAction action = new ListMBeanEachAction(maxDepth,pathStack,useCanonicalName);
            if (oName == null || oName.isPattern()) {
                pServerManager.each(oName, action);
            } else {
                pServerManager.call(oName,action);
            }

            return action.getResult();
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid path within the MBean part given. (Path: " + pRequest.getPath() + ")",e);
        } catch (InstanceNotFoundException e) {
            throw new IllegalArgumentException("No MBean '" + oName + "' found",e);
        } catch (JMException e) {
            throw new IllegalStateException("Internal error while retrieving list: " + e, e);
        }
    }

    // will not be called

    /** {@inheritDoc} */
    @Override
    public Object doHandleRequest(MBeanServerConnection server, JmxListRequest request)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException {
        throw new UnsupportedOperationException("Internal: Method must not be called when all MBeanServers are handled at once");
    }
    @Override

    /**
     * Path handling is done directly within this handler to avoid
     * excessive memory consumption by building up the whole list
     * into memory only for extracting a part from it. So we return false here.
     *
     * @return always false
     */
    public boolean useReturnValueWithPath() {
        return false;
    }

    // ==========================================================================================================

    // check for freshness
    private void checkForModifiedSince(MBeanServerExecutor pServerManager, JmxListRequest pRequest) throws NotChangedException {
        int ifModifiedSince = pRequest.getParameterAsInt(ConfigKey.IF_MODIFIED_SINCE);
        if (!pServerManager.hasMBeansListChangedSince(ifModifiedSince)) {
            throw new NotChangedException(pRequest);
        }
    }

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
        private final MBeanInfoData infoMap;

        /**
         * Handler used during iterations whe collecting MBean Meta data
         *
         * @param pMaxDepth max depth for the list tree to return
         * @param pPathStack optional stack for picking out a certain path from the list tree
         * @param pUseCanonicalName whether to use a canonical naming for the MBean property lists or the original
         *                          name
         */
        public ListMBeanEachAction(int pMaxDepth, Stack<String> pPathStack, boolean pUseCanonicalName) {
            infoMap = new MBeanInfoData(pMaxDepth,pPathStack,pUseCanonicalName);
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
            if (!infoMap.handleFirstOrSecondLevel(pName)) {
                try {
                    MBeanInfo mBeanInfo = pConn.getMBeanInfo(pName);
                    infoMap.addMBeanInfo(mBeanInfo, pName);
                } catch (IOException exp) {
                    infoMap.handleException(pName, exp);
                } catch (IllegalStateException exp) {
                    infoMap.handleException(pName, exp);
                } catch (IntrospectionException exp) {
                    throw new IllegalArgumentException("Cannot extra MBeanInfo for " + pName + ": " + exp,exp);
                }
            }
        }


        /**
         * Get the overall result
         *
         * @return the meta data suitable for JSON serialization
         */
        public Object getResult() {
            return infoMap.truncate();
        }

    }
}
