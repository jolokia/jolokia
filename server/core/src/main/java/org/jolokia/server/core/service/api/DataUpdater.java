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
package org.jolokia.server.core.service.api;

import java.util.Deque;
import java.util.Map;

import javax.management.MBeanInfo;
import javax.management.ObjectName;

import org.jolokia.json.JSONObject;

/**
 * Interface for updating a JSON representation of an {@link MBeanInfo} when handling
 * {@link org.jolokia.server.core.request.JolokiaListRequest}
 *
 * @author roland
 * @since 13.09.11
 */
public abstract class DataUpdater extends AbstractJolokiaService<DataUpdater> implements JolokiaService<DataUpdater> {

    protected DataUpdater(int pOrderId) {
        super(DataUpdater.class, pOrderId);
    }

    /**
     * Get the key under which the extracted data should be added.
     *
     * @return key
     */
    public abstract String getKey();

    /**
     * Update the given map object with the data extracted from the given
     * MBeanInfo
     *
     * @param pMap map to update
     * @param pObjectName {@link ObjectName} of the {@link MBeanInfo} to extract from
     * @param pMBeanInfo info to extract from
     * @param pPathStack stack for further constraining the result
     */
    public void update(Map<String, Object> pMap, ObjectName pObjectName, MBeanInfo pMBeanInfo, Deque<String> pPathStack) {

        boolean isPathEmpty = pPathStack == null || pPathStack.isEmpty();
        String filter = pPathStack != null && !pPathStack.isEmpty() ? pPathStack.pop() : null;
        verifyThatPathIsEmpty(pPathStack);

        JSONObject attrMap = extractData(pObjectName, pMBeanInfo, filter);

        if (!attrMap.isEmpty()) {
            pMap.put(getKey(), attrMap);
        } else if (!isPathEmpty) {
            throw new IllegalArgumentException("Path given but extracted value is empty");
        }
    }

    /**
     * Do the real work by extracting the data from the MBeanInfo. This method should be overridden,
     * in its default implementation it returns an empty map
     *
     * @param pObjectName {@link ObjectName} of the {@link MBeanInfo} to extract from
     * @param pMBeanInfo the info object to examine
     * @param pFilter any additional filter to apply
     * @return the extracted data as an JSON object
     */
    public JSONObject extractData(ObjectName pObjectName, MBeanInfo pMBeanInfo,String pFilter) {
        return new JSONObject();
    }

    // ======================================================================================

    /**
     * Check whether the given path is empty, if not, then throw an exception
     *
     * @param pPathStack path to check
     */
    protected void verifyThatPathIsEmpty(Deque<String> pPathStack) {
        if (pPathStack != null && !pPathStack.isEmpty()) {
            throw new IllegalArgumentException("Path contains extra elements not usable for a list request: " + pPathStack);
        }
    }

    /**
     * Custom updater may return some {@link JSONObject} with information describing its role.
     * @return
     */
    public JSONObject getInfo() {
        return null;
    }

}
