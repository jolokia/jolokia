package org.jolokia.client.request;

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

import java.util.*;

import javax.management.ObjectName;

import org.json.simple.*;

/**
 * A request dealing with a single MBean.
 *
 * @author roland
 * @since Apr 24, 2010
 */
public abstract class AbtractJ4pMBeanRequest extends J4pRequest {

    // name of MBean to execute a request on
    private ObjectName objectName;

    protected AbtractJ4pMBeanRequest(J4pType pType,ObjectName pMBeanName,J4pTargetConfig pTargetConfig) {
        super(pType,pTargetConfig);
        objectName = pMBeanName;
    }

    /**
     * Get the object name for the MBean on which this request
     * operates
     *
     * @return MBean's name
     */
    public ObjectName getObjectName() {
        return objectName;
    }

    @Override
    List<String> getRequestParts() {
        List<String> ret = new ArrayList<String>();
        ret.add(objectName.getCanonicalName());
        return ret;
    }

    @Override
    JSONObject toJson() {
        JSONObject ret =  super.toJson();
        ret.put("mbean",objectName.getCanonicalName());
        return ret;
    }

    // ======================================================================================

}
