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

import java.util.Stack;

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
     * @param pPathStack stack for further constraining the result
     */
    void update(JSONObject pJSONObject, MBeanInfo pMBeanInfo, Stack<String> pPathStack) {

        boolean isPathEmpty = pPathStack == null || pPathStack.empty();
        String filter = pPathStack != null && !pPathStack.empty() ? pPathStack.pop() : null;
        verifyThatPathIsEmpty(pPathStack);

        JSONObject attrMap = extractData(pMBeanInfo,filter);

        if (attrMap.size() > 0) {
            pJSONObject.put(getKey(), attrMap);
        } else if (!isPathEmpty) {
            throw new IllegalArgumentException("Path given but extracted value is empty");
        }
    }

    /**
     * Do the real work by extracting the data from the MBeanInfo. This method should be overridden,
     * in its default implementation it returns an empty map
     *
     * @param pMBeanInfo the info object to examine
     * @param pFilter any additional filter to apply
     * @return the extracted data as an JSON object
     */
    protected JSONObject extractData(MBeanInfo pMBeanInfo,String pFilter) {
        return new JSONObject();
    }

    // ======================================================================================

    /**
     * Check whether the given path is empty, if not, then throw an exception
     *
     * @param pPathStack path to check
     */
    protected void verifyThatPathIsEmpty(Stack<String> pPathStack) {
        if (pPathStack != null && pPathStack.size() > 0) {
            throw new IllegalArgumentException("Path contains extra elements not usable for a list request: " + pPathStack);
        }
    }
}
