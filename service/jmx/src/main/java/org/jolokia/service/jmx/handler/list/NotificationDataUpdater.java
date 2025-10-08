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

package org.jolokia.service.jmx.handler.list;

import java.util.Collections;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.ObjectName;

import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.server.core.service.api.DataUpdater;

import static org.jolokia.service.jmx.handler.list.DataKeys.*;

/**
 * InfoData updater for notifications
 *
 * @author roland
 * @since 13.09.11
 */
class NotificationDataUpdater extends DataUpdater {

    protected NotificationDataUpdater() {
        super(100);
    }

    /** {@inheritDoc} */
    @Override
    public String getKey() {
        return NOTIFICATIONS.getKey();
    }

    /** {@inheritDoc} */
    @Override
    public JSONObject extractData(ObjectName pObjectName, MBeanInfo pMBeanInfo, String pNotification) {
        JSONObject notMap = new JSONObject();
        for (MBeanNotificationInfo notInfo : pMBeanInfo.getNotifications()) {
            if (pNotification == null || notInfo.getName().equals(pNotification)) {
                JSONObject map = new JSONObject();
                map.put(NAME.getKey(), notInfo.getName());
                map.put(DESCRIPTION.getKey(), notInfo.getDescription());
                String[] types = notInfo.getNotifTypes();
                JSONArray tList = new JSONArray(types.length);
                Collections.addAll(tList, types);
                map.put(TYPES.getKey(), tList);
                notMap.put(notInfo.getName(), map);
            }
        }
        return notMap;
    }
}
