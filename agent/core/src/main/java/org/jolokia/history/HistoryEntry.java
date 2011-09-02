package org.jolokia.history;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.LinkedList;
import java.io.Serializable;

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
 * Single entry in the call history of this agent. Used by {@link HistoryStore} for internally
 * remembering ancient values. Each HistoryEntry represent a list of values which in the stored
 * is keyed with the attribute/operation which was called. It has a maximum  number of values
 * which are stored and truncates the oldest one if more values are added.
 *
 * @author roland
 * @since Jun 12, 2009
 */
class HistoryEntry implements Serializable {

    private static final long serialVersionUID = 42L;


    @SuppressWarnings("PMD.LooseCoupling")
    private LinkedList<ValueEntry> values;
    private int maxEntries;

    /**
     * Constructor
     *
     * @param pMaxEntries how many values to keep
     */
    HistoryEntry(int pMaxEntries) {
        maxEntries = pMaxEntries;
        values = new LinkedList<ValueEntry>();
    }

    /**
     * Get an JSON array with values (along with their timestamps)
     *
     * @return array of values
     */
    public JSONArray jsonifyValues() {
        JSONArray jValues = new JSONArray();
        for (ValueEntry vEntry : values) {
            JSONObject o = new JSONObject();
            o.put("value",vEntry.getValue());
            o.put("timestamp",vEntry.getTimestamp());
            jValues.add(o);
        }
        return jValues;
    }


    /**
     * Set the maximum number of entries and truncate if necessary
     *
     * @param pMaxEntries new maxium entries
     */
    public void setMaxEntries(int pMaxEntries) {
        maxEntries = pMaxEntries;
        trim();
    }

    /**
     * Add a new value with the given times stamp to the values list
     *
     * @param pObject object to add
     * @param pTime timestamp
     */
    public void add(Object pObject, long pTime) {
        values.addFirst(new ValueEntry(pObject,pTime));
        trim();
    }

    // Truncate list so that no more than max entries are stored in the list
    private void trim() {
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
