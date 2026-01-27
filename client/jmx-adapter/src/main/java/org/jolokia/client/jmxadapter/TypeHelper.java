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
package org.jolokia.client.jmxadapter;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;

import org.jolokia.converter.object.ObjectToObjectConverter;
import org.jolokia.converter.object.ObjectToOpenTypeConverter;
import org.jolokia.core.util.ClassUtil;

/**
 * Helper methods for dealing with types and type conversion for {@link RemoteJmxAdapter}
 */
public class TypeHelper {

    /**
     * Cache of String representation of types (from {@link MBeanAttributeInfo#getType()},
     * {@link MBeanOperationInfo#getReturnType()} and {@link MBeanParameterInfo#getType()}.
     * For {@link javax.management.openmbean.CompositeType} and {@link javax.management.openmbean.TabularType},
     * {@link #CACHE} is used. {@link ArrayType} depends only on dimensions and class/type, so can be cached here.
     */
    private static final Map<String, CachedType> TYPE_CACHE = new ConcurrentHashMap<>();

    /**
     * Cache of the resolved types by arbitrary keys. The reason is that resolution of some {@link OpenType Open types}
     * may depend on particular MBean. This is true for {@link javax.management.openmbean.CompositeType}
     * and {@link javax.management.openmbean.TabularType}
     */
    private static final Map<String, CachedType> CACHE = new ConcurrentHashMap<>();

    private TypeHelper() { }

    /**
     * Produce cache key for {@link MBeanOperationInfo} to speed up processing
     * @param op
     * @return
     */
    public static String operationKey(ObjectName name, MBeanOperationInfo op) {
        String signature = buildSignature(Arrays.stream(op.getSignature()).map(MBeanParameterInfo::getType).toArray(String[]::new));
        return name.getCanonicalName() + "::" + op.getName() + signature;
    }

    /**
     * Produce cache key for {@link MBeanAttributeInfo} to speed up processing
     *
     * @param name
     * @param attr
     * @return
     */
    public static String attributeKey(ObjectName name, MBeanAttributeInfo attr) {
        return name.getCanonicalName() + "::" + attr.getName();
    }

    /**
     * {@link MBeanServerConnection#invoke} accepts a String array of the classes of parameters - we merge
     * them literally to be processed at remote Jolokia Agent side. This method returns a list of parameter
     * class names, separated by commas and surrounded by parentheses.
     *
     * @param signature
     * @return
     */
    public static String buildSignature(String[] signature) {
        StringBuilder builder = new StringBuilder("(");
        if (signature != null) {
            for (int i = 0; i < signature.length; i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(signature[i]);
            }
        }
        builder.append(')');
        return builder.toString();
    }

    /**
     * <p>Caching function that translates the {@code type} into a {@link Class} and potentially {@link OpenType}.</p>
     *
     * <p>Mind that in the context of {@link org.jolokia.client.JolokiaClient}, we don't have (maybe
     * after <a href="https://github.com/jolokia/jolokia/issues/966">#966</a>) a full type information with
     * details about {@link javax.management.openmbean.CompositeType} or
     * {@link javax.management.openmbean.TabularType}. That's why we cache by the key, not by the type.</p>
     *
     * @param key
     * @param type
     * @param foundOpenType - passed if available from Jolokia {@code list} response (since 2.5.0)
     */
    public static CachedType cache(String key, String type, OpenType<?> foundOpenType) {
        if (type == null) {
            throw new IllegalArgumentException("Can't cache null type");
        }

        if (CACHE.containsKey(key)) {
            return CACHE.get(key);
        }
        if (TYPE_CACHE.containsKey(type)) {
            return TYPE_CACHE.get(type);
        }

        if ("void".equals(type) || Void.class.getName().equals(type)) {
            CachedType voidType = new CachedType(Void.class, SimpleType.VOID, type);
            TYPE_CACHE.put(type, voidType);
            return voidType;
        }

        Class<?> cls = ObjectToObjectConverter.knownTypeByName(type);
        if (cls == null) {
            cls = ClassUtil.classForName(type);
        }
        if (cls == null) {
            // mark as checked, but not resolved
            CachedType unknownType = new CachedType(null, null, type);
            TYPE_CACHE.put(type, unknownType);
            return unknownType;
        }

        // is it an OpenType?
        boolean cacheType = cls != CompositeData.class && cls != TabularData.class;

        String nonArrayType = type;
        if (nonArrayType.startsWith("[") && nonArrayType.endsWith(";")) {
            while (nonArrayType.startsWith("[")) {
                nonArrayType = nonArrayType.substring(1);
            }
            // trim initial "L" and trailing ";"
            nonArrayType = nonArrayType.substring(1, nonArrayType.length() - 1);
            Class<?> elClass = ClassUtil.classForName(nonArrayType);
            cacheType &= elClass != CompositeData.class && elClass != TabularData.class;
        }

        OpenType<?> openType = foundOpenType == null ? ObjectToOpenTypeConverter.knownSimpleType(cls) : foundOpenType;
        if (openType == null) {
            if (cls.isArray()) {
                Class<?> cc = cls;
                int dim = 0;
                while (cc.isArray()) {
                    dim++;
                    cc = cc.getComponentType();
                }
                if (cc.isPrimitive()) {
                    openType = ArrayType.getPrimitiveArrayType(cls);
                } else {
                    SimpleType<?> componentOpenType = ObjectToOpenTypeConverter.knownSimpleType(cc);
                    if (componentOpenType != null) {
                        try {
                            openType = new ArrayType<>(dim, componentOpenType);
                        } catch (OpenDataException ignored) {
                        }
                    }
                }
            } else if (cls == CompositeData.class) {
            } else if (cls == TabularData.class) {
            }
        }

        if (cacheType) {
            TYPE_CACHE.put(type, new CachedType(cls, openType, openType == null ? type : openType.getTypeName()));
            return TYPE_CACHE.get(type);
        } else {
            CACHE.put(key, new CachedType(cls, openType, openType == null ? type : openType.getTypeName()));
            return CACHE.get(key);
        }
    }

}
