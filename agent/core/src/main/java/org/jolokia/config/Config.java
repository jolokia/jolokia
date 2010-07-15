package org.jolokia.config;

import java.io.*;

import javax.management.*;

import org.jolokia.history.HistoryKey;
import org.jolokia.history.HistoryStore;


/*
 * jmx4perl - WAR Agent for exporting JMX via JSON
 *
 * Copyright (C) 2009 Roland Huß, roland@cpan.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * A commercial license is available as well. Please contact roland@cpan.org for
 * further details.
 */

/**
 * @author roland
 * @since Jun 12, 2009
 */
public class Config implements ConfigMBean,MBeanRegistration {

    private HistoryStore historyStore;
    private DebugStore debugStore;

    // Optional domain to used fo registering this MBean
    private String qualifier;

    public Config(HistoryStore pHistoryStore, DebugStore pDebugStore, String pQualifier) {
        historyStore = pHistoryStore;
        debugStore = pDebugStore;
        qualifier = pQualifier;
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

    public int getHistorySize() throws IOException {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        ObjectOutputStream oOut = new ObjectOutputStream(bOut);
        oOut.writeObject(historyStore);
        return bOut.size();
    }

    public String getObjectName() {
        return OBJECT_NAME + (qualifier != null ? "," + qualifier : "");
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
