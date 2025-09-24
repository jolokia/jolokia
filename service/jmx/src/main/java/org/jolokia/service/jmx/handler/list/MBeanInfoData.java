package org.jolokia.service.jmx.handler.list;

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
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.jolokia.json.JSONObject;
import org.jolokia.service.jmx.api.CacheKeyProvider;

/**
 * Tree of MBean metadata. This map is a container for one or more {@link MBeanInfo} metadata which can be obtained
 * via a <code>list</code> request. The full structure in its JSON representation looks like below. The amount
 * of data included can be fine-tuned in two ways:
 * <ul>
 *     <li>With a <code>maxDepth</code> parameter given at construction time, the size of the map can be restricted
 *     (from top down)</li>
 *     <li>A given path selects only partial information from the tree</li>
 * </ul>
 * Both limiting factors are taken care of when adding the information so that this map doesn't get unnecessarily
 * too large.
 *
 * <pre>{@code
 * {
 *   <domain> : {
 *     <prop list> : {
 *       "attr" : {
 *         <attr name> : {
 *           "type" : <attribute type>,
 *           "desc" : <textual description of attribute>,
 *           "rw"   : true/false
 *         },
 *         ...
 *       },
 *       "op" : {
 *         <operation name> : {
 *           "args" : [
 *             {
 *               "type" : <argument type>,
 *               "name" : <argument name>,
 *               "desc" : <textual description of argument>
 *             },
 *             ...
 *           ],
 *           "ret"  : <return type>,
 *           "desc" : <textual description of operation>
 *         },
 *         ...
 *       },
 *       "notif" : {
 *         <notification type> : {
 *           "name" : <name>,
 *           "desc" : <desc>,
 *           "types" : [ <type1>, <type2>, ... ]
 *         },
 *         ...
 *       }
 *     },
 *     ...
 *   },
 *   ...
 * }
 * }</pre>
 *
 * With {@link org.jolokia.server.core.config.ConfigKey#LIST_CACHE} optimization, top level fields of "list"
 * response should always be {@code cache} and {@code domains}. and some JSON representation of {@link MBeanInfo}
 * may be a String key into the "cache".
 *
 * @author roland
 * @since 13.09.11
 */
public class MBeanInfoData {

    // max depth for map to return
    private final int maxDepth;

    /**
     * Stack of path elements for Jolokia list operation. Two first elements are for {@link ObjectName#getDomain()}
     * and {@link ObjectName#getCanonicalKeyPropertyListString()}, 3rd element is to select single updater to use
     * like {@code class} or {@code attr}.
     */
    private final Deque<String> pathStack;

    /**
     * ObjectName or pattern recreated from the passed pathStack. Used to filter response elements. {@code null}
     * means <em>all match</em>.
     */
    private final ObjectName pathObjectName;

    /**
     * 3rd path segment can be used to select single {@link DataUpdater}. No more path segments are supported.
     */
    private final String selectedUpdater;

    /**
     * If a path consists of non-wildcard segments, we actually want to retrieve nested tree from the list
     * response. Otherwise we treat the path as a filter, not as a pointer to nested structure.
     */
    private int retrieveAtDepth = 0;

    // Map holding information. Without narrowing the list (using maxDepth), this should be:
    // domain -> mbean (by key property listing) -> JSON representation of mbeanInfo.
    // For optimized list() variant, the map is a bit more complex and the above mapping is under "domains" key,
    // while "cache" key contains full, JSON representation of some MBeanInfos from the MBeans under "domains"
    private final Map<String, Object> infoMap = new JSONObject();

    // static updaters for basic mapping of javax.management.MBeanInfo
    private static final Map<String, DataUpdater> UPDATERS = new HashMap<>();
    private static final DataUpdater LIST_KEYS_UPDATER = new ListKeysDataUpdater();

    // How to order keys in Object Names
    private final boolean useCanonicalName;

    // whether to add a map of keys from Object name to MBeanInfo data of the MBean
    private final boolean listKeys;

    // whether to use optimized list() response (with cache/domain)
    private boolean listCache;

    static {
        for (DataUpdater updater : new DataUpdater[] {
                new DescriptionDataUpdater(),
                new ClassNameDataUpdater(),
                new AttributeDataUpdater(),
                new OperationDataUpdater(),
                new NotificationDataUpdater(),
        }) {
            UPDATERS.put(updater.getKey(),updater);
        }
    }

    // Provider to prepend (if not null). Only org.jolokia.service.jsr160.Jsr160RequestHandler declares "proxy"
    // (literally) provider.
    private final String pProvider;

    /**
     * Constructor taking a max depth. The <em>max depth</em> specifies how deep the info tree should be build
     * up. The tree will be truncated if it gets larger than this value. A <em>path</em> (in form of a stack)
     * can be given, in which only a sub information (subtree or leaf value) is returned
     *
     * @param pMaxDepth         max depth
     * @param pPathStack        the stack for restricting the information to add. The given stack will be cloned
     *                          and is left untouched.
     * @param pUseCanonicalName whether to use canonical name in listings
     * @param pListKeys
     * @param pListCache
     */
    public MBeanInfoData(int pMaxDepth, Deque<String> pPathStack, boolean pUseCanonicalName, boolean pListKeys, boolean pListCache, String pProvider) {
        maxDepth = pMaxDepth;
        useCanonicalName = pUseCanonicalName;
        listKeys = pListKeys;
        listCache = pListCache;
        pathStack = pPathStack != null ? new LinkedList<>(pPathStack) : new LinkedList<>();
        this.pProvider = pProvider;

        // In list operation, path may be in the form of:
        //  - 1 element: domain-or-pattern
        //  - 2 elements: domain-or-pattern/keys-or-pattern
        try {
            if (pathStack.isEmpty()) {
                pathObjectName = null;
            } else {
                String domain = pathStack.pop();
                if (domain == null) {
                    domain = "*";
                }
                domain = removeProviderIfNeeded(domain);
                String name = "*";
                if (!pathStack.isEmpty()) {
                    name = pathStack.pop();
                    if (name == null) {
                        name = "*";
                    }
                }
                pathObjectName = "*".equals(domain) && "*".equals(name) ? null : new ObjectName(domain + ":" + name);
                if (pathObjectName != null) {
                    if (!pathObjectName.isDomainPattern()) {
                        retrieveAtDepth++;
                        if (!pathObjectName.isPropertyListPattern() && !pathObjectName.isPropertyValuePattern()) {
                            retrieveAtDepth++;
                        }
                    }
                }
            }
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        // if the pathObjectName is not a pattern, we can "navigate the path" and return subset of the tree.
        // in other case the returned tree will be filtered by the pattern and will already contain subsets of the
        // objects.
        // also without a pattern there's no point in caching
        if (pathObjectName != null && !pathObjectName.isPattern()) {
            listCache = false;
        }

        if (pathStack.isEmpty()) {
            selectedUpdater = null;
        } else {
            selectedUpdater = pathStack.pop();
            if (pathObjectName != null && !pathObjectName.isPattern()) {
                retrieveAtDepth++;
            }
        }

        if (!pathStack.isEmpty()) {
            throw new IllegalArgumentException("List operation supports only 3 path segments for MBean domain," +
                "canonical list of ObjectName properties and updater to use. Remaining path: " + String.join(", ", pathStack));
        }
    }

    /**
     * <p>The first two levels of this map (tree) consist of the MBean's domain name and name properties, which are
     * independent of an MBean's metadata. If the max depth given at construction time is less or equals than 2,
     * then a caller does not need to query the MBeanServer for MBeanInfo metadata, because it's not expected.</p>
     *
     * <p>This method checks this condition and returns true if this is the case. As side effect it will update this
     * map with the name part extracted from the given object name</p>
     *
     * @param pName the {@link ObjectName} used for the first two levels
     * @return true if the object name has been added and {@link MBeanServerConnection#getMBeanInfo} is not needed
     */
    public boolean handleFirstOrSecondLevel(ObjectName pName) {
        if (maxDepth > 2) {
            // full or partial serialization of MBeanInfo
            return false;
        }
        if (maxDepth == 1) {
            // Only add domain names with a dummy value if max depth is restricted to 1.
            // Only if the MBean matches ObjectName pattern created from 2 first segments of the path
            if (pathObjectName == null || pathObjectName.apply(pName)) {
                infoMap.put(addProviderIfNeeded(pName.getDomain()), 1);
            }
            return true;
        } else if (maxDepth == 2) {
            // Add domain an object name into the map, final value is a dummy value. Only if
            // Only if the MBean matches ObjectName pattern created from 2 first segments of the path
            if (pathObjectName == null || pathObjectName.apply(pName)) {
                Map<String, Object> domain = getOrCreateJSONObject(infoMap, addProviderIfNeeded(pName.getDomain()));
                domain.put(getKeyPropertyString(pName), 1);
            }
            return true;
        }
        return false;
    }

    /**
     * Turn {@link ObjectName} into a String depending on {@link org.jolokia.server.core.config.ConfigKey#CANONICAL_NAMING}
     * property setting.
     * @param pName
     * @return
     */
    private String getKeyPropertyString(ObjectName pName) {
        return useCanonicalName ? pName.getCanonicalKeyPropertyListString() : pName.getKeyPropertyListString();
    }

    /**
     * Add information about an MBean as obtained from an {@link MBeanInfo} descriptor. The information added
     * can be restricted by a given path (which has already been prepared as a stack). Also, a max depth as given in the
     * constructor restricts the size of the map from the top.
     *
     * @param pConn             {@link MBeanServerConnection} to get MBeanInfo from (or from cache if possible)
     * @param pInstance         the object instance of the MBean
     * @param customUpdaters    additional set of discovered updaters to enhance the constructed MBeanInfo (JSON data)
     * @param cacheKeyProviders set of services that help to construct the cache of MBeanInfo
     */
    public void addMBeanInfo(MBeanServerConnection pConn, ObjectInstance pInstance, Set<DataUpdater> customUpdaters,
                             Set<CacheKeyProvider> cacheKeyProviders)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException, IOException {

        if (pathObjectName != null && !pathObjectName.apply(pInstance.getObjectName())) {
            // filtered MBean
            return;
        }

        ObjectName objectName = pInstance.getObjectName();
        MBeanInfo mBeanInfo = pConn.getMBeanInfo(objectName);
        String domainName = addProviderIfNeeded(objectName.getDomain());
        String mbeanKeyListing = getKeyPropertyString(objectName);

        Map<String, Object> cache;
        Map<String, Object> domains;
        Map<String, Object> domain;
        Map<String, Object> mbean = null;
        if (listCache) {
            domains = getOrCreateJSONObject(infoMap, "domains");
            domain = getOrCreateJSONObject(domains, domainName);
        } else {
            domain = getOrCreateJSONObject(infoMap, domainName);
            mbean = getOrCreateJSONObject(domain, mbeanKeyListing);
        }

        if (!listCache) {
            // normal JSON representation of MBeanInfo without a cache
            addFullMBeanInfo(mbean, objectName, mBeanInfo, objectName, customUpdaters);
        } else {
            // cached MBeanInfo
            String key = null;
            for (CacheKeyProvider provider : cacheKeyProviders) {
                key = provider.determineKey(pInstance);
                if (key != null) {
                    break;
                }
            }
            cache = getOrCreateJSONObject(infoMap, "cache");
            if (key != null) {
                // an MBean may share its JSON representation of MBeanInfo with other MBeans
                // object name points to a key
                domain.put(mbeanKeyListing, key);
                // while key points to shared JSON representation of MBeanInfo
                mbean = getOrCreateJSONObject(cache, key);
                if (mbean.isEmpty()) {
                    addFullMBeanInfo(mbean, objectName, mBeanInfo, objectName, customUpdaters);
                }
            } else {
                // back to normal behavior
                mbean = getOrCreateJSONObject(domain, mbeanKeyListing);
                addFullMBeanInfo(mbean, objectName, mBeanInfo, objectName, customUpdaters);
            }
        }

        // Trim if required
        if (mbean != null && mbean.isEmpty()) {
            domain.remove(mbeanKeyListing);
            if (domain.isEmpty()) {
                infoMap.remove(domainName);
            }
        }
    }

    private String addProviderIfNeeded(String pDomain) {
        return pProvider != null ? pProvider + "@" + pDomain : pDomain;
    }

    private String removeProviderIfNeeded(String pDomain) {
        return pProvider != null && pDomain.startsWith(pProvider)
            ? pDomain.substring(pProvider.length() + 1) : pDomain;
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
        if (retrieveAtDepth == 0) {
            addException(pName, pExp);
        } else {
            // Happens for a deeper request, i.e. with a path pointing directly into an MBean,
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
        if (retrieveAtDepth == 0) {
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
        if (retrieveAtDepth == 0) {
           addException(pName, pExp);
        } else {
           throw new InstanceNotFoundException("InstanceNotFoundException for MBean " + pName + " (" + pExp.getMessage() + ")");
        }
    }

    // Add an exception to the info map
    private void addException(ObjectName pName, Exception pExp) {
        Map<String, Object> domain = getOrCreateJSONObject(infoMap, addProviderIfNeeded(pName.getDomain()));
        Map<String, Object> mbean = getOrCreateJSONObject(domain, getKeyPropertyString(pName));
        mbean.put(DataKeys.ERROR.getKey(), pExp.toString());
    }

    /**
     * Extract either a subtree or a leaf value. If a path is used, then adding MBeanInfos has added them
     * as if no path were given (i.e. in it original place in the tree) but leaves out other information
     * not included by the path. This method then moves up the part pointed to by the path to the top of the
     * tree hierarchy. It also takes into account the maximum depth of the tree and truncates below
     *
     * @return either a Map for a subtree or the leaf value as an object
     */
    public Object applyPath() {
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

    /**
     * Populates JSON MBean information based on {@link MBeanInfo} using all available {@link DataUpdater updaters}.
     *
     * @param pObjectName
     * @param pMBeanMap
     * @param pMBeanInfo
     * @param pName
     * @param customUpdaters
     */
    private void addFullMBeanInfo(Map<String, Object> pMBeanMap, ObjectName pObjectName, MBeanInfo pMBeanInfo, ObjectName pName, Set<DataUpdater> customUpdaters) {
        boolean updaterFound = false;
        for (DataUpdater updater : UPDATERS.values()) {
            if (selectedUpdater == null || updater.getKey().equals(selectedUpdater)) {
                updater.update(pMBeanMap, pObjectName, pMBeanInfo, null);
                updaterFound = true;
            }
        }
        if (listKeys && (selectedUpdater == null || LIST_KEYS_UPDATER.getKey().equals(selectedUpdater))) {
            LIST_KEYS_UPDATER.update(pMBeanMap, pObjectName, pMBeanInfo, null);
            updaterFound = true;
        }
        for (DataUpdater customUpdater : customUpdaters) {
            if (selectedUpdater == null || customUpdater.getKey().equals(selectedUpdater)) {
                customUpdater.update(pMBeanMap, pObjectName, pMBeanInfo, null);
                updaterFound = true;
            }
        }
        if (!updaterFound) {
            throw new IllegalArgumentException("Illegal path element for updater selection: " + selectedUpdater);
        }
    }

    /**
     * Ensure that {@code pMap} contains a nested map under {@code pKey} key and returns such nested
     * {@link JSONObject}.
     * @param pMap
     * @param pKey
     * @return
     */
    private Map<String, Object> getOrCreateJSONObject(Map<String, Object> pMap, String pKey) {
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
        Set<Map.Entry<String, Object>> entries = pValue.entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof JSONObject) {
                ret.put(key, truncateJSONObject((JSONObject) value, pMaxDepth - 1));
            } else {
                ret.put(key,value);
            }
        }
        return ret;
    }

    // Navigate to sub map or leaf value
    private Object navigatePath() {
        int size = retrieveAtDepth;
        Map<String, Object> innerMap = infoMap;

        if (!listCache) {
            // no optimization, standard handling of maxDepth and path
            while (size > 0) {
                Collection<Object> vals = innerMap.values();
                if (vals.isEmpty()) {
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
        }

        return innerMap;
    }
}
