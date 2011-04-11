package org.jolokia.mbean;

import javax.management.*;

import org.jolokia.history.HistoryKey;
import org.jolokia.history.HistoryStore;
import org.jolokia.util.DebugStore;

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

    // Optional domain to used fo registering this MBean
    private String qualifier;

    // MBean Objectname under which this bean should be registered
    private String objectName;

    public Config(HistoryStore pHistoryStore, DebugStore pDebugStore, String pQualifier, String pObjectName) {
        historyStore = pHistoryStore;
        debugStore = pDebugStore;
        qualifier = pQualifier;
        objectName = pObjectName;
    }

    public void setHistoryEntriesForAttribute(String pMBean, String pAttribute, String pPath, String pTarget, int pMaxEntries)
            throws MalformedObjectNameException {
        HistoryKey key = new HistoryKey(pMBean,pAttribute,pPath,pTarget);
        historyStore.configure(key,pMaxEntries);
    }

    public void setHistoryEntriesForOperation(String pMBean, String pOperation, String pTarget, int pMaxEntries) throws MalformedObjectNameException {
        HistoryKey key = new HistoryKey(pMBean,pOperation,pTarget);
        historyStore.configure(key,pMaxEntries);
    }

    public void resetHistoryEntries() {
        historyStore.reset();
    }

    public String debugInfo() {
        return debugStore.debugInfo();
    }

    public void resetDebugInfo() {
        debugStore.resetDebugInfo();
    }

    public int getHistoryMaxEntries() {
        return historyStore.getGlobalMaxEntries();
    }

    public void setHistoryMaxEntries(int pLimit) {
        historyStore.setGlobalMaxEntries(pLimit);
    }

    public boolean isDebug() {
        return debugStore.isDebug();
    }

    public void setDebug(boolean pSwitch) {
        debugStore.setDebug(pSwitch);
    }

    public int getMaxDebugEntries() {
        return debugStore.getMaxDebugEntries();
    }

    public void setMaxDebugEntries(int pNumber) {
        debugStore.setMaxDebugEntries(pNumber);
    }

    public int getHistorySize() {
        return historyStore.getSize();
    }

    public String getObjectName() {
        return objectName + (qualifier != null ? "," + qualifier : "");
    }


    // ========================================================================

    // Provide our own name on registration
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws MalformedObjectNameException {
        return new ObjectName(getObjectName());
    }


    public void postRegister(Boolean registrationDone) {
    }

    public void preDeregister() {
    }

    public void postDeregister() {
    }
}
