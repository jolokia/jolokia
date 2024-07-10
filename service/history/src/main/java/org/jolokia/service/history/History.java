package org.jolokia.service.history;

import javax.management.*;

import org.jolokia.server.core.request.JolokiaRequest;
import org.jolokia.json.JSONObject;

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
 * MBean for exporting various configuration tuning opportunities
 * to the outside world.
 *
 * @author roland
 * @since Jun 12, 2009
 */
public class History implements HistoryMBean {

    // Stores for various information
    private final HistoryStore store;

    /**
     * Constructor with the configurable objects as parameters.
     *
     * @param pStore history store where to hold historical values
     */
    public History(HistoryStore pStore) {
        store = pStore;
    }

    /** {@inheritDoc} */
    public void updateAndAdd(JolokiaRequest pJmxReq, JSONObject pJson) {
        store.updateAndAdd(pJmxReq,pJson);
    }

    /** {@inheritDoc} */
    public void setHistoryEntriesForAttribute(String pMBean, String pAttribute, String pPath, String pTarget, int pMaxEntries)
            throws MalformedObjectNameException {
        setHistoryLimitForAttribute(pMBean, pAttribute, pPath, pTarget, pMaxEntries, 0L);
    }

    /** {@inheritDoc} */
    public void setHistoryLimitForAttribute(String pMBean, String pAttribute, String pPath, String pTarget, int pMaxEntries, long pMaxDuration) throws MalformedObjectNameException {
        HistoryKey key = new HistoryKey(pMBean,pAttribute,pPath,pTarget);
        store.configure(key,limitOrNull(pMaxEntries, pMaxDuration));
    }

    /** {@inheritDoc} */
    public void setHistoryEntriesForOperation(String pMBean, String pOperation, String pTarget, int pMaxEntries) throws MalformedObjectNameException {
        setHistoryLimitForOperation(pMBean, pOperation, pTarget, pMaxEntries, 0L);
    }

    /** {@inheritDoc} */
    public void setHistoryLimitForOperation(String pMBean, String pOperation, String pTarget, int pMaxEntries, long pMaxDuration) throws MalformedObjectNameException {
        HistoryKey key = new HistoryKey(pMBean,pOperation,pTarget);
        store.configure(key, limitOrNull(pMaxEntries,pMaxDuration));
    }

    /** {@inheritDoc} */
    public void resetHistoryEntries() {
        store.reset();
    }

    /** {@inheritDoc} */
    public int getHistoryMaxEntries() {
        return store.getGlobalMaxEntries();
    }

    /** {@inheritDoc} */
    public void setHistoryMaxEntries(int pLimit) {
        store.setGlobalMaxEntries(pLimit);
    }

    /** {@inheritDoc} */
    public int getHistorySize() {
        return store.getSize();
    }

    // The limit or null if the entry should be disabled in the history store
    private HistoryLimit limitOrNull(int pMaxEntries, long pMaxDuration) {
        return pMaxEntries != 0 || pMaxDuration != 0 ? new HistoryLimit(pMaxEntries, pMaxDuration) : null;
    }
}
