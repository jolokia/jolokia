package org.jolokia.history;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.LinkedList;
import java.io.Serializable;

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
    private HistoryLimit limit;

    /**
     * Constructor
     *
     * @param pLimit how many values to keep and/or how long
     */
    HistoryEntry(HistoryLimit pLimit) {
        limit = pLimit;
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
     * Set the limit (maximum number, maximum duration) for entries and truncate if necessary
     *
     * @param pLimit new limit to apply
     */
    public void setLimit(HistoryLimit pLimit) {
        limit = pLimit;
        trim();
    }

    /**
     * Set the maximum entries for a history entry
     *
     * @param pMaxEntries maximum number of values to keep
     */
    public void setMaxEntries(int pMaxEntries) {
        setLimit(new HistoryLimit(pMaxEntries,limit.getMaxDuration()));
    }

    /**
     * Add a new value with the given times stamp to the values list
     *
     * @param pObject object to add
     * @param pTime timestamp in milliseconds
     */
    public void add(Object pObject, long pTime) {
        values.addFirst(new ValueEntry(pObject,pTime));
        trim();
    }

    // Truncate list so that no more than max entries are stored in the list
    private void trim() {

        // Trim
        while (values.size() > limit.getMaxEntries()) {
            values.removeLast();
        }

        // Trim according to duration
        if (limit.getMaxDuration() > 0 && !values.isEmpty()) {
            long duration = limit.getMaxDuration();
            long start = values.getFirst().getTimestamp();
            while (start - values.getLast().getTimestamp() > duration) {
                values.removeLast();
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("HistoryEntry");
        sb.append("{values=").append(values);
        sb.append(", limit=").append(limit);
        sb.append('}');
        return sb.toString();
    }

}
