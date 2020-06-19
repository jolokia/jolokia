package org.jolokia.client.jmxadapter;

import java.util.List;

@SuppressWarnings("unused")
public interface MBeanExampleMXBean {
    void unsupportedOperation();

    void unexpectedFailureMethod();

    List<String> getEmptyList();

    FieldWithMoreElementsThanTheTypeImpl getField();
}
