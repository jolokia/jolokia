/*
 * Copyright 2009-2025 Roland Huss
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

import javax.management.MBeanConstructorInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanParameterInfo;
import javax.management.ObjectName;

import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.server.core.service.api.DataUpdater;
import org.jolokia.server.core.util.JsonUtil;

import static org.jolokia.service.jmx.handler.list.DataKeys.*;

public class ConstructorDataUpdater extends DataUpdater {

    protected ConstructorDataUpdater() {
        super(100);
    }

    @Override
    public String getKey() {
        return CONSTRUCTORS.getKey();
    }

    @Override
    public JSONObject extractData(ObjectName pObjectName, MBeanInfo pMBeanInfo, String pName) {
        JSONObject opMap = new JSONObject();

        for (MBeanConstructorInfo ctorInfo : pMBeanInfo.getConstructors()) {
            if (pName == null || ctorInfo.getName().equals(pName)) {
                JSONObject map = new JSONObject();
                JSONArray argList = new JSONArray(ctorInfo.getSignature().length);
                for (MBeanParameterInfo paramInfo : ctorInfo.getSignature()) {
                    JSONObject args = new JSONObject();
                    args.put(DESCRIPTION.getKey(), paramInfo.getDescription());
                    args.put(NAME.getKey(), paramInfo.getName());
                    args.put(TYPE.getKey(), paramInfo.getType());
                    argList.add(args);
                }
                map.put(ARGS.getKey(), argList);
                map.put(DESCRIPTION.getKey(), ctorInfo.getDescription());
                JsonUtil.addJSONObjectToJSONObject(opMap, ctorInfo.getName(), map);
            }
        }
        return opMap;
    }

}
