package org.jolokia.handler;


import java.io.IOException;
import java.util.*;

import javax.management.*;

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
 * <p/>
 * TODO: If the path is used here directly,
 * no issue should be the order of attributes. Think also to use the MBean name directly as
 * first level and not domain as the first level and the attribute list as second level. This way,
 * the output of a search command could be used directly to obtain the meta information for a single
 * bean directly (however, fetching all meta info for a whole domain is not possible anymore this way but
 * could be approached with a bulk-list request)
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class ListHandler extends JsonRequestHandler<JmxListRequest> {

    // Properties for JSON answer
    private static final String KEY_DESCRIPTION = "desc";
    private static final String KEY_ERROR = "error";
    private static final String KEY_NAME = "name";
    private static final String KEY_TYPES = "types";
    private static final String KEY_ARGS = "args";
    private static final String KEY_RETURN = "ret";
    private static final String KEY_OPERATION = "op";
    private static final String KEY_TYPE = "type";
    private static final String KEY_NOTIFICATION = "not";
    private static final String KEY_READ_WRITE = "rw";
    private static final String KEY_ATTRIBUTE = "attr";

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
        Stack<String> originalPathStack = PathUtil.reversePath(pRequest.getPathParts());

        int maxDepth = getMaxDepth(pRequest);
        ObjectName oName = null;
        try {
            Map infoMap = new HashMap();

            Stack<String> pathStack = (Stack<String>) originalPathStack.clone();

            // Prepare an objectname patttern from a path (or "*:*" if no pattern is given)
            oName = objectNameFromPath(pathStack);
            if (oName != null && !oName.isPattern()) {
                // Fixed name, which can only occur at a single MBeanServer
                addSingleMBean(pServers, oName, infoMap, maxDepth, pathStack);
            } else {
                // MBean pattern for MBean occuring at multiple servers
                addMBeansFromPattern(pServers, oName, infoMap, maxDepth, pathStack);
            }
            return truncateAccordingToPath(infoMap, originalPathStack.size(), maxDepth);
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
    private void addMBeansFromPattern(Set<MBeanServerConnection> pServers,
                                      ObjectName pPattern,
                                      Map pInfoMap,
                                      int pMaxDepth,
                                      Stack<String> pPathStack)
            throws IOException, InstanceNotFoundException, IntrospectionException, ReflectionException {
        for (MBeanServerConnection server : pServers) {
            for (Object nameObject : server.queryNames(pPattern,null)) {
                ObjectName name = (ObjectName) nameObject;
                if (!handleFirstOrSecondLevel(name, pInfoMap, pMaxDepth, pPathStack.size())) {
                    addMBeanInfo(server, pInfoMap, name, pPathStack);
                }
            }
        }
    }

    // Add a single named MBean's information to the given map
    private void addSingleMBean(Set<MBeanServerConnection> pServers,
                                ObjectName pName,
                                Map pInfoMap,
                                int pMaxDepth,
                                Stack<String> pPathStack)
            throws IntrospectionException, ReflectionException, IOException, InstanceNotFoundException {

        if (!handleFirstOrSecondLevel(pName, pInfoMap, pMaxDepth, pPathStack.size())) {
            InstanceNotFoundException instanceNotFound = null;
            for (MBeanServerConnection server : pServers) {
                Stack<String> pathStack = (Stack<String>) pPathStack.clone();
                try {
                    addMBeanInfo(server, pInfoMap, pName, pathStack);
                    // Only the first MBeanServer holding the MBean wins
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

    // Check, whether a maxDepth dictates the extraction of only the first or second layer. If so, a special
    // treatment is required
    private boolean handleFirstOrSecondLevel(ObjectName pName, Map pInfoMap, int pMaxDepth, int pStackSize) {
        if (pMaxDepth == 1 && pStackSize == 0) {
            // Only add domain names with a dummy value if max depth is restricted to 1
            // But only when used without path
            pInfoMap.put(pName.getDomain(),1);
            return true;
        } else if (pMaxDepth == 2 && pStackSize == 0) {
            // Add domain an object name into the map, final value is a dummy value
            Map mBeansMap = getOrCreateMap(pInfoMap, pName.getDomain());
            mBeansMap.put(pName.getCanonicalKeyPropertyListString(),1);
            return true;
        }
        return false;
    }




    // Extract MBean infos for a given MBean and add results to pResult.
    private void addMBeanInfo(MBeanServerConnection server, Map pResult, ObjectName pName, Stack<String> pPathStack)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {
        int stackSize = pPathStack.size();
        Map mBeansMap = getOrCreateMap(pResult, pName.getDomain());
        Map mBeanMap = getOrCreateMap(mBeansMap, pName.getCanonicalKeyPropertyListString());
        try {
            MBeanInfo mBeanInfo = server.getMBeanInfo(pName);
            if (pPathStack.empty()) {
                addFullMBeanInfo(mBeanMap, mBeanInfo);
            } else {
                addPartialMBeanInfo(mBeanMap, mBeanInfo, pPathStack);
            }
            // Trim if required
            if (mBeanMap.size() == 0) {
                mBeansMap.remove(pName.getCanonicalKeyPropertyListString());
                if (mBeansMap.size() == 0) {
                    pResult.remove(pName.getDomain());
                }
            }
        } catch (IOException exp) {
            // In case of a remote call, IOException can occur e.g. for
            // NonSerializableExceptions
             if (stackSize == 0) {
                // There are more MBean infos included
                mBeanMap.put(KEY_ERROR, exp);
            } else {
                 // Happens for a deeper request, i.e with a path pointing directly into an MBean,
                 // Hence we throw immediately an error here since there will be only this exception
                 // and no extra info
                 throw exp;
            }
        }
    }

    private void addFullMBeanInfo(Map pMBeanMap, MBeanInfo pMBeanInfo) {
        pMBeanMap.put(KEY_DESCRIPTION, pMBeanInfo.getDescription());
        addAttributes(pMBeanMap, pMBeanInfo, null);
        addOperations(pMBeanMap, pMBeanInfo, null);
        addNotifications(pMBeanMap, pMBeanInfo, null);
    }

    private int getMaxDepth(JmxListRequest pRequest) {
        Integer maxDepthI = pRequest.getProcessingConfigAsInt(ConfigKey.MAX_DEPTH);
        return maxDepthI == null ? 0 : maxDepthI;
    }

    private Object truncateAccordingToPath(Map pInfoMap, int pStackSize, final int pMaxDepth) {
        Object value = navigatePath(pInfoMap,pStackSize);
        if (pMaxDepth == 0) {
            return value;
        }
        if (! (value instanceof Map)) {
            return value;
        } else {
            // Truncate all levels below
            return truncateMap((Map) value,pMaxDepth);
        }
    }

    private Object truncateMap(Map pValue, int pMaxDepth) {
        if (pMaxDepth == 0) {
            return 1;
        }
        Map ret = new HashMap();
        Set<Map.Entry> entries = pValue.entrySet();
        for (Map.Entry entry : entries) {
            Object value = entry.getValue();
            Object key = entry.getKey();
            if (value instanceof Map) {
                ret.put(key,truncateMap((Map) value,pMaxDepth - 1));
            } else {
                ret.put(key,value);
            }
        }
        return ret;
    }

    private Object navigatePath(Map pInfoMap,int pStackSize) {
        Map ret = pInfoMap;
        int size = pStackSize;
        while (size > 0) {
            Collection vals = ret.values();
            if (vals.size() == 0) {
                return ret;
            } else if (vals.size() != 1) {
                throw new IllegalStateException("Internal: More than one key found when extracting with path: " + vals);
            }
            Object value = vals.iterator().next();

            // End leaf, return it ....
            if (size == 1) {
                return value;
            }
            // Dive in deeper ...
            if (!(value instanceof Map)) {
                throw new IllegalStateException("Internal: Value within path extraction must be a Map, not " + value.getClass());
            }
            ret = (Map) value;
            --size;
        }
        return ret;
    }

    private void addPartialMBeanInfo(Map pMBeanMap, MBeanInfo pMBeanInfo, Stack<String> pPathStack) {
        String what = pPathStack.empty() ? null : pPathStack.pop();
        if (KEY_DESCRIPTION.equals(what)) {
            pMBeanMap.put(KEY_DESCRIPTION, pMBeanInfo.getDescription());
        } else if (KEY_ATTRIBUTE.equals(what)) {
            addAttributes(pMBeanMap, pMBeanInfo, popOrNull(pPathStack));
        } else if (KEY_OPERATION.equals(what)) {
            addOperations(pMBeanMap, pMBeanInfo, popOrNull(pPathStack));
        } else if (KEY_NOTIFICATION.equals(what)) {
            addNotifications(pMBeanMap, pMBeanInfo, popOrNull(pPathStack));
        } else {
            throw new IllegalArgumentException("Illegal path element " + what);
        }
    }


    private ObjectName objectNameFromPath(Stack<String> pPathStack) throws MalformedObjectNameException {
        if (pPathStack.empty()) {
            return null;
        }
        String domain = pPathStack.pop();
        if (pPathStack.empty()) {
            return new ObjectName(domain + ":*");
        }
        String props = pPathStack.pop();
        ObjectName mbean = new ObjectName(domain + ":" + props);
        if (mbean.isPattern()) {
            throw new IllegalArgumentException("Cannot use an MBean pattern as path (given MBean: " + mbean + ")");
        }
        return mbean;
    }

    private void addAttributes(Map pMBeanMap, MBeanInfo pMBeanInfo, String pAttributeFilter) {
        // Extract attributes
        Map attrMap = new HashMap();
        for (MBeanAttributeInfo attrInfo : pMBeanInfo.getAttributes()) {
            if (pAttributeFilter == null || attrInfo.getName().equals(pAttributeFilter)) {
                Map map = new HashMap();
                map.put(KEY_TYPE, attrInfo.getType());
                map.put(KEY_DESCRIPTION, attrInfo.getDescription());
                map.put(KEY_READ_WRITE, Boolean.valueOf(attrInfo.isWritable() && attrInfo.isReadable()));
                attrMap.put(attrInfo.getName(), map);
            }
        }
        updateMapConsideringPathError(KEY_ATTRIBUTE,pMBeanMap, attrMap, pAttributeFilter);
    }

    private void addOperations(Map pMBeanMap, MBeanInfo pMBeanInfo, String pOperationFilter) {
        // Extract operations
        Map opMap = new HashMap();
        for (MBeanOperationInfo opInfo : pMBeanInfo.getOperations()) {
            if (pOperationFilter == null || opInfo.getName().equals(pOperationFilter)) {
                Map map = new HashMap();
                List argList = new ArrayList();
                for (MBeanParameterInfo paramInfo : opInfo.getSignature()) {
                    Map args = new HashMap();
                    args.put(KEY_DESCRIPTION, paramInfo.getDescription());
                    args.put(KEY_NAME, paramInfo.getName());
                    args.put(KEY_TYPE, paramInfo.getType());
                    argList.add(args);
                }
                map.put(KEY_ARGS, argList);
                map.put(KEY_RETURN, opInfo.getReturnType());
                map.put(KEY_DESCRIPTION, opInfo.getDescription());
                Object ops = opMap.get(opInfo.getName());
                if (ops != null) {
                    if (ops instanceof List) {
                        // If it is already a list, simply add it to the end
                        ((List) ops).add(map);
                    } else if (ops instanceof Map) {
                        // If it is a map, add a list with two elements
                        // (the old one and the new one)
                        List opList = new ArrayList();
                        opList.add(ops);
                        opList.add(map);
                        opMap.put(opInfo.getName(), opList);
                    } else {
                        throw new IllegalArgumentException("Internal: list, addOperations: Expected Map or List, not "
                                                                   + ops.getClass());
                    }
                } else {
                    // No value set yet, simply add the map as plain value
                    opMap.put(opInfo.getName(), map);
                }
            }
        }
        updateMapConsideringPathError(KEY_OPERATION,pMBeanMap, opMap, pOperationFilter);
    }

    private void addNotifications(Map pMBeanMap, MBeanInfo pMBeanInfo, String pNotificationFilter) {
        Map notMap = new HashMap();
        for (MBeanNotificationInfo notInfo : pMBeanInfo.getNotifications()) {
            if (pNotificationFilter == null || notInfo.getName().equals(pNotificationFilter)) {
                Map map = new HashMap();
                map.put(KEY_NAME, notInfo.getName());
                map.put(KEY_DESCRIPTION, notInfo.getDescription());
                map.put(KEY_TYPES, notInfo.getNotifTypes());
            }
        }
        updateMapConsideringPathError(KEY_NOTIFICATION,pMBeanMap, notMap, pNotificationFilter);
    }



    // Add a map, but also check when a path is given, and the map is empty, then trow an error
    private void updateMapConsideringPathError(String pType,Map pMap, Map pToAdd, String pPathPart) {
        if (pToAdd.size() > 0) {
            pMap.put(pType, pToAdd);
        } else if (pPathPart != null) {
            throw new IllegalArgumentException("Invalid attribute path provided (element '" + pPathPart + "' not found)");
        }
    }

    private Map getOrCreateMap(Map pMap, String pKey) {
        Map nMap = (Map) pMap.get(pKey);
        if (nMap == null) {
            nMap = new HashMap();
            pMap.put(pKey, nMap);
        }
        return nMap;
    }

    private String popOrNull(Stack<String> pPathStack) {
        return pPathStack.empty() ? null : pPathStack.pop();
    }

}
