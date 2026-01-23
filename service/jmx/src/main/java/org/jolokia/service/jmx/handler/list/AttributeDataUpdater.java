/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.service.jmx.handler.list;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.ObjectName;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenType;

import org.jolokia.converter.object.OpenTypeHelper;
import org.jolokia.json.JSONObject;
import org.jolokia.server.core.service.api.DataUpdater;

import static org.jolokia.service.jmx.handler.list.DataKeys.*;

/**
 * InfoData updater for attributes
 *
 * @author roland
 * @since 13.09.11
 */
class AttributeDataUpdater extends DataUpdater {

    protected AttributeDataUpdater() {
        super(100);
    }

    @Override
    public String getKey() {
        return ATTRIBUTES.getKey();
    }

    @Override
    public JSONObject extractData(ObjectName pObjectName, MBeanInfo pMBeanInfo, String attribute) {
        JSONObject attrMap = new JSONObject();

        for (MBeanAttributeInfo attrInfo : pMBeanInfo.getAttributes()) {
            if (attribute == null || attrInfo.getName().equals(attribute)) {
                JSONObject map = new JSONObject();
                map.put(TYPE.getKey(), attrInfo.getType());
                if (attrInfo instanceof OpenMBeanAttributeInfo openMBeanAttributeInfo) {
                    map.put(OPEN_TYPE.getKey(), OpenTypeHelper.toJSON(openMBeanAttributeInfo.getOpenType(), attrInfo));
                } else {
                    OpenType<?> openType = OpenTypeHelper.findOpenType(attrInfo.getDescriptor());
                    if (openType != null) {
                        map.put(OPEN_TYPE.getKey(), OpenTypeHelper.toJSON(openType, attrInfo));
                    }
                }
                map.put(DESCRIPTION.getKey(), attrInfo.getDescription());
                map.put(READ.getKey(), attrInfo.isReadable());
                map.put(WRITE.getKey(), attrInfo.isWritable());
                map.put(READ_WRITE.getKey(), attrInfo.isWritable() && attrInfo.isReadable());
                map.put(IS.getKey(), attrInfo.isIs());
                attrMap.put(attrInfo.getName(), map);
            }
        }
        return attrMap;
    }

}
