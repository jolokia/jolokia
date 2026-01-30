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
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularType;

public class Example1 implements Example1MXBean {
    private short s1;
    private Short s2;

    private User user;

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
    public CompositeData getCompositeData() {
        return null;
    }

    @Override
    public CompositeData[] getCompositeDataArray() {
        return new CompositeData[0];
    }

    @Override
    public TabularData getTabularData() {
        return null;
    }

    @Override
    public TabularData[] getTabularDataArray() {
        return new TabularData[0];
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

    /**
     * Should return manually constructed {@link TabularType} as if it was prepared by {@code com.sun.jmx.mbeanserver.MXBeanIntrospector}
     * @return
     */
    public static TabularType typeOfProperTabularTypeAttribute() throws OpenDataException {
        CompositeType valueRowType = new CompositeType("java.util.Map<java.lang.String, java.lang.Short>", "some description",
                new String[] { "key", "value" },
                new String[] { "key", "value" },
                new OpenType<?>[] { SimpleType.STRING, SimpleType.SHORT });
        TabularType valueType = new TabularType(valueRowType.getTypeName(), "some description", valueRowType, new String[] { "key" });

        CompositeType rowType = new CompositeType("java.util.Map<java.lang.String, java.util.Map<java.lang.String, java.lang.Short>>", "some description",
                new String[] { "key", "value" },
                new String[] { "key", "value" },
                new OpenType<?>[] { SimpleType.STRING, valueType });
        return new TabularType(rowType.getTypeName(), "some description", rowType, new String[] { "key" });
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
