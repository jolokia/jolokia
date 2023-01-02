package org.jolokia.client.jmxadapter;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public interface MBeanExampleMXBean {
    void unsupportedOperation();

    void unexpectedFailureMethod();

    List<String> getEmptyList();

    FieldWithMoreElementsThanTheTypeImpl getField();

    Map<String,String> getMapAttribute();

    Map<String,String> doMapOperation();
}
