package org.jolokia.client.jmxadapter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    @Override
    public Map<String,String> getMapAttribute() {
        return Collections.singletonMap("foo", "bar");
    }

    @Override
    public Map<String,String> doMapOperation() {
        return getMapAttribute();
    }
}
