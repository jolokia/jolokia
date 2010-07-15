package org.jolokia.history;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.LinkedList;
import java.io.Serializable;

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
class HistoryEntry implements Serializable {

    private static final long serialVersionUID = 42L;


    @SuppressWarnings("PMD.LooseCoupling")
    private LinkedList<ValueEntry> values;
    private int maxEntries;

    HistoryEntry(int pMaxEntries) {
        maxEntries = pMaxEntries;
        values = new LinkedList<ValueEntry>();
    }

    public Object jsonifyValues() {
        JSONArray jValues = new JSONArray();
        for (ValueEntry vEntry : values) {
            JSONObject o = new JSONObject();
            o.put("value",vEntry.getValue());
            o.put("timestamp",vEntry.getTimestamp());
            jValues.add(o);
        }
        return jValues;
    }


    public void setMaxEntries(int pMaxEntries) {
        maxEntries = pMaxEntries;
    }


    public void add(Object pObject, long pTime) {
        values.addFirst(new ValueEntry(pObject,pTime));
        trim();
    }

    public void trim() {
        // Trim
        while (values.size() > maxEntries) {
            values.removeLast();
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("HistoryEntry");
        sb.append("{values=").append(values);
        sb.append(", maxEntries=").append(maxEntries);
        sb.append('}');
        return sb.toString();
    }
}
