package org.jolokia.history;

import java.io.*;
import java.util.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.request.*;
import org.jolokia.util.RequestType;
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
 * Store for remembering values which has been fetched through a previous
 * request.
 *
 * @author roland
 * @since Jun 12, 2009
 */
@SuppressWarnings("IS2_INCONSISTENT_SYNC") // FindBugs gets confused with inner classes accessing objects in the parent
public class HistoryStore {

    // Hard limit for number of entries for a single history track
    private int globalMaxEntries;

    private Map<HistoryKey, HistoryEntry> historyStore;
    private Map<HistoryKey, HistoryLimit> patterns;

    // Keys used in JSON representation
    private static final String KEY_HISTORY = "history";
    private static final String KEY_VALUE = "value";
    private static final String KEY_TIMESTAMP = "timestamp";

    private Map<RequestType,HistoryUpdater> historyUpdaters = new HashMap<RequestType, HistoryUpdater>();

    /**
     * Constructor for a history store
     *
     * @param pTotalMaxEntries number of entries to hold at max. Even when configured, this maximum can not
     *        be overwritten. This is a hard limit.
     */
    public HistoryStore(int pTotalMaxEntries) {
        globalMaxEntries = pTotalMaxEntries;
        historyStore = new HashMap<HistoryKey, HistoryEntry>();
        patterns = new HashMap<HistoryKey, HistoryLimit>();
        initHistoryUpdaters();
    }

    /**
     * Get the maximum number of entries stored.
     *
     * @return the maximum number of entries
     */
    public synchronized int getGlobalMaxEntries() {
        return globalMaxEntries;
    }

    /**
     * Set the global maximum limit for history entries.
     *
     * @param pGlobalMaxEntries limit
     */
    public synchronized void setGlobalMaxEntries(int pGlobalMaxEntries) {
        globalMaxEntries = pGlobalMaxEntries;
        // Refresh all entries
        for (HistoryEntry entry : historyStore.values()) {
            entry.setMaxEntries(globalMaxEntries);
        }
    }

    /**
     * Configure the history length for a specific entry. If the length
     * is 0 disable history for this key. Please note, that this method might change the limit
     * object so the ownership of this object goes over to the callee.
     *
     * @param pKey history key
     * @param pHistoryLimit limit to apply or <code>null</code> if no history should be recored for this entry
     */
    public synchronized void configure(HistoryKey pKey, HistoryLimit pHistoryLimit) {
        // Remove entries if set to null
        if (pHistoryLimit == null) {
            removeEntries(pKey);
            return;
        }
        HistoryLimit limit = pHistoryLimit.respectGlobalMaxEntries(globalMaxEntries);

        if (pKey.isMBeanPattern()) {
            patterns.put(pKey,limit);
            // Trim all already stored keys
            for (HistoryKey key : historyStore.keySet()) {
                if (pKey.matches(key)) {
                    HistoryEntry entry = historyStore.get(key);
                    entry.setLimit(limit);
                }
            }
        } else {
            HistoryEntry entry = historyStore.get(pKey);
            if (entry != null) {
                entry.setLimit(limit);
            } else {
                entry = new HistoryEntry(limit);
                historyStore.put(pKey,entry);
            }
        }
    }

    /**
     * Reset the complete store.
     */
    public synchronized void reset() {
        historyStore = new HashMap<HistoryKey, HistoryEntry>();
        patterns = new HashMap<HistoryKey, HistoryLimit>();
    }

    /**
     * Update the history store with the value of an an read, write or execute operation. Also, the timestamp
     * of the insertion is recorded. Also, the recorded history values are added to the given json value.
     *
     * @param pJmxReq request for which an entry should be added in this history store
     * @param pJson the JSONObject to which to add the history.
     */
    public synchronized void updateAndAdd(JmxRequest pJmxReq, JSONObject pJson) {
        long timestamp = System.currentTimeMillis() / 1000;
        pJson.put(KEY_TIMESTAMP,timestamp);

        RequestType type  = pJmxReq.getType();
        HistoryUpdater updater = historyUpdaters.get(type);
        if (updater != null) {
            updater.updateHistory(pJson,pJmxReq,timestamp);
        }
    }

    /**
     * Get the size of this history store in bytes
     *
     * @return size in bytes
     */
    public synchronized int getSize() {
        try {
            ByteArrayOutputStream bOut = new ByteArrayOutputStream();
            ObjectOutputStream oOut = new ObjectOutputStream(bOut);
            oOut.writeObject(historyStore);
            bOut.close();
            return bOut.size();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot serialize internal store: " + e,e);
        }
    }

    // =======================================================================================================

    // Interface for updating a history entry for a certain type

    /**
     * Internal interface used for updating this store
     *
     * @param <R> request type
     */
    interface HistoryUpdater<R extends JmxRequest> {
        /**
         * Update history
         *
         * @param pJson the result of the request
         * @param request request leading to the result
         * @param pTimestamp timestamp when the request was executed
         */
        void updateHistory(JSONObject pJson,R request,long pTimestamp);
    }

    // A set of updaters which are dispatched to for certain request types
    private void initHistoryUpdaters() {
        historyUpdaters.put(RequestType.EXEC,
                            new HistoryUpdater<JmxExecRequest>() {
                                /** {@inheritDoc} */
                                public void updateHistory(JSONObject pJson,JmxExecRequest request, long pTimestamp) {
                                    HistoryEntry entry = historyStore.get(new HistoryKey(request));
                                    if (entry != null) {
                                        synchronized(entry) {
                                            pJson.put(KEY_HISTORY,entry.jsonifyValues());
                                            entry.add(pJson.get(KEY_VALUE),pTimestamp);
                                        }
                                    }
                                }
                            });
        historyUpdaters.put(RequestType.WRITE,
                            new HistoryUpdater<JmxWriteRequest>() {
                                /** {@inheritDoc} */
                                public void updateHistory(JSONObject pJson,JmxWriteRequest request, long pTimestamp) {
                                    HistoryEntry entry = historyStore.get(new HistoryKey(request));
                                    if (entry != null) {
                                        synchronized(entry) {
                                            pJson.put(KEY_HISTORY,entry.jsonifyValues());
                                            entry.add(request.getValue(),pTimestamp);
                                        }
                                    }
                                }
                            });
        historyUpdaters.put(RequestType.READ,
                            new HistoryUpdater<JmxReadRequest>() {
                                /** {@inheritDoc} */
                                public void updateHistory(JSONObject pJson,JmxReadRequest request, long pTimestamp) {
                                    updateReadHistory(request,pJson,pTimestamp);
                                }
                            });

    }


    // Remove entries
    private void removeEntries(HistoryKey pKey) {
        if (pKey.isMBeanPattern()) {
            patterns.remove(pKey);
            List<HistoryKey> toRemove = new ArrayList<HistoryKey>();
            for (HistoryKey key : historyStore.keySet()) {
                if (pKey.matches(key)) {
                    toRemove.add(key);
                }
            }
            // Avoid concurrent modification exceptions
            for (HistoryKey key : toRemove) {
                historyStore.remove(key);
            }
        } else {
            HistoryEntry entry = historyStore.get(pKey);
            if (entry != null) {
                historyStore.remove(pKey);
            }
        }
    }

    // Update potentially multiple history entries for a READ request which could
    // return multiple values with a single request
    private void updateReadHistory(JmxReadRequest pJmxReq, JSONObject pJson, long pTimestamp)  {
        ObjectName name = pJmxReq.getObjectName();
        if (name.isPattern()) {
            // We have a pattern and hence a value structure
            // of bean -> attribute_key -> attribute_value
            Map<String,Object> values = (Map<String, Object>) pJson.get(KEY_VALUE);
            // Can be null if used with path and no single match occurred
            if (values != null) {
                JSONObject history = updateHistoryForPatternRead(pJmxReq, pTimestamp, values);
                if (history.size() > 0) {
                    pJson.put(KEY_HISTORY,history);
                }
            }
        } else if (pJmxReq.isMultiAttributeMode() || !pJmxReq.hasAttribute()) {
            // Multiple attributes, but a single bean.
            // Value has the following structure:
            // attribute_key -> attribute_value
            JSONObject history = addMultipleAttributeValues(
                    pJmxReq,
                    ((Map<String, Object>) pJson.get(KEY_VALUE)),
                    pJmxReq.getObjectNameAsString(),
                    pTimestamp);
            if (history.size() > 0) {
                pJson.put(KEY_HISTORY,history);
            }
        } else {
            // Single attribute, single bean. Value is the attribute_value
            // itself.
            addAttributeFromSingleValue(pJson,
                                        new HistoryKey(pJmxReq), KEY_HISTORY,
                                        pJson.get(KEY_VALUE),
                                        pTimestamp);
        }
    }

    private JSONObject updateHistoryForPatternRead(JmxReadRequest pJmxReq, long pTimestamp, Map<String, Object> pValues) {
        JSONObject history = new JSONObject();
        List<String> pathParts = pJmxReq.getPathParts();
        if (pathParts != null && pathParts.size() == 1) {
            return updateHistoryForPatternReadWithMBeanAsPath(pJmxReq, pTimestamp, pValues);
        }
        for (Map.Entry<String,Object> beanEntry : pValues.entrySet()) {
            JSONObject beanHistory = null;
            String beanName = beanEntry.getKey();
            Object value = beanEntry.getValue();
            if (pathParts != null && pathParts.size() == 2) {
                beanHistory = addPathFilteredAttributeValue(pJmxReq, pTimestamp, beanName, value);
            }
            if (value instanceof Map) {
                beanHistory =
                        addMultipleAttributeValues(
                                pJmxReq,
                                ((Map<String, Object>) beanEntry.getValue()),
                                beanName,
                                pTimestamp);
            }
            if (beanHistory != null && beanHistory.size() > 0) {
                history.put(beanName, beanHistory);
            }
        }
        return history;
    }

    private JSONObject addPathFilteredAttributeValue(JmxReadRequest pJmxReq, long pTimestamp, String pBeanName, Object pValue) {
        // value
        String attribute = pJmxReq.getPathParts().get(1);
        HistoryKey key = createHistoryKey(pJmxReq, pBeanName,attribute,pJmxReq.getPath());
        return addAttributeFromSingleValue(key,attribute,pValue,pTimestamp);
    }

    private JSONObject updateHistoryForPatternReadWithMBeanAsPath(JmxReadRequest pJmxReq, long pTimestamp, Map<String, Object> pValues) {
        // It the content of the MBean itself. MBean name is the first the single path part
        String beanName = pJmxReq.getPathParts().get(0);
        JSONObject ret = new JSONObject();
        JSONObject beanHistory = addMultipleAttributeValues(
                pJmxReq,
                pValues,
                beanName,
                pTimestamp);
        if (beanHistory.size() > 0) {
            ret.put(beanName, beanHistory);
        }
        return ret;
    }

    private JSONObject addMultipleAttributeValues(JmxRequest pJmxReq, Map<String, Object> pAttributesMap,
                                                  String pBeanName, long pTimestamp) {
        JSONObject ret = new JSONObject();
        for (Map.Entry<String,Object> attrEntry : pAttributesMap.entrySet()) {
            String attrName = attrEntry.getKey();
            Object value = attrEntry.getValue();
            String path = pJmxReq.getPath();
            HistoryKey key = createHistoryKey(pJmxReq, pBeanName, attrName, path);
            addAttributeFromSingleValue(ret,
                                        key,
                                        attrName,
                                        value,
                                        pTimestamp);
        }
        return ret;
    }

    private HistoryKey createHistoryKey(JmxRequest pJmxReq, String pBeanName, String pAttrName, String pPath) {
        HistoryKey key;
        try {
            String target = pJmxReq.getTargetConfig() != null ? pJmxReq.getTargetConfig().getUrl() : null;
            key = new HistoryKey(pBeanName, pAttrName, pPath,target);
        } catch (MalformedObjectNameException e) {
            // Shouldn't occur since we get the MBeanName from a JMX operation's result. However,
            // we will rethrow it just in case
            throw new IllegalArgumentException("Can not parse MBean name " + pBeanName,e);
        }
        return key;
    }

    // Return a fresh map
    private JSONObject addAttributeFromSingleValue(HistoryKey pKey, String pAttrName, Object pValue, long pTimestamp) {
        HistoryEntry entry = getEntry(pKey,pValue,pTimestamp);
        return entry != null ?
                addToHistoryEntryAndGetCurrentHistory(new JSONObject(), entry, pAttrName, pValue, pTimestamp) :
                null;
    }

    // Use an existing map
    private void addAttributeFromSingleValue(JSONObject pHistMap, HistoryKey pKey, String pAttrName,
                                             Object pValue, long pTimestamp) {
        HistoryEntry entry = getEntry(pKey, pValue, pTimestamp);
        if (entry != null) {
            addToHistoryEntryAndGetCurrentHistory(pHistMap, entry, pAttrName, pValue, pTimestamp);
        }
    }

    private JSONObject addToHistoryEntryAndGetCurrentHistory(JSONObject pHistMap, HistoryEntry pEntry, String pAttrName,
                                                             Object pValue, long pTimestamp) {
        synchronized (pEntry) {
                pHistMap.put(pAttrName, pEntry.jsonifyValues());
                pEntry.add(pValue, pTimestamp);
        }
        return pHistMap;
    }

    private synchronized HistoryEntry getEntry(HistoryKey pKey,Object pValue,long pTimestamp) {
        HistoryEntry entry = historyStore.get(pKey);
        if (entry != null) {
            return entry;
        }
        // Now try all known patterns and add lazily the key
        for (HistoryKey key : patterns.keySet()) {
            if (key.matches(pKey)) {
                entry = new HistoryEntry(patterns.get(key));
                entry.add(pValue,pTimestamp);
                historyStore.put(pKey,entry);
                return entry;
            }
        }
        return null;
    }

}
