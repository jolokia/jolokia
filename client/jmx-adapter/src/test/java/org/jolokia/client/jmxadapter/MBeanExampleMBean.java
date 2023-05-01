package org.jolokia.client.jmxadapter;

import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unused")
public interface MBeanExampleMBean {
    void unsupportedOperation();

    void unexpectedFailureMethod();

    List<String> getEmptyList();

  Set<String> getSetAttribute();

  Set<String> doEmptySetOperation();

  FieldWithMoreElementsThanTheTypeImpl getField();

    Map<String,String> getMapAttribute();

    Map<String,String> doMapOperation();
}
