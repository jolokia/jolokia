package org.jolokia.handler.list;

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

import org.json.simple.JSONObject;

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
public class MBeanInfoData {

    // max depth for map to return
    private int maxDepth;

    // stack for an inner path
    private Stack<String> pathStack;

    // Map holding information
    private JSONObject infoMap;

    // Initialise updaters
    private static final Map<String,DataUpdater> UPDATERS = new HashMap<String, DataUpdater>();

    // How to order keys in Object Names
    private boolean useCanonicalName;

    static {
        for (DataUpdater updater : new DataUpdater[] {
                new DescriptionDataUpdater(),
                new ClassNameDataUpdater(),
                new AttributeDataUpdater(),
                new OperationDataUpdater(),
                new NotificationDataUpdater()
        }) {
            UPDATERS.put(updater.getKey(),updater);
        }
    }

    /**
     * Constructor taking a max depth. The <em>max depth</em> specifies how deep the info tree should be build
     * up. The tree will be truncated if it gets larger than this value. A <em>path</em> (in form of a stack)
     * can be given, in which only a sub information is (sub-tree or leaf value) is stored
     *
     * @param pMaxDepth max depth
     * @param pPathStack the stack for restricting the information to add. The given stack will be cloned
     *                   and is left untouched.
     * @param pUseCanonicalName whether to use canonical name in listings
     */
    public MBeanInfoData(int pMaxDepth, Stack<String> pPathStack, boolean pUseCanonicalName) {
        maxDepth = pMaxDepth;
        useCanonicalName = pUseCanonicalName;
        pathStack = pPathStack != null ? (Stack<String>) pPathStack.clone() : new Stack<String>();
        infoMap = new JSONObject();
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
            JSONObject mBeansMap = getOrCreateJSONObject(infoMap, pName.getDomain());
            mBeansMap.put(getKeyPropertyString(pName),1);
            return true;
        }
        return false;
    }

    private String getKeyPropertyString(ObjectName pName) {
        return useCanonicalName ? pName.getCanonicalKeyPropertyListString() : pName.getKeyPropertyListString();
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

        JSONObject mBeansMap = getOrCreateJSONObject(infoMap, pName.getDomain());
        JSONObject mBeanMap = getOrCreateJSONObject(mBeansMap, getKeyPropertyString(pName));
        // Trim down stack to get rid of domain/property list
        Stack<String> stack = truncatePathStack(2);
        if (stack.empty()) {
            addFullMBeanInfo(mBeanMap, mBeanInfo);
        } else {
            addPartialMBeanInfo(mBeanMap, mBeanInfo,stack);
        }
        // Trim if required
        if (mBeanMap.size() == 0) {
            mBeansMap.remove(getKeyPropertyString(pName));
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
            addException(pName, pExp);
        } else {
            // Happens for a deeper request, i.e with a path pointing directly into an MBean,
            // Hence we throw immediately an error here since there will be only this exception
            // and no extra info
            throw new IOException("IOException for MBean " + pName + " (" + pExp.getMessage() + ")",pExp);
        }
    }

    /**
     * Add an exception which occurred during extraction of an {@link MBeanInfo} for
     * a certain {@link ObjectName} to this map.
     *
     * @param pName MBean name for which the error occurred
     * @param pExp exception occurred
     * @throws IllegalStateException if this method decides to rethrow the exception
     */
    public void handleException(ObjectName pName, IllegalStateException pExp) {
        // This happen happens for JBoss 7.1 in some cases.
        if (pathStack.size() == 0) {
            addException(pName, pExp);
        } else {
            throw new IllegalStateException("IllegalStateException for MBean " + pName + " (" + pExp.getMessage() + ")",pExp);
        }
    }

    /**
     * Add an exception which occurred during extraction of an {@link MBeanInfo} for
     * a certain {@link ObjectName} to this map.
     *
     * @param pName MBean name for which the error occurred
     * @param pExp exception occurred
     * @throws IllegalStateException if this method decides to rethrow the exception
     */
    public void handleException(ObjectName pName, InstanceNotFoundException pExp) throws InstanceNotFoundException {
        // This happen happens for JBoss 7.1 in some cases (i.e. ResourceAdapterModule)
        if (pathStack.size() == 0) {
           addException(pName, pExp);
        } else {
           throw new InstanceNotFoundException("InstanceNotFoundException for MBean " + pName + " (" + pExp.getMessage() + ")");
        }
    }
 
    // Add an exception to the info map
    private void addException(ObjectName pName, Exception pExp) {
        JSONObject mBeansMap = getOrCreateJSONObject(infoMap, pName.getDomain());
        JSONObject mBeanMap = getOrCreateJSONObject(mBeansMap, getKeyPropertyString(pName));
        mBeanMap.put(DataKeys.ERROR.getKey(), pExp.toString());
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
        if (! (value instanceof JSONObject)) {
            return value;
        } else {
            // Truncate all levels below
            return truncateJSONObject((JSONObject) value, maxDepth);
        }
    }

    // =====================================================================================================

    private void addFullMBeanInfo(JSONObject pMBeanMap, MBeanInfo pMBeanInfo) {
        for (DataUpdater updater : UPDATERS.values()) {
            updater.update(pMBeanMap,pMBeanInfo,null);
        }
    }

    private void addPartialMBeanInfo(JSONObject pMBeanMap, MBeanInfo pMBeanInfo, Stack<String> pPathStack) {
        String what = pPathStack.empty() ? null : pPathStack.pop();
        DataUpdater updater = UPDATERS.get(what);
        if (updater != null) {
            updater.update(pMBeanMap, pMBeanInfo, pPathStack);
        } else {
            throw new IllegalArgumentException("Illegal path element " + what);
        }
    }

    private JSONObject getOrCreateJSONObject(JSONObject pMap, String pKey) {
        JSONObject nMap = (JSONObject) pMap.get(pKey);
        if (nMap == null) {
            nMap = new JSONObject();
            pMap.put(pKey, nMap);
        }
        return nMap;
    }

    private Object truncateJSONObject(JSONObject pValue, int pMaxDepth) {
        if (pMaxDepth == 0) {
            return 1;
        }
        JSONObject ret = new JSONObject();
        Set<Map.Entry> entries = pValue.entrySet();
        for (Map.Entry entry : entries) {
            Object value = entry.getValue();
            Object key = entry.getKey();
            if (value instanceof JSONObject) {
                ret.put(key, truncateJSONObject((JSONObject) value, pMaxDepth - 1));
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
            // pathStack gets cloned here since the processing will eat it up
            Stack<String> ret = (Stack<String>) pathStack.clone();
            for (int i = 0;i < pLevel;i++) {
                ret.pop();
            }
            return ret;
        }
    }

    // Navigate to sub map or leaf value
    private Object navigatePath() {
        int size = pathStack.size();
        JSONObject innerMap = infoMap;

        while (size > 0) {
            Collection vals = innerMap.values();
            if (vals.size() == 0) {
                return innerMap;
            } else if (vals.size() != 1) {
                throw new IllegalStateException("Internal: More than one key found when extracting with path: " + vals);
            }
            Object value = vals.iterator().next();

            // End leaf, return it ....
            if (size == 1) {
                return value;
            }
            // Dive in deeper ...
            if (!(value instanceof JSONObject)) {
                throw new IllegalStateException("Internal: Value within path extraction must be a Map, not " + value.getClass());
            }
            innerMap = (JSONObject) value;
            --size;
        }
        return innerMap;
    }
}
