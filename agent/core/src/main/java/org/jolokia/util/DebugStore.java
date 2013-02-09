package org.jolokia.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;

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
 * Simple store for remembering debug info and returning it via a JMX operation
 * (exposed in ConfigMBean)
 *
 * @author roland
 * @since Jun 15, 2009
 */
public class DebugStore {

    @SuppressWarnings("PMD.LooseCoupling")
    private LinkedList<Entry> debugEntries = new LinkedList<Entry>();
    private int maxDebugEntries;
    private boolean isDebug;

    /**
     * Create the debug store for holding debug messages
     *
     * @param pMaxDebugEntries how many messages to keep
     * @param pDebug whether debug is switched on
     */
    public DebugStore(int pMaxDebugEntries, boolean pDebug) {
        maxDebugEntries = pMaxDebugEntries;
        isDebug = pDebug;
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
        if (!isDebug) {
            return "";
        }
        StringBuffer ret = new StringBuffer();
        for (int i = debugEntries.size() - 1;i >= 0;i--) {
            Entry entry = debugEntries.get(i);
            ret.append(entry.timestamp).append(": ").append(entry.message).append("\n");
            if (entry.throwable != null) {
                StringWriter writer = new StringWriter();
                entry.throwable.printStackTrace(new PrintWriter(writer));
                ret.append(writer.toString());
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
    private void add(long pTime,String message) {
        debugEntries.addFirst(new Entry(pTime,message));
        trim();
    }

    private void add(long pTimestamp, String pMessage, Throwable pThrowable) {
        debugEntries.addFirst(new Entry(pTimestamp,pMessage,pThrowable));
        trim();
    }

    // trim list of debug entries
    private void trim() {
        while (debugEntries.size() > maxDebugEntries) {
            debugEntries.removeLast();
        }
    }

    // ========================================================================

    // a singel entry in the debug store
    private static final class Entry {
        private long timestamp;
        private String message;
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
