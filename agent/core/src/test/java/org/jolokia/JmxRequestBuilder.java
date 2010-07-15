package org.jolokia;

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
