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

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;

import org.json.simple.JSONObject;

import static org.jolokia.handler.list.DataKeys.*;
/**
 * InfoData updater for attributes
 *
 * @author roland
 * @since 13.09.11
 */
class AttributeDataUpdater extends DataUpdater {

    /** {@inheritDoc} */
    @Override
    public String getKey() {
        return ATTRIBUTES.getKey();
    }

    /** {@inheritDoc} */
    @Override
    protected JSONObject extractData(MBeanInfo pMBeanInfo, String attribute) {
        JSONObject attrMap = new JSONObject();

        for (MBeanAttributeInfo attrInfo : pMBeanInfo.getAttributes()) {
            if (attribute == null || attrInfo.getName().equals(attribute)) {
                JSONObject map = new JSONObject();
                map.put(TYPE.getKey(), attrInfo.getType());
                map.put(DESCRIPTION.getKey(), attrInfo.getDescription());
                map.put(READ_WRITE.getKey(), Boolean.valueOf(attrInfo.isWritable() && attrInfo.isReadable()));
                attrMap.put(attrInfo.getName(), map);
            }
        }
        return attrMap;
    }
}
