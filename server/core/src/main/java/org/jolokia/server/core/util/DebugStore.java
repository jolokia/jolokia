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
package org.jolokia.server.core.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.jolokia.server.core.backend.Config;
import org.jolokia.server.core.backend.ConfigMBean;
import org.jolokia.server.core.service.api.AbstractJolokiaService;
import org.jolokia.server.core.service.api.AgentDetails;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.api.JolokiaService;

import static org.jolokia.server.core.config.ConfigKey.DEBUG;
import static org.jolokia.server.core.config.ConfigKey.DEBUG_MAX_ENTRIES;

/**
 * Simple store for remembering debug info and returning it via a JMX operation
 * (exposed in ConfigMBean)
 *
 * @author roland
 * @since Jun 15, 2009
 */
public class DebugStore extends AbstractJolokiaService<JolokiaService.Init> {

    @SuppressWarnings("PMD.LooseCoupling")
    private final LinkedList<Entry> debugEntries = new LinkedList<>();

    private ObjectName debugStoreObjectName;

    private int maxDebugEntries;
    private boolean isDebug;

    public DebugStore() {
        super(Init.class, 0 /* no order required */);
    }

    public DebugStore(int maxDebugEntries, boolean isDebug) {
        super(Init.class, 0 /* no order required */);
        this.maxDebugEntries = maxDebugEntries;
        this.isDebug = isDebug;
    }

    @Override
    public void init(JolokiaContext pJolokiaContext) {
        super.init(pJolokiaContext);

        AgentDetails details = pJolokiaContext.getAgentDetails();
        String agentId = details.getAgentId();
        String oName = ConfigMBean.OBJECT_NAME + ",agent=" + agentId;

        maxDebugEntries = Integer.parseInt(pJolokiaContext.getConfig(DEBUG_MAX_ENTRIES));
        isDebug = Boolean.parseBoolean(pJolokiaContext.getConfig(DEBUG));

        Config config = new Config(this, oName);
        try {
            debugStoreObjectName = new ObjectName(oName);
            pJolokiaContext.registerMBean(config, oName);
        } catch (MalformedObjectNameException | NotCompliantMBeanException | MBeanRegistrationException e) {
            pJolokiaContext.error("Problem registering " + oName + " MBean", e);
        } catch (InstanceAlreadyExistsException e) {
            pJolokiaContext.info(oName + " MBean is already registered");
        }
    }

    @Override
    public void destroy() throws Exception {
        if (debugStoreObjectName != null) {
            getJolokiaContext().unregisterMBean(debugStoreObjectName);
            debugStoreObjectName = null;
        }
    }

    /**
     * Store the given message in this store if debug is switched on
     *
     * @param pMessage message to store
     */
    public void log(String pMessage) {
        if (!isDebug) {
            return;
        }
        add(System.currentTimeMillis() / 1000,pMessage);
    }


    /**
     * Store the given message in this store if debug is switched on
     *
     * @param pMessage message to store
     * @param pThrowable exception to store
     */
    public void log(String pMessage, Throwable pThrowable) {
        add(System.currentTimeMillis() / 1000,pMessage,pThrowable);
    }

    /**
     * Get back all previously logged and stored debug messages
     *
     * @return debug string
     */
    public String debugInfo() {
        if (!isDebug || debugEntries.isEmpty()) {
            return "";
        }
        StringBuilder ret = new StringBuilder();
        for (int i = debugEntries.size() - 1;i >= 0;i--) {
            Entry entry = debugEntries.get(i);
            ret.append(entry.timestamp).append(": ").append(entry.message).append("\n");
            if (entry.throwable != null) {
                StringWriter writer = new StringWriter();
                entry.throwable.printStackTrace(new PrintWriter(writer));
                ret.append(writer);
            }
        }
        return ret.toString();
    }

    /**
     * Reset debug info
     */
    public void resetDebugInfo() {
        debugEntries.clear();
    }

    /**
     * Switch on/off debug
     *
     * @param pSwitch if <code>true</code> switch on debug
     */
    public void setDebug(boolean pSwitch) {
        if (!pSwitch) {
            resetDebugInfo();
        }
        isDebug = pSwitch;
    }

    /**
     * Return <code>true</code> when debugging is switched on
     *
     * @return true if debugging is switched on
     */
    public boolean isDebug() {
        return isDebug;
    }

    /**
     * Get the number of max debugging entries which can be stored
     * @return number of maximum debug entries
     */
    public int getMaxDebugEntries() {
        return maxDebugEntries;
    }

    /**
     * Set the number of maximum debuggin entries and trim the list of
     * debug entries
     *
     * @param pNumber the maximal number of debug entries
     */
    public void setMaxDebugEntries(int pNumber) {
        maxDebugEntries = pNumber;
        trim();
    }

    // add a message along with a time stamp
    private synchronized void add(long pTime,String message) {
        debugEntries.addFirst(new Entry(pTime,message));
        trim();
    }

    private synchronized void add(long pTimestamp, String pMessage, Throwable pThrowable) {
        debugEntries.addFirst(new Entry(pTimestamp,pMessage,pThrowable));
        trim();
    }

    // trim list of debug entries
    private synchronized void trim() {
        while (debugEntries.size() > maxDebugEntries) {
            debugEntries.removeLast();
        }
    }

    // ========================================================================

    // a singel entry in the debug store
    private static final class Entry {
        private final long timestamp;
        private final String message;
        private Throwable throwable;

        private Entry(long pTimestamp, String pMessage, Throwable pThrowable) {
            timestamp = pTimestamp;
            message = pMessage;
            throwable = pThrowable;
        }

        private Entry(long pTime, String pMessage) {
            timestamp = pTime;
            message = pMessage;
        }
    }
}
