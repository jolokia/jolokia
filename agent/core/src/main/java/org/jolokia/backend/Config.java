package org.jolokia.backend;

import javax.management.*;

import org.jolokia.history.*;
import org.jolokia.util.DebugStore;

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
public class Config implements ConfigMBean,MBeanRegistration {

    // Stores for various informations
    private HistoryStore historyStore;
    private DebugStore debugStore;

    // MBean Objectname under which this bean should be registered
    private String objectName;

    /**
     * Constructor with the configurable objects as parameters.
     *
     * @param pHistoryStore history store where to hold historical values
     * @param pDebugStore debug store for holding debug messages
     * @param pOName object name under which to register this MBean
     */
    public Config(HistoryStore pHistoryStore, DebugStore pDebugStore, String pOName) {
        historyStore = pHistoryStore;
        debugStore = pDebugStore;
        objectName = pOName;
    }

    /** {@inheritDoc} */
    public void setHistoryEntriesForAttribute(String pMBean, String pAttribute, String pPath, String pTarget, int pMaxEntries)
            throws MalformedObjectNameException {
        setHistoryLimitForAttribute(pMBean, pAttribute, pPath, pTarget, pMaxEntries, 0L);
    }

    /** {@inheritDoc} */
    public void setHistoryLimitForAttribute(String pMBean, String pAttribute, String pPath, String pTarget, int pMaxEntries, long pMaxDuration) throws MalformedObjectNameException {
        HistoryKey key = new HistoryKey(pMBean,pAttribute,pPath,pTarget);
        historyStore.configure(key,limitOrNull(pMaxEntries, pMaxDuration));
    }

    /** {@inheritDoc} */
    public void setHistoryEntriesForOperation(String pMBean, String pOperation, String pTarget, int pMaxEntries) throws MalformedObjectNameException {
        setHistoryLimitForOperation(pMBean, pOperation, pTarget, pMaxEntries, 0L);
    }

    /** {@inheritDoc} */
    public void setHistoryLimitForOperation(String pMBean, String pOperation, String pTarget, int pMaxEntries, long pMaxDuration) throws MalformedObjectNameException {
        HistoryKey key = new HistoryKey(pMBean,pOperation,pTarget);
        historyStore.configure(key, limitOrNull(pMaxEntries,pMaxDuration));
    }

    /** {@inheritDoc} */
    public void resetHistoryEntries() {
        historyStore.reset();
    }

    /** {@inheritDoc} */
    public String debugInfo() {
        return debugStore.debugInfo();
    }

    /** {@inheritDoc} */
    public void resetDebugInfo() {
        debugStore.resetDebugInfo();
    }

    /** {@inheritDoc} */
    public int getHistoryMaxEntries() {
        return historyStore.getGlobalMaxEntries();
    }

    /** {@inheritDoc} */
    public void setHistoryMaxEntries(int pLimit) {
        historyStore.setGlobalMaxEntries(pLimit);
    }

    /** {@inheritDoc} */
    public boolean isDebug() {
        return debugStore.isDebug();
    }

    /** {@inheritDoc} */
    public void setDebug(boolean pSwitch) {
        debugStore.setDebug(pSwitch);
    }

    /** {@inheritDoc} */
    public int getMaxDebugEntries() {
        return debugStore.getMaxDebugEntries();
    }

    /** {@inheritDoc} */
    public void setMaxDebugEntries(int pNumber) {
        debugStore.setMaxDebugEntries(pNumber);
    }

    /** {@inheritDoc} */
    public int getHistorySize() {
        return historyStore.getSize();
    }

    // The limit or null if the entry should be disabled in the history store
    private HistoryLimit limitOrNull(int pMaxEntries, long pMaxDuration) {
        return pMaxEntries != 0 || pMaxDuration != 0 ? new HistoryLimit(pMaxEntries, pMaxDuration) : null;
    }


    // ========================================================================

    // Provide our own name on registration
    /** {@inheritDoc} */
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws MalformedObjectNameException {
        return new ObjectName(objectName);
    }

    /** {@inheritDoc} */
    public void postRegister(Boolean registrationDone) {
    }

    /** {@inheritDoc} */
    public void preDeregister() {
    }

    /** {@inheritDoc} */
    public void postDeregister() {
    }
}
