package org.jolokia.handler.list;

/*
 * Copyright 2009-2011 Roland Huss
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

import javax.management.MBeanInfo;

import org.json.simple.JSONObject;

/**
 * Interface for updating a {@link MBeanInfoData} for a certain aspect of an {@link MBeanInfo}
 *
 * @author roland
 * @since 13.09.11
 */
abstract class DataUpdater {

    /**
     * Get the key under which the extracted data should be added.
     *
     * @return key
     */
    abstract String getKey();

    /**
     * Update the given JSON object with the data extracted from the given
     * MBeanInfo
     *
     * @param pJSONObject JSON object to update
     * @param pMBeanInfo info to extract from
     * @param pFilter additional filter which should be used for further restricting the update
     */
    abstract void update(JSONObject pJSONObject, MBeanInfo pMBeanInfo, String pFilter);

    // ======================================================================================

    /**
     * Add a map, but also check when a path is given, and the map is empty, then throw an error
     *
     * @param pJSONObject object to update
     * @param pToAdd the object to add
     * @param pPathPart additional path part
     */
    protected void updateMapConsideringPathError(JSONObject pJSONObject, JSONObject pToAdd, String pPathPart) {
        if (pToAdd.size() > 0) {
            pJSONObject.put(getKey(), pToAdd);
        } else if (pPathPart != null) {
            throw new IllegalArgumentException("Invalid attribute path provided (element '" + pPathPart + "' not found)");
        }
    }

}
