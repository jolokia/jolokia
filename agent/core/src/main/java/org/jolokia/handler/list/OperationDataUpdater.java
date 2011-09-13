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

import java.util.*;

import javax.management.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import static org.jolokia.handler.list.DataKeys.*;
/**
 * MBean info data updater for operations
 *
 * @author roland
 * @since 13.09.11
 */
class OperationDataUpdater extends DataUpdater {

    /** {@inheritDoc} */
    @Override
    String getKey() {
        return OPERATIONS.getKey();
    }

    /** {@inheritDoc} */
    @Override
    protected JSONObject extractData(MBeanInfo pMBeanInfo, String pOperation) {
        JSONObject opMap = new JSONObject();

        for (MBeanOperationInfo opInfo : pMBeanInfo.getOperations()) {
            if (pOperation == null || opInfo.getName().equals(pOperation)) {
                JSONObject map = new JSONObject();
                JSONArray argList = new JSONArray();
                for (MBeanParameterInfo paramInfo : opInfo.getSignature()) {
                    JSONObject args = new JSONObject();
                    args.put(DESCRIPTION.getKey(), paramInfo.getDescription());
                    args.put(NAME.getKey(), paramInfo.getName());
                    args.put(TYPE.getKey(), paramInfo.getType());
                    argList.add(args);
                }
                map.put(ARGS.getKey(), argList);
                map.put(RETURN_TYPE.getKey(), opInfo.getReturnType());
                map.put(DESCRIPTION.getKey(), opInfo.getDescription());
                Object ops = opMap.get(opInfo.getName());
                if (ops != null) {
                    if (ops instanceof List) {
                        // If it is already a list, simply add it to the end
                        ((List) ops).add(map);
                    } else if (ops instanceof Map) {
                        // If it is a map, add a list with two elements
                        // (the old one and the new one)
                        JSONArray opList = new JSONArray();
                        opList.add(ops);
                        opList.add(map);
                        opMap.put(opInfo.getName(), opList);
                    } else {
                        throw new IllegalArgumentException("Internal: list, addOperations: Expected Map or List, not "
                                                           + ops.getClass());
                    }
                } else {
                    // No value set yet, simply add the map as plain value
                    opMap.put(opInfo.getName(), map);
                }
            }
        }
        return opMap;
    }
}
