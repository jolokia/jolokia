package org.jolokia.client.jmxadapter;

import java.util.Collections;
import java.util.List;

public class MBeanExample implements MBeanExampleMXBean {

    @Override
    @SuppressWarnings("unused")
    public void unsupportedOperation() {
        throw new UnsupportedOperationException("ha ha");
    }

    @Override
    @SuppressWarnings("unused")
    public void unexpectedFailureMethod() {
        throw new NullPointerException("uh oh");
    }

    @Override
    public List<String> getEmptyList() {
        return Collections.emptyList();
    }
    @Override
    public FieldWithMoreElementsThanTheTypeImpl getField() {
        return new FieldWithMoreElementsThanTheTypeImpl("a value",
            "another value");
    }
}
