package org.jolokia;

/*
 *  Copyright 2009-2010 Roland Huss
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

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.Arrays;
import java.util.List;

/**
 * Helper class for unit testing
 *
 * @author roland
 * @since Mar 6, 2010
 */
public class JmxRequestBuilder {

    private JmxRequest request;


    public JmxRequestBuilder(JmxRequest.Type pType, String pObjectName) throws MalformedObjectNameException {
        request = new JmxRequest(pType,pObjectName);
    }

    public JmxRequestBuilder(JmxRequest.Type pType, ObjectName pMBean) throws MalformedObjectNameException {
        request = new JmxRequest(pType,pMBean.getCanonicalName());
    }

    public JmxRequest build() {
        return request;
    }

    public JmxRequestBuilder attribute(String pAttribute) {
        request.setAttributeName(pAttribute);
        return this;
    }

    public JmxRequestBuilder attributes(List<String> pAttributeNames) {
        request.setAttributeNames(pAttributeNames);
        return this;
    }

    public JmxRequestBuilder attributes(String ... pAttributeNames) {
        request.setAttributeNames(Arrays.asList(pAttributeNames));
        return this;
    }

    public JmxRequestBuilder operation(String pOperation) {
        request.setOperation(pOperation);
        return this;
    }

    public JmxRequestBuilder value(String pValue) {
        request.setValue(pValue);
        return this;
    }

    public JmxRequestBuilder extraArgs(List<String> pExtraArgs) {
        request.setExtraArgs(pExtraArgs);
        return this;
    }

    public JmxRequestBuilder extraArgs(String ... pExtraArgs) {
        request.setExtraArgs(Arrays.asList(pExtraArgs));
        return this;
    }
}
