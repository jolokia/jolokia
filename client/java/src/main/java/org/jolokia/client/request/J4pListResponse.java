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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import javax.management.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Response for a {@link J4pListRequest}
 *
 * @author roland
 * @since 26.03.11
 */

public final class J4pListResponse extends J4pResponse<J4pListRequest> {

  J4pListResponse(J4pListRequest pRequest, JSONObject pJsonResponse) {
    super(pRequest, pJsonResponse);
  }

  public List<ObjectInstance> getObjectInstances(ObjectName name)
      throws MalformedObjectNameException {
    List<ObjectInstance> result = new LinkedList<ObjectInstance>();
    Map<String, Map<String, Map<String, String>>> value = this.getValue();
    //class, at top level means single result
    if (value.containsKey("class")) {
      return Collections.singletonList(new ObjectInstance(name, getClassName()));
    }
    List<ObjectInstance> instances = new ArrayList<ObjectInstance>(value.size());
    for (Entry<String, Map<String, Map<String, String>>> domain : value.entrySet()) {
      for (Entry<String, Map<String, String>> qualifier : domain.getValue().entrySet()) {
        result.add(new ObjectInstance(new ObjectName(domain.getKey() + ":" + qualifier.getKey()),
            qualifier.getValue().get("class")));
      }
    }
    return result;
  }

  public MBeanInfo getMbeanInfo() throws InstanceNotFoundException {
    JSONObject value = getValue();
    if (value.containsKey("class")) {
      return mBeanInfoFrom(value);
    }
    throw new InstanceNotFoundException();
  }

  @SuppressWarnings("unchecked")
  private MBeanInfo mBeanInfoFrom(JSONObject value) {
    return new MBeanInfo(
        "" + value.get("class"), "" + value.get("desc"), attributesFrom(
        (Map<String, Map<String, Object>>) value.get("attr")), new MBeanConstructorInfo[0],
        operationsFrom((Map<String, Map<String, Object>>) value.get("op")),
        new MBeanNotificationInfo[0]);
  }

  @SuppressWarnings("unchecked")
  private MBeanOperationInfo[] operationsFrom(Map<String, Map<String, Object>> operations) {
      if(operations == null) {
          return new MBeanOperationInfo[0];
      }
    final List<MBeanOperationInfo> result = new ArrayList<MBeanOperationInfo>(operations.size());

    for (Map.Entry<String, Map<String, Object>> operation : operations.entrySet()) {
        //if more operations with same name (overloaded), the value part is an array
        if(operation.getValue() instanceof JSONArray) {
            for(Object operationItem :  (JSONArray)operation.getValue()) {
                result.add(operationFrom(operation.getKey(),
                    (Map<String, Object>) operationItem));
            }
        } else {
            result.add(operationFrom(operation.getKey(), operation.getValue()));
        }
    }
    return result.toArray(new MBeanOperationInfo[0]);
  }

  private MBeanOperationInfo operationFrom(String operationName,
      Map<String, Object> operation) {
    return new MBeanOperationInfo(operationName, "" + operation.get("desc"),
        getArguments((JSONArray) operation.get("args")),
        "" + operation.get("ret"),
        MBeanOperationInfo.UNKNOWN);
  }

  @SuppressWarnings("unchecked")
  private MBeanParameterInfo[] getArguments(JSONArray args) {
    final MBeanParameterInfo[] result = new MBeanParameterInfo[args.size()];
    for (int i = 0; i < args.size(); i++) {
      result[i] = parameterInfo((Map<String, String>)args.get(i));
    }
    return result;
  }

  private MBeanParameterInfo parameterInfo(Map<String, String> parameter) {
    return new MBeanParameterInfo(parameter.get("name"), parameter.get("type"), parameter.get("desc"));
  }

  private MBeanAttributeInfo[] attributesFrom(Map<String, Map<String, Object>> attributes) {
      if(attributes == null) {
          return new MBeanAttributeInfo[0];
      }
      //sort alphabetically to match native MBeanServer
      attributes=new TreeMap<String, Map<String, Object>>(attributes);

      final MBeanAttributeInfo[] result = new MBeanAttributeInfo[attributes.size()];
    int index = 0;
    for (Map.Entry<String, Map<String, Object>> attribute : attributes.entrySet()) {
      result[index++] = attributeFrom(attribute);
    }
    return result;
  }

  private MBeanAttributeInfo attributeFrom(Entry<String, Map<String, Object>> attribute) {
    return new MBeanAttributeInfo(attribute.getKey(), "" + attribute.getValue().get("type"),
        "" + attribute.getValue().get("desc"), true, Boolean.TRUE == attribute.getValue().get("rw"),
        false);
  }

  public String getClassName() {
    return (String) ((JSONObject) getValue()).get("class");
  }

}
