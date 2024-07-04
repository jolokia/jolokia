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
import javax.management.*;

import org.json.JSONArray;
import org.json.JSONObject;

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

  public List<ObjectInstance> getObjectInstances(ObjectName name) throws MalformedObjectNameException {
      List<ObjectInstance> result = new LinkedList<>();
      JSONObject value = this.getValue();
      //class, at top level means single result
      if (value.has("class")) {
          return Collections.singletonList(new ObjectInstance(name, getClassName()));
      }
      List<ObjectInstance> instances = new ArrayList<>(value.length());
      for (String domainKey : value.keySet()) {
          JSONObject domain = value.getJSONObject(domainKey);
          for (String quelifierKey : domain.keySet()) {
              JSONObject qualifier = domain.getJSONObject(quelifierKey);
              result.add(new ObjectInstance(new ObjectName(domainKey + ":" + quelifierKey),
                  qualifier.getString("class")));
          }
      }
      return result;
  }

  public MBeanInfo getMbeanInfo() throws InstanceNotFoundException {
    JSONObject value = getValue();
    if (value.has("class")) {
      return mBeanInfoFrom(value);
    }
    throw new InstanceNotFoundException();
  }

  private MBeanInfo mBeanInfoFrom(JSONObject value) {
    return new MBeanInfo(
        "" + value.get("class"), "" + value.get("desc"), attributesFrom(
        value.optJSONObject("attr")), new MBeanConstructorInfo[0],
        operationsFrom(value.optJSONObject("op")),
        new MBeanNotificationInfo[0]);
  }

  private MBeanOperationInfo[] operationsFrom(JSONObject operations) {
      if(operations == null) {
          return new MBeanOperationInfo[0];
      }
    final List<MBeanOperationInfo> result = new ArrayList<>(operations.length());

    for (String key : operations.keySet()) {
        Object operation = operations.get(key);
        //if more operations with same name (overloaded), the value part is an array
        if (operation instanceof JSONArray) {
            for (Object operationItem : (JSONArray) operation) {
                result.add(operationFrom(key, (JSONObject) operationItem));
            }
        } else {
            result.add(operationFrom(key, (JSONObject) operation));
        }
    }
    return result.toArray(new MBeanOperationInfo[0]);
  }

  private MBeanOperationInfo operationFrom(String operationName,
      JSONObject operation) {
    return new MBeanOperationInfo(operationName, "" + operation.get("desc"),
        getArguments((JSONArray) operation.get("args")),
        "" + operation.get("ret"),
        MBeanOperationInfo.UNKNOWN);
  }

  private MBeanParameterInfo[] getArguments(JSONArray args) {
    final MBeanParameterInfo[] result = new MBeanParameterInfo[args.length()];
    for (int i = 0; i < args.length(); i++) {
      result[i] = parameterInfo((JSONObject)args.get(i));
    }
    return result;
  }

  private MBeanParameterInfo parameterInfo(JSONObject parameter) {
    return new MBeanParameterInfo(parameter.getString("name"), parameter.getString("type"), parameter.getString("desc"));
  }

  private MBeanAttributeInfo[] attributesFrom(JSONObject attributes) {
      Set<String> keySet;
      if (attributes == null) {
          return new MBeanAttributeInfo[0];
      }
      //sort alphabetically to match native MBeanServer
      keySet = new TreeSet<>(attributes.keySet());

      final MBeanAttributeInfo[] result = new MBeanAttributeInfo[keySet.size()];
      int index = 0;
      for (String key : keySet) {
          result[index++] = attributeFrom(key, attributes.getJSONObject(key).toMap());
      }
      return result;
  }

  private MBeanAttributeInfo attributeFrom(String key, Map<String, Object> attribute) {
    return new MBeanAttributeInfo(key, "" + attribute.get("type"),
        "" + attribute.get("desc"), true, Boolean.TRUE == attribute.get("rw"),
        false);
  }

  public String getClassName() {
    return (String) ((JSONObject) getValue()).get("class");
  }

}
