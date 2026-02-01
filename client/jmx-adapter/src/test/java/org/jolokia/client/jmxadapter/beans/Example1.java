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
import javax.management.JMRuntimeException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.jolokia.client.jmxadapter.RemoteJmxAdapter;

public class Example1 implements Example1MXBean {
    private short s1 = (short) 3;
    private Short s2 = (short) 4;

    private User user = new User("Jolokia", new Address("Night City", 12345L));

    @Override
    public short getPrimitiveShort() {
        return s1;
    }

    @Override
    public Short getShort() {
        return s2;
    }

    @Override
    public void setPrimitiveShort(short v) {
        s1 = v;
    }

    @Override
    public void setShort(Short v) {
        s2 = v;
    }

    @Override
    public short[] getPrimitiveShortArray() {
        return new short[] { s1, s2 };
    }

    @Override
    public Short[] getShortArray() {
        return new Short[] { s1, s2 };
    }

    @Override
    public short[][] getPrimitiveShort2DArray() {
        return new short[][] {
                new short[] { 1, 2, 3 },
                new short[] { 4, 5, 6 }
        };
    }

    @Override
    public Short[][] getShort2DArray() {
        return new Short[][] {
                new Short[] { 4, 5, 6 },
                new Short[] { 1, 2, 3 }
        };
    }

    @Override
    public CompositeData getCompositeMXData() {
        try {
            CompositeType type1 = typeOfMXCompositeDataAttribute();
            CompositeType type2 = (CompositeType) type1.getType("value");
            return new CompositeDataSupport(type1, Map.of(
                    "key", "k1",
                    "value", new CompositeDataSupport(type2, Map.of(
                            "key", "k1_1",
                            "value", (short) 42
                    ))
            ));
        } catch (OpenDataException e) {
            throw new JMRuntimeException(e.getMessage());
        }
    }

    @Override
    public CompositeData[] getCompositeMXDataArray() {
        return new CompositeData[] {
                getCompositeMXData(),
                getCompositeMXData(),
                getCompositeMXData(),
                getCompositeMXData()
        };
    }

    /**
     * Should return manually constructed {@link TabularType} as if it was prepared by {@code com.sun.jmx.mbeanserver.MXBeanIntrospector}
     * @return
     */
    public static CompositeType typeOfMXCompositeDataAttribute() {
        try {
            CompositeType internal = new CompositeType("java.util.Map<java.lang.String, java.lang.Short>", "some description",
                    new String[] { "key", "value" },
                    new String[] { "key", "value" },
                    new OpenType<?>[] { SimpleType.STRING, SimpleType.SHORT });

            return new CompositeType("java.util.Map<java.lang.String, java.util.Map<java.lang.String, java.lang.Short>>", "some description",
                    new String[] { "key", "value" },
                    new String[] { "key", "value" },
                    new OpenType<?>[] { SimpleType.STRING, internal });
        } catch (OpenDataException e) {
            throw new JMRuntimeException(e.getMessage());
        }
    }

    @Override
    public CompositeData getCompositeData() {
        try {
            CompositeType userType = typeOfNonMXCompositeDataAttribute();
            CompositeType addressType = (CompositeType) userType.getType("address");
            return new CompositeDataSupport(userType, Map.of(
                    "firstName", "John",
                    "lastName", "H2O",
                    "address", new CompositeDataSupport(addressType, Map.of(
                            "city", "Szczebrzeszyn",
                            "zip", 123456L
                    ))
            ));
        } catch (OpenDataException e) {
            throw new JMRuntimeException(e.getMessage());
        }
    }

    @Override
    public CompositeData[] getCompositeDataArray() {
        return new CompositeData[] {
                getCompositeData(),
                getCompositeData(),
                getCompositeData()
        };
    }

    /**
     * Should return manually constructed {@link TabularType} as if it was prepared by {@code com.sun.jmx.mbeanserver.MXBeanIntrospector}
     * @return
     */
    public static CompositeType typeOfNonMXCompositeDataAttribute() {
        try {
            CompositeType addressType = new CompositeType("Address of a User", "some description",
                    new String[] { "city", "zip" },
                    new String[] { "City", "ZIP code" },
                    new OpenType<?>[] { SimpleType.STRING, SimpleType.LONG });

            return new CompositeType("User", "some description",
                    new String[] { "firstName", "lastName", "address" },
                    new String[] { "First Name", "Last Name", "Address" },
                    new OpenType<?>[] { SimpleType.STRING, SimpleType.STRING, addressType });
        } catch (OpenDataException e) {
            throw new JMRuntimeException(e.getMessage());
        }
    }

    @Override
    public TabularData getTabularMXData() {
        TabularType tabularType = typeOfMXTabularDataAttribute();
        CompositeType rowType = tabularType.getRowType();
        TabularType innerMapType = (TabularType) rowType.getType("value");
        CompositeType innerRowType = innerMapType.getRowType();
        TabularDataSupport data = new TabularDataSupport(tabularType);
        TabularDataSupport innerData = new TabularDataSupport(innerMapType);
        try {
            innerData.put(new CompositeDataSupport(innerRowType, Map.of(
                    "key", "ka",
                    "value", (short) 42
            )));
            innerData.put(new CompositeDataSupport(innerRowType, Map.of(
                    "key", "kb",
                    "value", (short) 42
            )));
            data.put(new CompositeDataSupport(rowType, Map.of(
                    "key", "k1",
                    "value", innerData
            )));
            data.put(new CompositeDataSupport(rowType, Map.of(
                    "key", "k2",
                    "value", innerData
            )));
        } catch (OpenDataException e) {
            throw new JMRuntimeException(e.getMessage());
        }

        return data;
    }

    @Override
    public TabularData[] getTabularMXDataArray() {
        return new TabularData[] {
                getTabularMXData(),
                getTabularMXData(),
                getTabularMXData()
        };
    }

    /**
     * Should return manually constructed {@link TabularType} as if it was prepared by {@code com.sun.jmx.mbeanserver.MXBeanIntrospector}
     * @return
     */
    public static TabularType typeOfMXTabularDataAttribute() {
        try {
            CompositeType internal = new CompositeType("java.util.Map<java.lang.String, java.lang.Short>", "some description",
                    new String[] { "key", "value" },
                    new String[] { "key", "value" },
                    new OpenType<?>[] { SimpleType.STRING, SimpleType.SHORT });

            TabularType rowValueType = new TabularType(internal.getTypeName(), "some description", internal, new String[] { "key" });
            CompositeType rowType = new CompositeType("java.util.Map<java.lang.String, java.util.Map<java.lang.String, java.lang.Short>>", "some description",
                    new String[] { "key", "value" },
                    new String[] { "key", "value" },
                    new OpenType<?>[] { SimpleType.STRING, rowValueType });
            return new TabularType(rowType.getTypeName(), "some description", rowType, new String[] { "key" });
        } catch (OpenDataException e) {
            throw new JMRuntimeException(e.getMessage());
        }
    }

    @Override
    public TabularData getTabularData() {
        TabularDataSupport data = new TabularDataSupport(typeOfNonMXTabularDataAttribute());
        CompositeType userType = typeOfNonMXCompositeDataAttribute();
        CompositeType addressType = (CompositeType) userType.getType("address");
        try {
            data.put(new CompositeDataSupport(userType, Map.of(
                    "firstName", "John",
                    "lastName", "H2O",
                    "address", new CompositeDataSupport(addressType, Map.of(
                            "city", "Szczebrzeszyn",
                            "zip", 123456L
                    ))
            )));
            data.put(new CompositeDataSupport(userType, Map.of(
                    "firstName", "John",
                    "lastName", "H2O",
                    "address", new CompositeDataSupport(addressType, Map.of(
                            "city", "Szczebrzeszyn",
                            "zip", 123457L
                    ))
            )));
            data.put(new CompositeDataSupport(userType, Map.of(
                    "firstName", "John",
                    "lastName", "H2O",
                    "address", new CompositeDataSupport(addressType, Map.of(
                            "city", "Komoronoboronowice",
                            "zip", 123456L
                    ))
            )));
        } catch (OpenDataException e) {
            throw new JMRuntimeException(e.getMessage());
        }

        return data;
    }

    @Override
    public TabularData[] getTabularDataArray() {
        return new TabularData[] {
                getTabularData(),
                getTabularData(),
                getTabularData()
        };
    }

    public static TabularType typeOfNonMXTabularDataAttribute() {
        try {
            CompositeType rowType = typeOfNonMXCompositeDataAttribute();
            return new TabularType(rowType.getTypeName(), "some description", rowType, new String[] { "address" });
        } catch (OpenDataException e) {
            throw new JMRuntimeException(e.getMessage());
        }
    }

    @Override
    public TabularData getTabularDataWithMultipleKeys() {
        TabularDataSupport data = new TabularDataSupport(typeOfMultiIndexTabularDataAttribute());
        try {
            data.put(new CompositeDataSupport(data.getTabularType().getRowType(),
                    new String[] { "key1", "key2", "key3", "value1", "value2" },
                    new Object[] { "id1", "id2", 1L, (short) 42, "some value" })
            );
            data.put(new CompositeDataSupport(data.getTabularType().getRowType(),
                    new String[] { "key1", "key2", "key3", "value1", "value2" },
                    new Object[] { "id1", "id2", 2L, (short) 42, "some value" })
            );
            data.put(new CompositeDataSupport(data.getTabularType().getRowType(),
                    new String[] { "key1", "key2", "key3", "value1", "value2" },
                    new Object[] { "id2", "id2", 2L, (short) 42, "some value" })
            );
        } catch (OpenDataException e) {
            throw new JMRuntimeException(e.getMessage());
        }
        return data;
    }

    public static TabularType typeOfMultiIndexTabularDataAttribute() {
        try {
            CompositeType rowType = new CompositeType("TabularDataWithMultipleKeys", "some description",
                    new String[] { "key1", "key2", "key3", "value1", "value2" },
                    new String[] { "key1", "key2", "key3", "value1", "value2" },
                    new OpenType<?>[] { SimpleType.STRING, SimpleType.STRING, SimpleType.LONG, SimpleType.SHORT, SimpleType.STRING });

            return new TabularType(rowType.getTypeName(), "some description", rowType, new String[] { "key1", "key2", "key3" });
        } catch (OpenDataException e) {
            throw new JMRuntimeException(e.getMessage());
        }
    }

    @Override
    public Map<String, Map<String, Short>> getProperTabularType() {
        return Map.of(
                "k1", Map.of("k1a", (short) 1, "k1b", (short) 3),
                "k2", Map.of("k2a", (short) 2, "k2b", (short) 4)
        );
    }

    @Override
    public Map<String, Map<String, Short>>[] getProperTabularTypeArray() {
        return new Map[] {
                Map.of(
                        "k1", Map.of("k1a", (short) 1, "k1b", (short) 3),
                        "k2", Map.of("k2a", (short) 2, "k2b", (short) 4)
                ),
                Map.of(
                        "k3", Map.of("k3a", (short) 1, "k3b", (short) 3),
                        "k4", Map.of("k4a", (short) 2, "k4b", (short) 4)
                )
        };
    }

    @Override
    public Map<ObjectName, Float> getAlmostProperTabularType() {
        try {
            return Map.of(
                    new ObjectName("a:b=c"), 42.3F
            );
        } catch (MalformedObjectNameException e) {
            throw new JMRuntimeException(e.getMessage());
        }
    }

    public static TabularType typeOfAlmostProperTabularDataAttribute() {
        try {
            CompositeType rowType = new CompositeType("java.util.Map<javax.management.ObjectName, java.lang.Float>", "some description",
                    new String[] { "key", "value" },
                    new String[] { "key", "value" },
                    new OpenType<?>[] { SimpleType.OBJECTNAME, SimpleType.FLOAT }
            );
            return new TabularType("java.util.Map<javax.management.ObjectName, java.lang.Float>", "some description", rowType, new String[] { "key" });
        } catch (OpenDataException e) {
            throw new JMRuntimeException(e.getMessage());
        }
    }

    @Override
    public Map<Long[], Float> getComplexKeyedTabularType() {
        return Map.of(
                new Long[] { 1L, 2L }, 42.2F,
                new Long[] { 3L }, 22.2F
        );
    }

    public static TabularType typeOfComplexKeyedTabularDataAttribute() {
        try {
            CompositeType rowType = new CompositeType("java.util.Map<java.lang.Long[], java.lang.Float>", "some description",
                    new String[] { "key", "value" },
                    new String[] { "key", "value" },
                    new OpenType<?>[] { new ArrayType<>(1, SimpleType.LONG), SimpleType.FLOAT }
            );
            return new TabularType("java.util.Map<java.lang.Long[], java.lang.Float>", "some description", rowType, new String[] { "key" });
        } catch (OpenDataException e) {
            throw new JMRuntimeException(e.getMessage());
        }
    }

    @Override
    public User getUser() {
        return this.user;
    }

    @Override
    public User[] getUsers() {
        return this.user == null ? new User[0] : new User[] { this.user };
    }

    @Override
    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public void setUsers(User[] users) {
        this.user = users.length > 0 ? users[0] : null;
    }

    @Override
    public List<String> getList() {
        return List.of("a", "b", "c");
    }

    @Override
    public List<String>[] getLists() {
        return new List[] {
                List.of("a", "b", "c"),
                List.of("d", "e", "f")
        };
    }

    @Override
    public Set<Short> getSet() {
        return Set.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5);
    }

    @Override
    public Set<Short>[] getSets() {
        return new Set[] {
                Set.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5),
                Set.of((short) 6, (short) 7)
        };
    }

}
