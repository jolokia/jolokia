package org.jolokia.handler;

/*
 * Copyright 2009-2011 Roland Huss
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

import java.io.IOException;
import java.util.*;

import javax.management.*;

/**
 * Tree of MBean meta data. This map is a container for one or more MBeanInfo meta data which can be obtained
 * via a <code>list</code> request. The full structure in its JSON representation looks like below. The amount
 * of data included can be fine tuned in two ways:
 * <ul>
 *     <li>With a <code>maxDepth</code> parameter given at construction time, the size of the map can be restricted
 *     (from top down)</li>
 *     <li>A given path select onkly a partial information from the tree</li>
 * </ul>
 * Both limiting factors are taken care of when adding the information so that this map doesnt get unnecessarily
 * to large.
 *
 * <pre>
 * {
 *  &lt;domain&gt; :
 *  {
 *    &lt;prop list&gt; :
 *    {
 *     "attr" :
 *     {
 *       &lt;attr name&gt; :
 *       {
 *         "type" : &lt;attribute type&gt;,
 *         "desc" : &lt;textual description of attribute&gt;,
 *         "rw"   : true/false
 *       },
 *       ....
 *     },
 *     "op" :
 *     {
 *        &lt;operation name&gt; :
 *        {
 *          "args" : [
 *                     {
 *                      "type" : &lt;argument type&gt;
 *                      "name" : &lt;argument name&gt;
 *                      "desc" : &lt;textual description of argument&gt;
 *                     },
 *                     .....
 *                    ],
 *          "ret"  : &lt;return type&gt;,
 *          "desc" : &lt;textual description of operation&gt;
 *        },
 *        .....
 *     },
 *     "not" :
 *     {
 *        "name" : &lt;name&gt;,
 *        "desc" : &lt;desc&gt;,
 *        "types" : [ &lt;type1&gt;, &lt;type2&gt; ]
 *     }
 *    },
 *    ....
 *  },
 *   ....
 * }
 * </pre>
 * @author roland
 * @since 13.09.11
 */
public class MBeanInfoMap {

    // max depth for map to return
    private int maxDepth;

    // stack for an inner path
    private Stack<String> pathStack;

    // Map holding information
    private Map infoMap;

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

    /**
     * Constructor taking a max depth. The <em>max depth</em> specifies how deep the info tree should be build
     * up. The tree will be truncated if it gets larger than this value. A <em>path</em> (in form of a stack)
     * can be given, in which only a sub information is (sub-tree or leaf value) is stored
     *
     * @param pMaxDepth max depth
     * @param pPathStack the stack for restricting the information to add
     */
    public MBeanInfoMap(int pMaxDepth, Stack<String> pPathStack) {
        maxDepth = pMaxDepth;
        pathStack = pPathStack != null ? (Stack<String>) pPathStack.clone() : new Stack<String>();
        infoMap = new HashMap();
    }

    /**
     * The first two levels of this map (tree) consist of the MBean's domain name and name properties, which are
     * independent of an MBean's meta data. If the max depth given at construction time is less or equals than 2 (and
     * no inner path into the map is given), then a client of this map does not need to query the MBeanServer for
     * MBeanInfo meta data.
     * <p></p>
     * This method checks this condition and returns true if this is the case. As side effect it will update this
     * map with the name part extracted from the given object name
     *
     * @param pName the objectname used for the first two levels
     * @return true if the object name has been added.
     */
    public boolean handleFirstOrSecondLevel(ObjectName pName) {
        if (maxDepth == 1 && pathStack.size() == 0) {
            // Only add domain names with a dummy value if max depth is restricted to 1
            // But only when used without path
            infoMap.put(pName.getDomain(), 1);
            return true;
        } else if (maxDepth == 2 && pathStack.size() == 0) {
            // Add domain an object name into the map, final value is a dummy value
            Map mBeansMap = getOrCreateMap(infoMap, pName.getDomain());
            mBeansMap.put(pName.getCanonicalKeyPropertyListString(),1);
            return true;
        }
        return false;
    }

    /**
     * Add information about an MBean as obtained from an {@link MBeanInfo} descriptor. The information added
     * can be restricted by a given path (which has already be prepared as a stack). Also, a max depth as given in the
     * constructor restricts the size of the map from the top.
     *
     * @param mBeanInfo the MBean info
     * @param pName the object name of the MBean
     */
    public void addMBeanInfo(MBeanInfo mBeanInfo, ObjectName pName)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {

        Map mBeansMap = getOrCreateMap(infoMap, pName.getDomain());
        Map mBeanMap = getOrCreateMap(mBeansMap, pName.getCanonicalKeyPropertyListString());
        // Trim down stack to get rid of domain/property list
        Stack<String> stack = truncatePathStack(2);
        if (stack.empty()) {
            addFullMBeanInfo(mBeanMap, mBeanInfo);
        } else {
            // pathStack gets cloned here since the processing will eat it up
            addPartialMBeanInfo(mBeanMap, mBeanInfo,stack);
        }
        // Trim if required
        if (mBeanMap.size() == 0) {
            mBeansMap.remove(pName.getCanonicalKeyPropertyListString());
            if (mBeansMap.size() == 0) {
                infoMap.remove(pName.getDomain());
            }
        }
    }

    /**
     * Add an exception which occurred during extraction of an {@link MBeanInfo} for
     * a certain {@link ObjectName} to this map.
     *
     * @param pName MBean name for which the error occurred
     * @param pExp exception occurred
     * @throws IOException if this method decides to rethrow the execption
     */
    public void handleException(ObjectName pName, IOException pExp) throws IOException {
        // In case of a remote call, IOException can occur e.g. for
        // NonSerializableExceptions
             if (pathStack.size() == 0) {
                 Map mBeansMap = getOrCreateMap(infoMap, pName.getDomain());
                 Map mBeanMap = getOrCreateMap(mBeansMap, pName.getCanonicalKeyPropertyListString());
                 mBeanMap.put(KEY_ERROR, pExp);
             } else {
                 // Happens for a deeper request, i.e with a path pointing directly into an MBean,
                 // Hence we throw immediately an error here since there will be only this exception
                 // and no extra info
                 throw pExp;
            }
    }

    /**
     * Extract either a sub tree or a leaf value. If a path is used, then adding MBeanInfos has added them
     * as if no path were given (i.e. in it original place in the tree) but leaves out other information
     * not included by the path. This method then moves up the part pointed to by the path to the top of the
     * tree hierarchy. It also takes into account the maximum depth of the tree and truncates below
     *
     * @return either a Map for a subtree or the leaf value as an object
     */
    public Object truncate() {
        Object value = navigatePath();
        if (maxDepth == 0) {
            return value;
        }
        if (! (value instanceof Map)) {
            return value;
        } else {
            // Truncate all levels below
            return truncateMap((Map) value,maxDepth);
        }
    }

    // =====================================================================================================

    private void addFullMBeanInfo(Map pMBeanMap, MBeanInfo pMBeanInfo) {
        addDescription(pMBeanMap, pMBeanInfo, null);
        addAttributes(pMBeanMap, pMBeanInfo, null);
        addOperations(pMBeanMap, pMBeanInfo, null);
        addNotifications(pMBeanMap, pMBeanInfo, null);
    }

    private void addPartialMBeanInfo(Map pMBeanMap, MBeanInfo pMBeanInfo, Stack<String> pPathStack) {
        String what = pPathStack.empty() ? null : pPathStack.pop();
        if (KEY_DESCRIPTION.equals(what)) {
            addDescription(pMBeanMap, pMBeanInfo,popOrNull(pPathStack));
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

    private void addDescription(Map pMBeanMap, MBeanInfo pMBeanInfo, String pFilter) {
        pMBeanMap.put(KEY_DESCRIPTION, pMBeanInfo.getDescription());
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

    // Add a map, but also check when a path is given, and the map is empty, then throw an error
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

    // Trim down the stack by some value or return an empty stack
    private Stack<String> truncatePathStack(int pLevel) {
        if (pathStack.size() < pLevel) {
            return new Stack<String>();
        } else {
            // Trim of domain and MBean properties
            Stack<String> ret = (Stack<String>) pathStack.clone();
            for (int i = 0;i < pLevel;i++) {
                ret.pop();
            }
            return ret;
        }
    }

    private Object navigatePath() {
        int size = pathStack.size();
        while (size > 0) {
            Collection vals = infoMap.values();
            if (vals.size() == 0) {
                return infoMap;
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
            infoMap = (Map) value;
            --size;
        }
        return infoMap;
    }
}
