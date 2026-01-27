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

import java.beans.Transient;
import java.util.Map;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

public class CustomDynamic implements CustomDynamicMBean {

    @Override
    public TabularData getCustomTabularData() {
        try {
            TabularDataSupport res = new TabularDataSupport(type());
            CompositeData cd1 = new CompositeDataSupport(type().getRowType(), Map.of(
                "id1", (byte)0x42,
                "id2", 42424242L,
                "name", "Jolokia",
                "address", "https://jolokia.org"
            ));
            res.put(cd1);
            CompositeData cd2 = new CompositeDataSupport(type().getRowType(), Map.of(
                "id1", (byte)0x43,
                "id2", 42424242L,
                "name", "Jolokia",
                "address", "https://jolokia.org"
            ));
            res.put(cd2);

            return res;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Transient
    public static TabularType type() throws OpenDataException {
        CompositeType cType = new CompositeType("My Composite Type", "Row type for My Tabular Type",
            new String[] { "id1", "id2", "name", "address" },
            new String[] { "id1 (desc)", "id2 (desc)", "name (desc)", "address (desc)" },
            new OpenType[] { SimpleType.BYTE, SimpleType.LONG, SimpleType.STRING, SimpleType.STRING }
        );
        return new TabularType("My Tabular Type", "I just want to check jolokia-client-jmx-adapter", cType,
            new String[] { "id1", "id2" });
    }

}
