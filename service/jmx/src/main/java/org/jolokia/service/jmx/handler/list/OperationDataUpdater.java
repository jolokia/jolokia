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

import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.ObjectName;
import javax.management.openmbean.OpenMBeanOperationInfo;
import javax.management.openmbean.OpenMBeanParameterInfo;
import javax.management.openmbean.OpenType;

import org.jolokia.converter.object.OpenTypeHelper;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.jolokia.server.core.service.api.DataUpdater;
import org.jolokia.server.core.service.api.OpenTypeAwareDataUpdate;
import org.jolokia.server.core.util.JsonUtil;

import static org.jolokia.service.jmx.handler.list.DataKeys.*;
/**
 * MBean info data updater for operations
 *
 * @author roland
 * @since 13.09.11
 */
class OperationDataUpdater extends DataUpdater implements OpenTypeAwareDataUpdate {

    protected OperationDataUpdater() {
        super(100);
    }

    @Override
    public String getKey() {
        return OPERATIONS.getKey();
    }

    @Override
    public JSONObject extractData(ObjectName pObjectName, MBeanInfo pMBeanInfo, String pOperation) {
        JSONObject opMap = new JSONObject();

        for (MBeanOperationInfo opInfo : pMBeanInfo.getOperations()) {
            if (pOperation == null || opInfo.getName().equals(pOperation)) {
                JSONObject map = new JSONObject();
                JSONArray argList = new JSONArray(opInfo.getSignature().length);
                for (MBeanParameterInfo paramInfo : opInfo.getSignature()) {
                    JSONObject args = parameterData(paramInfo);
                    argList.add(args);
                }
                map.put(ARGS.getKey(), argList);
                map.put(RETURN_TYPE.getKey(), opInfo.getReturnType());
                map.put(DESCRIPTION.getKey(), opInfo.getDescription());
                JsonUtil.addJSONObjectToJSONObject(opMap, opInfo.getName(), map);
            }
        }
        return opMap;
    }

    @Override
    public JSONObject extractDataWithOpenTypes(ObjectName pObjectName, MBeanInfo pMBeanInfo, String pOperation) {
        JSONObject opMap = new JSONObject();

        for (MBeanOperationInfo opInfo : pMBeanInfo.getOperations()) {
            if (pOperation == null || opInfo.getName().equals(pOperation)) {
                JSONObject map = new JSONObject();
                JSONArray argList = new JSONArray(opInfo.getSignature().length);
                for (MBeanParameterInfo paramInfo : opInfo.getSignature()) {
                    JSONObject args = parameterData(paramInfo);
                    if (paramInfo instanceof OpenMBeanParameterInfo openMBeanParameterInfo) {
                        args.put(OPEN_TYPE.getKey(), OpenTypeHelper.toJSON(openMBeanParameterInfo.getOpenType(), paramInfo));
                    } else {
                        OpenType<?> openType = OpenTypeHelper.findOpenType(paramInfo.getDescriptor());
                        if (openType != null) {
                            args.put(OPEN_TYPE.getKey(), OpenTypeHelper.toJSON(openType, paramInfo));
                        }
                    }
                    argList.add(args);
                }
                map.put(ARGS.getKey(), argList);
                map.put(RETURN_TYPE.getKey(), opInfo.getReturnType());
                map.put(DESCRIPTION.getKey(), opInfo.getDescription());
                if (opInfo instanceof OpenMBeanOperationInfo openMBeanOperationInfo) {
                    map.put(RETURN_OPEN_TYPE.getKey(), OpenTypeHelper.toJSON(openMBeanOperationInfo.getReturnOpenType(), opInfo));
                } else {
                    OpenType<?> openType = OpenTypeHelper.findOpenType(opInfo.getDescriptor());
                    if (openType != null) {
                        map.put(RETURN_OPEN_TYPE.getKey(), OpenTypeHelper.toJSON(openType, opInfo));
                    }
                }
                JsonUtil.addJSONObjectToJSONObject(opMap, opInfo.getName(), map);
            }
        }
        return opMap;
    }

    private static JSONObject parameterData(MBeanParameterInfo paramInfo) {
        JSONObject args = new JSONObject();
        args.put(DESCRIPTION.getKey(), paramInfo.getDescription());
        args.put(NAME.getKey(), paramInfo.getName());
        args.put(TYPE.getKey(), paramInfo.getType());
        return args;
    }

}
