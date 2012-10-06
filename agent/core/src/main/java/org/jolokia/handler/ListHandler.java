package org.jolokia.handler;


import java.io.IOException;
import java.util.*;

import javax.management.*;

import org.jolokia.handler.list.MBeanInfoData;
import org.jolokia.request.JmxListRequest;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.*;

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
    public Object doHandleRequest(Set<MBeanServerConnection> pServers, JmxListRequest pRequest)
            throws InstanceNotFoundException, IOException {
        Stack<String> originalPathStack = EscapeUtil.reversePath(pRequest.getPathParts());

        int maxDepth = getMaxDepth(pRequest);
        ObjectName oName = null;
        try {
            Stack<String> pathStack = (Stack<String>) originalPathStack.clone();
            MBeanInfoData infoMap = new MBeanInfoData(maxDepth,pathStack);

            oName = objectNameFromPath(pathStack);
            if (oName == null || oName.isPattern()) {
                // MBean pattern for MBean can match at multiple servers
                addMBeansFromPattern(infoMap,pServers,oName);
            } else {
                // Fixed name, which can only be registered at a single MBeanServer
                addSingleMBean(infoMap,pServers,oName);
            }
            return infoMap.truncate();
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid path within the MBean part given. (Path: " + pRequest.getPath() + ")",e);
        } catch (InstanceNotFoundException e) {
            throw new IllegalArgumentException("Invalid object name '" + oName + "': Instance not found",e);
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

    // Lookup MBeans from a pattern, and for each found extract the required information
    private void addMBeansFromPattern(MBeanInfoData pInfoMap,
                                      Set<MBeanServerConnection> pServers,
                                      ObjectName pPattern)
            throws IOException, InstanceNotFoundException, IntrospectionException, ReflectionException {
        for (MBeanServerConnection server : pServers) {
            for (Object nameObject : server.queryNames(pPattern,null)) {
                ObjectName name = (ObjectName) nameObject;
                if (!pInfoMap.handleFirstOrSecondLevel(name)) {
                    addMBeanInfo(pInfoMap,server, name);
                }
            }
        }
    }


    // Add a single named MBean's information to the given map
    private void addSingleMBean(MBeanInfoData pInfomap,
                                Set<MBeanServerConnection> pServers,
                                ObjectName pName)
            throws IntrospectionException, ReflectionException, IOException, InstanceNotFoundException {

        if (!pInfomap.handleFirstOrSecondLevel(pName)) {
            InstanceNotFoundException instanceNotFound = null;
            for (MBeanServerConnection server : pServers) {
                try {
                    // Only the first MBeanServer holding the MBean wins
                    addMBeanInfo(pInfomap,server, pName);
                    return;
                } catch (InstanceNotFoundException exp) {
                    instanceNotFound = exp;
                }
            }
            if (instanceNotFound != null) {
                throw instanceNotFound;
            }
        }
    }

    // Extract MBean infos for a given MBean and add results to pResult.
    private void addMBeanInfo(MBeanInfoData pInfoMap, MBeanServerConnection  server, ObjectName pName)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
        try {
            MBeanInfo mBeanInfo = server.getMBeanInfo(pName);
            pInfoMap.addMBeanInfo(mBeanInfo,pName);
        } catch (IOException exp) {
            pInfoMap.handleException(pName,exp);
        } catch (IllegalStateException exp) {
            pInfoMap.handleException(pName,exp);
        }
    }

    private int getMaxDepth(JmxListRequest pRequest) {
        Integer maxDepthI = pRequest.getProcessingConfigAsInt(ConfigKey.MAX_DEPTH);
        return maxDepthI == null ? 0 : maxDepthI;
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
}
