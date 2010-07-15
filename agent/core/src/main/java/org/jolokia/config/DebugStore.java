package org.jolokia.config;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;

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
