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
package org.jolokia.converter.object;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.management.Descriptor;
import javax.management.JMX;
import javax.management.MBeanFeatureInfo;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanOperationInfo;
import javax.management.openmbean.OpenMBeanParameterInfo;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularType;

import org.jolokia.core.util.ClassUtil;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;

/**
 * <p>Helper class to convert between {@link javax.management.openmbean.OpenType Open types}, JSON representation for
 * Jolokia protocol and {@link javax.management.MBeanInfo} information. Conversion doesn't require particular
 * values and rely only on type information.</p>
 *
 * <p>This class is an implementation of <a href="https://github.com/jolokia/jolokia/issues/966">#966</a> which
 * enhances the information found in Jolokia {@code list} responses.</p>
 */
public class OpenTypeHelper {

    public static final String FIELD_KIND = "kind";
    /** Field for a class of an {@link OpenType} - can always be passed to {@link Class#forName} */
    public static final String FIELD_CLASS = "class";
    /** Field for a type name of an {@link OpenType} - usually generated using {@code com.sun.jmx.mbeanserver.MXBeanIntrospector#typeName()} */
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_DESCRIPTION = "desc";

    public static final String FIELD_ARRAY_PRIMITIVE = "primitive";
    public static final String FIELD_ARRAY_DIMENSION = "dimension";
    public static final String FIELD_ARRAY_ELEMENT_TYPE = "elemType";

    public static final String FIELD_COMPOSITE_ITEMS = "items";

    public static final String FIELD_TABULAR_INDEX = "index";
    public static final String FIELD_TABULAR_ROW_TYPE = "rowType";

    private OpenTypeHelper() {
    }

    /**
     * Normally we can get {@link OpenType} information from 3 sources:<ul>
     *     <li>{@link OpenMBeanAttributeInfo#getOpenType()}</li>
     *     <li>{@link OpenMBeanOperationInfo#getReturnOpenType()}</li>
     *     <li>{@link OpenMBeanParameterInfo#getOpenType()}</li>
     * </ul>
     * But it may be the case when non-open version of the information is used, but still there <em>is</em> some
     * related {@link OpenType} - we can find it (sometimes) in the {@link Descriptor}.
     *
     * @param descriptor
     * @return
     */
    public static OpenType<?> findOpenType(Descriptor descriptor) {
        Object v = descriptor == null ? null : descriptor.getFieldValue(JMX.OPEN_TYPE_FIELD);
        return v instanceof OpenType<?> openType ? openType : null;
    }

    // ---- MBeanXXXInfo to JSON representation to be produced by DataUpdater services at server side

    public static Object toJSON(OpenType<?> type, MBeanFeatureInfo featureInfo) {
        if (type instanceof SimpleType<?> simpleType) {
            return toJSON(simpleType, featureInfo);
        }
        if (type instanceof ArrayType<?> arrayType) {
            return toJSON(arrayType, featureInfo);
        }
        if (type instanceof CompositeType compositeType) {
            return toJSON(compositeType, featureInfo);
        }
        if (type instanceof TabularType tabularType) {
            return toJSON(tabularType, featureInfo);
        }
        throw new IllegalArgumentException("Unsupported OpenType: " + type.getClass().getName());
    }

    public static String toJSON(SimpleType<?> simpleType, MBeanFeatureInfo featureInfo) {
        // this would be  serious space issue, because a lot of MBean attributes/returnTypes/parameters
        // use simple types
//        JSONObject v = basicInformation(simpleType);
//        v.put(FIELD_KIND, Kind.simple.name());

        return simpleType.getTypeName();
    }

    public static JSONObject toJSON(ArrayType<?> arrayType, MBeanFeatureInfo featureInfo) {
        JSONObject v = basicInformation(arrayType);
        v.put(FIELD_KIND, Kind.array.name());
        v.put(FIELD_ARRAY_PRIMITIVE, arrayType.isPrimitiveArray());
        int dimension = arrayType.getDimension();
        v.put(FIELD_ARRAY_DIMENSION, dimension);
        Object elementTypeV = toJSON(arrayType.getElementOpenType(), null);
        String prefix = "[".repeat(dimension);
        if (v.get(FIELD_TYPE).equals(prefix + "L" + CompositeData.class.getName() + ";") && elementTypeV instanceof JSONObject elementType) {
            // it's better to have:
            // [Ljdk.management.jfr.EventTypeInfo;
            // than:
            // [Ljavax.management.openmbean.CompositeData;
            v.put(FIELD_TYPE, prefix + "L" + elementType.get(FIELD_TYPE) + ";");
            if (featureInfo != null && featureInfo.getDescriptor() != null) {
                Object originalTypeValue = featureInfo.getDescriptor().getFieldValue(JMX.ORIGINAL_TYPE_FIELD);
                if (originalTypeValue instanceof String originalType) {
                    // it's even better to have:
                    // java.util.List<jdk.management.jfr.EventTypeInfo>
                    // than:
                    // [Ljdk.management.jfr.EventTypeInfo;
                    v.put(FIELD_TYPE, originalType);
                }
            }
        }
        v.put(FIELD_ARRAY_ELEMENT_TYPE, elementTypeV);

        return v;
    }

    public static JSONObject toJSON(CompositeType/*<CompositeData>*/ compositeType, MBeanFeatureInfo featureInfo) {
        JSONObject v = basicInformation(compositeType);
        v.put(FIELD_KIND, Kind.composite.name());
        JSONObject items = new JSONObject();
        v.put(FIELD_COMPOSITE_ITEMS, items);
        for (String item : compositeType.keySet()) {
            items.put(item, toJSON(compositeType.getType(item), null));
        }

        return v;
    }

    public static JSONObject toJSON(TabularType/*<TabularData>*/ tabularType, MBeanFeatureInfo featureInfo) {
        JSONObject v = basicInformation(tabularType);
        v.put(FIELD_KIND, Kind.tabular.name());
        v.put(FIELD_TABULAR_INDEX, new JSONArray(tabularType.getIndexNames()));
        v.put(FIELD_TABULAR_ROW_TYPE, toJSON(tabularType.getRowType(), null));

        return v;
    }

    private static JSONObject basicInformation(OpenType<?> type) {
        JSONObject v = new JSONObject();
        v.put(FIELD_TYPE, type.getTypeName());
        v.put(FIELD_CLASS, type.getClassName());
        v.put(FIELD_DESCRIPTION, type.getDescription());
        return v;
    }

    // ---- JSON representation to MBeanXXXInfo fetched by JolokiaClient and consumed by client-jmx-adapter
    //      we expect unexpected and fail gently by returning null

    public static OpenType<?> fromJSON(Object openType) throws OpenDataException {
        // We have two options - it's either a String (SimpleType) or JSONObject (Array/Composite/Tabular)
        if (openType instanceof String simpleOpenType) {
            Class<Object> cls = ClassUtil.classForName(simpleOpenType);
            if (cls != null) {
                return ObjectToOpenTypeConverter.knownSimpleOpenType(cls);
            } else {
                return null;
            }
        } else if (openType instanceof JSONObject complexOpenType) {
            // array/composite/tabular types
            Object kindV = complexOpenType.get(FIELD_KIND);
            if (kindV instanceof String kind) {
                Kind k = Kind.from(kind);
                if (k == null || k == Kind.simple) {
                    return null;
                }
                return switch (k) {
                    case array -> fromJSONToArray(complexOpenType);
                    case composite -> fromJSONToComposite(complexOpenType);
                    case tabular -> fromJSONToTabular(complexOpenType);
                    default -> null;
                };
            }
        }

        return null;
    }

    private static OpenType<?> fromJSONToArray(JSONObject complexOpenType) throws OpenDataException {
        Object classV = complexOpenType.get(FIELD_CLASS);
        if (!(classV instanceof String)) {
            return null;
        }
        Object elemTypeV = complexOpenType.get(FIELD_ARRAY_ELEMENT_TYPE);
        Object elemJsonType;
        Object dimensionV = complexOpenType.get(FIELD_ARRAY_DIMENSION);
        Object primitiveV = complexOpenType.get(FIELD_ARRAY_PRIMITIVE);
        if (elemTypeV instanceof String || elemTypeV instanceof JSONObject) {
            elemJsonType = elemTypeV;
        } else {
            return null;
        }

        if (dimensionV instanceof Number dimension && primitiveV instanceof Boolean primitive) {
            OpenType<?> elemType = fromJSON(elemJsonType);
            if (elemType != null) {
                if (primitive) {
                    // the "class" should be something like "[[I" which we can pass directly to Class.forName()
                    Class<?> cls = ClassUtil.classForName((String) classV);
                    return cls == null ? null : ArrayType.getPrimitiveArrayType(cls);
                } else {
                    return new ArrayType<>(dimension.intValue(), elemType);
                }
            }
        }

        return null;
    }

    private static OpenType<?> fromJSONToComposite(JSONObject complexOpenType) throws OpenDataException {
        Object typeV = complexOpenType.get(FIELD_TYPE);
        Object classV = complexOpenType.get(FIELD_CLASS);
        Object descV = complexOpenType.get(FIELD_DESCRIPTION);
        if (!(classV instanceof String && CompositeData.class.getName().equals(classV))) {
            // only this one is expected
            return null;
        }
        if (!(typeV instanceof String)) {
            return null;
        }
        Object itemsV = complexOpenType.get(FIELD_COMPOSITE_ITEMS);
        if (itemsV instanceof JSONObject items) {
            List<String> names = new ArrayList<>(items.size());
            List<String> descriptions = new ArrayList<>(items.size());
            List<OpenType<?>> types = new ArrayList<>(items.size());

            boolean allTypes = true;
            for (Map.Entry<String, Object> e : items.entrySet()) {
                String key = e.getKey();
                Object value = e.getValue();

                OpenType<?> itemType = fromJSON(value);
                if (itemType == null) {
                    allTypes = false;
                    break;
                }
                names.add(key);
                descriptions.add(key);
                types.add(itemType);
            }

            if (allTypes) {
                return new CompositeType((String) typeV, descV instanceof String desc ? desc : "",
                    names.toArray(String[]::new),
                    descriptions.toArray(String[]::new),
                    types.toArray(OpenType<?>[]::new));
            }
        }

        return null;
    }

    private static OpenType<?> fromJSONToTabular(JSONObject complexOpenType) throws OpenDataException {
        Object typeV = complexOpenType.get(FIELD_TYPE);
        Object classV = complexOpenType.get(FIELD_CLASS);
        Object descV = complexOpenType.get(FIELD_DESCRIPTION);
        if (!(classV instanceof String && TabularData.class.getName().equals(classV))) {
            // only this one is expected
            return null;
        }
        if (!(typeV instanceof String)) {
            return null;
        }
        Object indexV = complexOpenType.get(FIELD_TABULAR_INDEX);
        Object rowTypeV = complexOpenType.get(FIELD_TABULAR_ROW_TYPE);
        if (indexV instanceof JSONArray index && rowTypeV instanceof JSONObject rowType) {
            List<String> idx = new ArrayList<>(index.size());

            boolean isFine = true;
            for (Object item : index) {
                if (!(item instanceof String)) {
                    isFine = false;
                    break;
                } else {
                    idx.add((String) item);
                }
            }

            OpenType<?> compositeRowType = fromJSON(rowType);

            if (isFine && compositeRowType instanceof CompositeType rowCompositeType) {
                return new TabularType((String) typeV, descV instanceof String desc ? desc : "", rowCompositeType,
                    idx.toArray(String[]::new));
            }
        }

        return null;
    }

    public enum Kind {
        simple, array, composite, tabular;

        public static Kind from(String kind) {
            if (simple.name().equals(kind)) {
                return simple;
            }
            if (array.name().equals(kind)) {
                return array;
            }
            if (composite.name().equals(kind)) {
                return composite;
            }
            if (tabular.name().equals(kind)) {
                return tabular;
            }
            return null;
        }
    }

}
