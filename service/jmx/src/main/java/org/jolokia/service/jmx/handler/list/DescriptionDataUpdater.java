package org.jolokia.service.jmx.handler.list;

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

import java.util.Deque;
import java.util.Map;

import javax.management.MBeanInfo;
import javax.management.ObjectName;

import org.jolokia.server.core.service.api.DataUpdater;

import static org.jolokia.service.jmx.handler.list.DataKeys.DESCRIPTION;

/**
 * InfoData updater for the MBean description
 *
 * @author roland
 * @since 13.09.11
 */
class DescriptionDataUpdater extends DataUpdater {

    protected DescriptionDataUpdater() {
        super(100);
    }

    /** {@inheritDoc} */
    @Override
    public String getKey() {
        return DESCRIPTION.getKey();
    }

    /**
     * The update method is overridden here directly since the usual extraction method
     * is not needed
     *
     * {@inheritDoc}
     */
    @Override
    public void update(Map<String, Object> pMap, ObjectName pObjectName, MBeanInfo pMBeanInfo, Deque<String> pPathStack) {
        verifyThatPathIsEmpty(pPathStack);
        pMap.put(getKey(), pMBeanInfo.getDescription());
    }
}
