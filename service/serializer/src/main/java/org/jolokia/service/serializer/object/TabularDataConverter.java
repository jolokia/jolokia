/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jolokia.service.serializer.object;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.KeyAlreadyExistsException;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.jolokia.json.JSONObject;
import org.jolokia.server.core.util.ClassUtil;

/**
 * <p>Converter for {@link TabularType} objects. This converter transforms maps (in particular
 * {@link org.jolokia.json.JSONObject JSON objects} into {@link javax.management.openmbean.TabularData} values
 * according to the type specification defined in a {@link TabularType}.</p>
 *
 * <p>The maps being converted come in 3 flavors:<ul>
 *
 * <li><strong>#1.{@link javax.management.MXBean} compatible {@link Map}/{@link SortedMap}</strong>: requires
 * {@link TabularType#getRowType()} to define two items: {@code key} and {@code value}.
 * {@link TabularType#getIndexNames()} should contain one item: {@code key}. If the type of {@code} item
 * is {@link SimpleType#STRING}, we can convert <em>any</em> map, otherwise we expect maps that can
 * be handled by flavors #2 and #3.</li>
 *
 * <li><strong>#2. non {@code @MXBean} maps with {@code indexNames} and {@code values} top level fields</strong>: this
 * is Jolokia-specific representation.</li>
 *
 * <li><strong>#3. other non {@code @MXBean} maps</strong>: {@link TabularType#getIndexNames()} are nested maps which
 * eventually lead to the values.</li>
 * </ul></p>
 *
 * @author roland
 * @since 28.09.11
 */
class TabularDataConverter extends OpenTypeConverter<TabularType> {

    // Fixed key names for tabular data representation of Maps for MXBeans
    // see: https://docs.oracle.com/en/java/javase/17/docs/api/java.management/javax/management/MXBean.html
    //
    // A Map<K,V> or SortedMap<K,V>, for example Map<String, ObjectName>, has Open Type TabularType
    // and is mapped to a TabularData.
    // The TabularType has two items called `key` and `value`.
    // The Open Type of `key` is opentype(K), and the Open Type of `value` is opentype(V).
    // The index of the TabularType is the single item `key`.
    // NOTE: the `key` item doesn't have to be a javax.management.openmbean.SimpleType.STRING!
    private static final String TD_KEY_KEY = "key";
    private static final String TD_KEY_VALUE = "value";

    private static final String JOLOKIA_KEY_INDEX_NAMES = "indexNames";
    private static final String JOLOKIA_KEY_VALUES = "values";

    private static final String[] MX_BEAN_MAP_ITEMS = new String[]{TD_KEY_KEY, TD_KEY_VALUE};
    private static final String[] MX_BEAN_MAP_INDEX = new String[]{TD_KEY_KEY};

    /**
     * Constructor
     *
     * @param pOpenTypeDeserializer parent converter used for recursive conversion
     */
    public TabularDataConverter(OpenTypeDeserializer pOpenTypeDeserializer) {
        super(pOpenTypeDeserializer);
    }

    @Override
    boolean canConvert(OpenType<?> pType) {
        return pType instanceof TabularType;
    }

    @Override
    public Object convert(TabularType pType, Object pValue) {
        Map<String, Object> givenValues = toMap(pValue);

        // if the TabularType matches MXBean spec with SimpleType "key" item (convertible from String),
        // we can convert ANY map
        if (isMXBeanMapWithSimpleKeys(pType)) {
            // https://docs.oracle.com/en/java/javase/17/docs/api/java.management/javax/management/MXBean.html#type-names
            // Mappings for maps (Map<K,V> etc)
            return createMXBeanTabularData(pType, givenValues);
        }

        // If it is given a a full representation (with "indexNames" and "values"), then parse this accordingly
        // with validation. Each object under "values" will be added as single CompositeData. We don't
        // get automatic uniqueness validation as in #1
        if (checkForFullTabularDataRepresentation(pType, givenValues)) {
            return convertTabularDataFromFullRepresentation(pType, givenValues);
        }

        // It's a plain TabularData non conforming to @MXBean specification, which is tried to be converted
        // from a map of maps. The more elements in the index, the more nested maps we expect.
        TabularDataSupport tabularData = new TabularDataSupport(pType);
        // Recursively go down the map and collect the values
        putRowsToTabularData(pType, givenValues, tabularData, pType.getIndexNames().size());

        return tabularData;
    }

    /**
     * <p>Helper method to create MXBean compatible {@link TabularType} with {@code key} and {@code value} items
     * in its {@link CompositeType} and single {@code key} index. The types of the key and value items may be
     * any of {@link OpenType}.</p>
     *
     * <p>If the type of the key is {@link Comparable}, the {@link TabularType} maps to
     * {@link java.util.SortedMap}. Otherwise it maps to unordered {@link Map}.</p>
     *
     * @see <a href="https://docs.oracle.com/en/java/javase/17/docs/api/java.management/javax/management/MXBean.html#type-names">MXBean Type Names</a>
     *
     * @param keyType
     * @param valueType
     * @return
     */
    public static TabularType createMXBeanTabularType(OpenType<?> keyType, OpenType<?> valueType) throws OpenDataException {
        // for type names
        //  - If T is a non-generic type, this string is the value returned by Class.getName().
        //  - otherwise:
        //     - If T is a non-generic type, this string is the value returned by Class.getName().
        //     - If T is an array E[], genericstring(T) is genericstring(E) followed by "[]".
        //     - Otherwise, T is a parameterized type such as List<String> and genericstring(T) consists of the following:
        //        - the fully-qualified name of the parameterized type as returned by Class.getName()
        //        - a left angle bracket ("<")
        //        - genericstring(A) where A is the first type parameter
        //        - if there is a second type parameter B then "," (a comma and a single space) followed by genericstring(B)
        //        - a right angle bracket (">").
        // Note that if a method returns int[], this will be represented by the string "[I" returned by Class.getName(),
        // but if a method returns List<int[]>, this will be represented by the string "java.util.List<int[]>".

        // OpenType has:
        //  - className
        //     - SimpleType: from Java class
        //     - ArrayType: constructed from names of items
        //     - CompositeType: always "javax.management.openmbean.CompositeData"
        //     - TabularType: always "javax.management.openmbean.TabularData"
        //  - typeName
        //     - TabularType: com.sun.jmx.mbeanserver.MXBeanIntrospector.typeName() or just some other
        //     - TabularType: com.sun.jmx.mbeanserver.MXBeanIntrospector.typeName()
        //  - description - anything

        OpenType<?>[] types = new OpenType<?>[]{keyType, valueType};
        Class<?> cls = keyType.getClass() == SimpleType.class ? ClassUtil.classForName(keyType.getTypeName()) : null;

        StringBuilder sb = new StringBuilder();
        if (cls != null && Comparable.class.isAssignableFrom(cls)) {
            sb.append(SortedMap.class.getName());
        } else {
            sb.append(Map.class.getName());
        }
        sb.append("<");
        sb.append(OpenTypeConverter.getMXBeanTypeName(keyType));
        sb.append(", ");
        sb.append(OpenTypeConverter.getMXBeanTypeName(valueType));
        sb.append(">");
        String typeName = sb.toString();

        // TabularType with rowType=CompositeType with key and value items is a convention for mapping
        // Maps for parameters/return values of MXBeans
        CompositeType rowType = new CompositeType(typeName, typeName, MX_BEAN_MAP_ITEMS, MX_BEAN_MAP_ITEMS, types);

        return new TabularType(typeName, typeName, rowType, MX_BEAN_MAP_INDEX);
    }

    // #1 @MXBean compatible TabularData with [ key, value ] items and [ key ] index

    /**
     * Check if a {@link TabularType} matches the mapping for {@code Map<K, V>} in {@link javax.management.MXBean}
     * specification. On top of {@link javax.management.MXBean} specification, we also check if they keys
     * are of {@link SimpleType#STRING}, because of JSON serialization
     *
     * @param pType
     * @return
     */
    private boolean isMXBeanMapWithSimpleKeys(TabularType pType) {
        // index with one "key" item
        List<String> indexNames = pType.getIndexNames();
        if (!(indexNames.size() == 1 && indexNames.contains(TD_KEY_KEY))) {
            return false;
        }

        CompositeType rowType = pType.getRowType();

        // type of the "key" item should be SimpleType and we'll expect keys to be Strings convertible to
        // this SimpleType
        if (!(rowType.getType(TD_KEY_KEY) instanceof SimpleType)) {
            return false;
        }

        // only "key" and "value" items allowed
        if (rowType.keySet().size() == 2) {
            return rowType.keySet().contains(TD_KEY_KEY) && rowType.keySet().contains(TD_KEY_VALUE);
        }

        return false;
    }

    /**
     * Create {@link javax.management.MXBean}-compatible {@link TabularData} from any {@link Map}. Each key-value from
     * the map is converted into a single {@link CompositeData} {@link TabularData#put(CompositeData) added as row}.
     * Naturally this ensures uniqueness of the {@code key} item among all rows.
     *
     * @param pType
     * @param pValue
     * @return
     */
    private TabularData createMXBeanTabularData(TabularType pType, Map<String, Object> pValue) {
        CompositeType rowType = pType.getRowType();
        TabularDataSupport tabularData = new TabularDataSupport(pType);

        for (Map.Entry<String, Object> entry : pValue.entrySet()) {
            // HashMap/TreeMap distinguishment is used when converting TabularData back to a Map
            Map<String, Object> map = new HashMap<>();
            // key can be any SimpleType and we'll convert it to OpenType of the "key" item
            map.put(TD_KEY_KEY, openTypeDeserializer.convert(rowType.getType("key"), entry.getKey()));
            map.put(TD_KEY_VALUE, openTypeDeserializer.convert(rowType.getType("value"), entry.getValue()));

            try {
                CompositeData compositeData = new CompositeDataSupport(rowType, map);
                tabularData.put(compositeData);
            } catch (KeyAlreadyExistsException | OpenDataException e) {
                // keys should be unique and values need to be OpenType
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }

        return tabularData;
    }

    // #2 non-@MXBean maps with "indexNames" and "values" top level fields

    /**
     * Is the map being converted a Jolokia representation with {@code indexNames} and {@code values} fields?
     *
     * @param pType
     * @param pValue
     * @return
     */
    private boolean checkForFullTabularDataRepresentation(TabularType pType, Map<String, Object> pValue) {
        if (pValue.containsKey(JOLOKIA_KEY_INDEX_NAMES) && pValue.containsKey(JOLOKIA_KEY_VALUES) && pValue.size() == 2) {
            Object indexNamesValue = pValue.get(JOLOKIA_KEY_INDEX_NAMES);
            if (!(indexNamesValue instanceof Collection)) {
                throw new IllegalArgumentException("\"indexNames\" field for TabularData must be a Collection<String>, not "
                    + indexNamesValue.getClass().getName());
            }
            Object values = pValue.get(JOLOKIA_KEY_VALUES);
            if (!(values instanceof Collection)) {
                throw new IllegalArgumentException("\"values\" field for TabularData must be a Collection<?>, not "
                    + values.getClass().getName());
            }

            Collection<?> indexNames = (Collection<?>) indexNamesValue;
            List<String> tabularIndexNames = pType.getIndexNames();
            if (indexNames.size() != tabularIndexNames.size()) {
                throw new IllegalArgumentException("Invalid definition of \"indexNames\" - expected " + tabularIndexNames.size() + " entries, " +
                                                   "found " + indexNames.size() + " entries");
            }

            for (Object index : indexNames) {
                if (!(index instanceof String)) {
                    throw new IllegalArgumentException("Invalid type of index element - expected String, found "
                        + index.getClass().getName());
                }
                if (!tabularIndexNames.contains((String) index)) {
                    throw new IllegalArgumentException("No index element named \"" + index + "\" defined in TabularType " + pType.getTypeName());
                }
            }

            // TOCHECK: should we validate each child of "values" array?

            return true;
        }

        return false;
    }

    /**
     * Create non-{@link javax.management.MXBean} compatible {@link TabularData} from a {@link Map} where
     * the "rows" are contained in nested maps which represent {@link TabularType#getIndexNames()}.
     *
     * @param pType
     * @param pValue
     * @return
     */
    private TabularData convertTabularDataFromFullRepresentation(TabularType pType, Map<String, Object> pValue) {
        // already checked earlier
        Collection<?> jsonVal = (Collection<?>) pValue.get("values");

        TabularDataSupport tabularData = new TabularDataSupport(pType);
        for (Object val : jsonVal) {
            if (!(val instanceof Map)) {
                throw new IllegalArgumentException("TabularData values must be given as Maps or JSONObjects, not "
                    + val.getClass().getName());
            }
            tabularData.put((CompositeData) openTypeDeserializer.convert(pType.getRowType(), val));
        }

        return tabularData;
    }

    /**
     * <p>Create non-{@link javax.management.MXBean} compatible {@link TabularData} from a {@link Map} where
     * {@link TabularType#getIndexNames()}} are represented as nested maps in the converted value.</p>
     *
     * <p>While the values for index elements could be re-created from the nested map keys, the <em>final</em>
     * value should contain all the keys and values anyway.</p>
     *
     * @param pType
     * @param pValue
     * @param pTabularData
     * @param pLevel
     */
    private void putRowsToTabularData(TabularType pType, Map<String, Object> pValue, TabularDataSupport pTabularData, int pLevel) {
        for (Object value : pValue.values()) {
            if (!(value instanceof JSONObject)) {
                throw new IllegalArgumentException(
                        "Cannot convert " + trim(pValue.toString()) + " to " + pType
                            + " because the object provided (" + value.getClass().getName()
                            + ") is not of the expected type JSONObject at level " + pLevel);
            }

            Map<String, Object> jsonValue = (JSONObject) value;
            if (pLevel > 1) {
                putRowsToTabularData(pType, jsonValue, pTabularData, pLevel - 1);
            } else {
                // convert the "final" map into a CompositeDate
                pTabularData.put((CompositeData) openTypeDeserializer.convert(pType.getRowType(), jsonValue));
            }
        }
    }

}
