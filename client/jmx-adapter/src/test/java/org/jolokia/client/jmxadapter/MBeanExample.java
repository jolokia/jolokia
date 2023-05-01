package org.jolokia.client.jmxadapter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MBeanExample implements MBeanExampleMBean {

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
    public Set<String> getSetAttribute() {
        return new HashSet<String>(Arrays.asList("foo", "bar"));
    }

    @Override
    public Set<String> doEmptySetOperation() {
        return Collections.emptySet();
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
