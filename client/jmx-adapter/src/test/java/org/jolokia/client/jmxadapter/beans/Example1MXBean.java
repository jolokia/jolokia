/*
 * Copyright 2009-2026 Roland Huss
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
package org.jolokia.client.jmxadapter.beans;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

public interface Example1MXBean {

    short getPrimitiveShort();
    Short getShort();
    void setPrimitiveShort(short v);
    void setShort(Short v);

    short[] getPrimitiveShortArray();
    Short[] getShortArray();
    short[][] getPrimitiveShort2DArray();
    Short[][] getShort2DArray();

    // see https://docs.oracle.com/en/java/javase/17/docs/api/java.management/javax/management/MXBean.html#mapping-rules

    // that's not how you use OpenTypes in MXBean - both will actually be converted to CompositeData

    // attributes of types which use arbitrary definitions of CompositeType/TabularType
    // for MXBeans both will be treated as "beans", so converted to CompositeTypes
    CompositeData getCompositeData();
    CompositeData[] getCompositeDataArray();
    TabularData getTabularData();
    TabularData[] getTabularDataArray();

    TabularData getTabularDataWithMultipleKeys();

    // attributes of types which use MX definitions of CompositeType/TabularType ("key" and "value" items, ["key"] index)
    CompositeData getCompositeMXData();
    CompositeData[] getCompositeMXDataArray();
    TabularData getTabularMXData();
    TabularData[] getTabularMXDataArray();

    // this is proper MXBean attribute type converted to TabularData
    Map<String, Map<String, Short>> getProperTabularType();
    // this is proper MXBean attribute type converted to TabularData[]
    Map<String, Map<String, Short>>[] getProperTabularTypeArray();

    Map<ObjectName, Float> getAlmostProperTabularType();
    Map<Long[], Float> getComplexKeyedTabularType();

    // this is proper MXBean attribute type converted to CompositeData
    User getUser();
    User[] getUsers();
    void setUser(User user);
    void setUsers(User[] users);

    List<String> getList();
    List<String>[] getLists();

    Set<Short> getSet();
    Set<Short>[] getSets();

}
