package org.jolokia.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;

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

    public DebugStore(int pMaxDebugEntries, boolean pDebug) {
        maxDebugEntries = pMaxDebugEntries;
        isDebug = pDebug;
    }

    public void log(String pMessage) {
        if (!isDebug) {
            return;
        }
        add(System.currentTimeMillis() / 1000,pMessage);
    }

    public void log(String pMessage, Throwable pThrowable) {
        add(System.currentTimeMillis() / 1000,pMessage,pThrowable);
    }

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

    public void resetDebugInfo() {
        debugEntries.clear();
    }

    public void setDebug(boolean pSwitch) {
        if (!pSwitch) {
            resetDebugInfo();
        }
        isDebug = pSwitch;
    }

    public boolean isDebug() {
        return isDebug;
    }

    public int getMaxDebugEntries() {
        return maxDebugEntries;
    }

    public void setMaxDebugEntries(int pNumber) {
        maxDebugEntries = pNumber;
        trim();
    }


    private void add(long pTime,String message) {
        debugEntries.addFirst(new Entry(pTime,message));
        trim();
    }

    private void add(long pTimestamp, String pMessage, Throwable pThrowable) {
        debugEntries.addFirst(new Entry(pTimestamp,pMessage,pThrowable));
        trim();
    }

    public void trim() {
        while (debugEntries.size() > maxDebugEntries) {
            debugEntries.removeLast();
        }
    }

    // ========================================================================

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
