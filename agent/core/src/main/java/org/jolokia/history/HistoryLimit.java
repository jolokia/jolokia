package org.jolokia.history;

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

import java.io.Serializable;

/**
 * Helper class used for specifying history entry limits
 *
 * @author roland
 * @since 05.03.12
 */
public class HistoryLimit implements Serializable {

    private static final long serialVersionUID = 42L;

    // maximum number of entries
    private int maxEntries;

    // maximum duration to keep
    private long maxDuration;

    /**
     * Create a limit with either or both maxEntries and maxDuration set
     *
     * @param pMaxEntries maximum number of entries to keep
     * @param pMaxDuration maximum duration for entries to keep (in seconds)
     */
    public HistoryLimit(int pMaxEntries, long pMaxDuration) {
        if (pMaxEntries == 0 && pMaxDuration == 0) {
            throw new IllegalArgumentException("Invalid limit, either maxEntries or maxDuration must be != 0");
        }
        if (pMaxEntries < 0) {
            throw new IllegalArgumentException("Invalid limit, maxEntries must be >= 0");
        }
        if (pMaxDuration < 0) {
            throw new IllegalArgumentException("Invalid limit, maxDuration must be >= 0");
        }
        maxEntries = pMaxEntries;
        maxDuration = pMaxDuration;
    }

    public int getMaxEntries() {
        return maxEntries;
    }

    public long getMaxDuration() {
        return maxDuration;
    }

    // Return a limit which has for sure as upper limit the given argument

    /**
     * Return a limit whose max entries are smaller or equals the given upper limit. For effieciency reasons, this object's
     * state might change with this method (i.e. the maxEntry number might be set or decreased)
     *
     * @param pGlobalMaxEntries upper limit
     * @return this if this limit already is below the upper limit or a new limit which lies in this limit
     */
    public HistoryLimit respectGlobalMaxEntries(int pGlobalMaxEntries) {
        if (maxEntries > pGlobalMaxEntries || maxEntries == 0) {
            maxEntries = pGlobalMaxEntries;
        }
        return this;
    }

    @Override
    public String toString() {
        return "HistoryLimit{" +
               "maxEntries=" + maxEntries +
               ", maxDuration=" + maxDuration +
               '}';
    }
}
