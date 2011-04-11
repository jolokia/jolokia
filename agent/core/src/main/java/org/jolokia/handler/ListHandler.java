package org.jolokia.handler;


import org.jolokia.request.*;
import org.jolokia.restrictor.Restrictor;
import org.jolokia.util.*;

import javax.management.*;

import java.io.IOException;
import java.util.*;

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
 * TODO: Optimize when a path is already given so that only the relevant information
 * is looked up (and the path) removed. Currently, all data is looked up and is truncated
 * afterwards after this handler has been finished. Also, if the path is used here directly,
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

    public RequestType getType() {
        return RequestType.LIST;
    }

    public ListHandler(Restrictor pRestrictor) {
        super(pRestrictor);
    }

    @Override
    public boolean handleAllServersAtOnce(JmxListRequest pRequest) {
        return true;
    }

    @Override
    public Object doHandleRequest(Set<MBeanServerConnection> pServers, JmxListRequest pRequest)
            throws InstanceNotFoundException, IOException {
        Stack<String> originalPathStack = PathUtil.reversePath(pRequest.getPathParts());

        Integer maxDepthI = pRequest.getProcessingConfigAsInt(ConfigKey.MAX_DEPTH);
        int maxDepth = maxDepthI == null ? 0 : maxDepthI;

        try {
            Map infoMap = new HashMap();
            for (MBeanServerConnection server : pServers) {
                Stack<String> pathStack = (Stack<String>) originalPathStack.clone();
                int stackSize = pathStack.size();

                // Prepare an objectname patttern from a path (or "*:*" if no pattern is given)
                ObjectName oName = objectNameFromPath(pathStack);


                for (Object nameObject : queryMBeans(server, oName)) {
                    ObjectName name = (ObjectName) nameObject;

                    if (maxDepth == 1 && stackSize == 0) {
                        // Only add domain names with a dummy value if max depth is restricted to 1
                        // But only when used without path
                        infoMap.put(name.getDomain(),1);

                    } else if (maxDepth == 2 && stackSize == 0) {
                        // Add domain an object name into the map, final value is a dummy value
                        Map mBeansMap = getOrCreateMap(infoMap, name.getDomain());
                        mBeansMap.put(name.getCanonicalKeyPropertyListString(),1);
                    } else {
                        Map mBeansMap = getOrCreateMap(infoMap, name.getDomain());
                        Map mBeanMap = getOrCreateMap(mBeansMap, name.getCanonicalKeyPropertyListString());
                        try {
                            MBeanInfo mBeanInfo = server.getMBeanInfo(name);
                            if (pathStack.empty()) {
                                mBeanMap.put(KEY_DESCRIPTION, mBeanInfo.getDescription());
                                addAttributes(mBeanMap, mBeanInfo, pathStack);
                                addOperations(mBeanMap, mBeanInfo, pathStack);
                                addNotifications(mBeanMap, mBeanInfo, pathStack);
                            } else {
                                addPartialMBeanInfo(pRequest, pathStack, mBeanMap, mBeanInfo);
                            }
                            // Trim if required
                            if (mBeanMap.size() == 0) {
                                mBeansMap.remove(name.getCanonicalKeyPropertyListString());
                                if (mBeansMap.size() == 0) {
                                    infoMap.remove(name.getDomain());
                                }
                            }
                        } catch (IOException exp) {
                            // In case of a remote call, IOException can occur e.g. for
                            // NonSerializableExceptions
                            if (stackSize <= 2) {
                                // There are more MBean infos included
                                mBeanMap.put(KEY_ERROR, exp);
                            } else {
                                // Happens for a deeper request, hence we throw immediately
                                // an error here

                            }
                        }
                    }
                }
            }
            return trunkAccordingToPath(infoMap, originalPathStack.size(), maxDepth);
        } catch (ReflectionException e) {
            throw new IllegalStateException("Internal error while retrieving list: " + e, e);
        } catch (IntrospectionException e) {
            throw new IllegalStateException("Internal error while retrieving list: " + e, e);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid path within the MBean part given. (Path: " + pRequest.getPath() + ")");
        }

    }

    private Object trunkAccordingToPath(Map pInfoMap, int pStackSize, int pMaxDepth) {
        Map ret = pInfoMap;
        int maxDepth = pMaxDepth - 1;
        while (pStackSize > 0) {
            Collection vals = ret.values();
            if (vals.size() != 1) {
                throw new IllegalStateException("Internal: More than one key found when extracting with path: " + vals);
            }
            Object value = vals.iterator().next();

            // End leaf, return it ....
            if (pStackSize == 1) {
                return value;
            }
            // Dive in deeper ...
            if (!(value instanceof Map)) {
                throw new IllegalStateException("Internal: Value within path extraction must be a Map, not " + value.getClass());
            }
            ret = (Map) value;
            // Truncate the level below
            if (maxDepth == 0) {
                Map trunc = new HashMap();
                for (Object key : ret.keySet()) {
                    trunc.put(key,1);
                }
                return trunc;
            }

            --pStackSize;
            --maxDepth;
        }
        return ret;
    }

    private void addPartialMBeanInfo(JmxListRequest request, Stack<String> pPathStack, Map pMBeanMap, MBeanInfo pMBeanInfo) {
        String what = pPathStack.empty() ? null : pPathStack.pop();
        if (KEY_DESCRIPTION.equals(what)) {
            pMBeanMap.put(KEY_DESCRIPTION, pMBeanInfo.getDescription());
        } else if (KEY_ATTRIBUTE.equals(what)) {
            addAttributes(pMBeanMap, pMBeanInfo, pPathStack);
        } else if (KEY_OPERATION.equals(what)) {
            addOperations(pMBeanMap, pMBeanInfo, pPathStack);
        } else if (KEY_NOTIFICATION.equals(what)) {
            addNotifications(pMBeanMap, pMBeanInfo, pPathStack);
        } else {
            throw new IllegalArgumentException("Illegal path element " + what + " within path " + request.getPath());
        }
    }

    private Set<ObjectName> queryMBeans(MBeanServerConnection pServer, ObjectName pName) throws IOException {
        if (pName == null) {
            return pServer.queryNames(null, null);
        } else if (pName.isPattern()) {
            return pServer.queryNames(pName, (QueryExp) null);
        } else {
            return new HashSet<ObjectName>(Arrays.asList(pName));
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

    private void addNotifications(Map pMBeanMap, MBeanInfo pMBeanInfo, Stack<String> pPathStack) {
        String what = pPathStack.empty() ? null : pPathStack.pop();

        Map notMap = new HashMap();
        for (MBeanNotificationInfo notInfo : pMBeanInfo.getNotifications()) {
            if (what == null || notInfo.getName().equals(what)) {
                Map map = new HashMap();
                map.put(KEY_NAME, notInfo.getName());
                map.put(KEY_DESCRIPTION, notInfo.getDescription());
                map.put(KEY_TYPES, notInfo.getNotifTypes());
            }
        }
        if (notMap.size() > 0) {
            pMBeanMap.put(KEY_NOTIFICATION, notMap);
        }
    }

    private void addOperations(Map pMBeanMap, MBeanInfo pMBeanInfo, Stack<String> pPathStack) {
        String what = pPathStack.empty() ? null : pPathStack.pop();

        // Extract operations
        Map opMap = new HashMap();
        for (MBeanOperationInfo opInfo : pMBeanInfo.getOperations()) {
            if (what == null || opInfo.getName().equals(what)) {
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
        if (opMap.size() > 0) {
            pMBeanMap.put(KEY_OPERATION, opMap);
        }
    }

    private void addAttributes(Map pMBeanMap, MBeanInfo pMBeanInfo, Stack<String> pPathStack) {
        // Extract attributes
        Map attrMap = new HashMap();
        String what = pPathStack.empty() ? null : pPathStack.pop();
        for (MBeanAttributeInfo attrInfo : pMBeanInfo.getAttributes()) {
            if (what == null || attrInfo.equals(what)) {
                Map map = new HashMap();
                map.put(KEY_TYPE, attrInfo.getType());
                map.put(KEY_DESCRIPTION, attrInfo.getDescription());
                map.put(KEY_READ_WRITE, Boolean.valueOf(attrInfo.isWritable() && attrInfo.isReadable()));
                attrMap.put(attrInfo.getName(), map);
            }
        }
        if (attrMap.size() > 0) {
            pMBeanMap.put(KEY_ATTRIBUTE, attrMap);
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

    // will not be called
    @Override
    public Object doHandleRequest(MBeanServerConnection server, JmxListRequest request)
            throws InstanceNotFoundException, AttributeNotFoundException, ReflectionException, MBeanException {
        throw new UnsupportedOperationException("Internal: Method must not be called when all MBeanServers are handled at once");
    }

    @Override
    // Path handling is done directly within this handler to avoid
    // excessive memory consumption by building up the whole list
    // into memory only for extracting a part from it
    public boolean useReturnValueWithPath() {
        return false;
    }
}
