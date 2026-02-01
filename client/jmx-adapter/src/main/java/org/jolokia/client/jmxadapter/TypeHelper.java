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

import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.management.JMRuntimeException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularType;

import org.jolokia.converter.object.Converter;
import org.jolokia.converter.object.ObjectToObjectConverter;
import org.jolokia.converter.object.ObjectToOpenTypeConverter;
import org.jolokia.converter.object.OpenTypeHelper;
import org.jolokia.core.util.ClassUtil;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;

import static javax.management.openmbean.SimpleType.*;

/**
 * Helper methods for dealing with types and type conversion for {@link RemoteJmxAdapter}. Helps with known
 * types, but also when type information has to be discovered from actual data (which is usually the case
 * with {@link CompositeData} and {@link TabularData}.
 */
public class TypeHelper {

    /**
     * Cache for String representation of types (from {@link MBeanAttributeInfo#getType()},
     * {@link MBeanOperationInfo#getReturnType()} and {@link MBeanParameterInfo#getType()}) which are unique
     * and don't have <em>internal structure</em>.
     * For {@link javax.management.openmbean.CompositeType} and {@link javax.management.openmbean.TabularType},
     * {@link #CACHE} is used. {@link ArrayType} depends only on dimensions and class/type, so can be cached here.
     */
    private static final Map<String, CachedType> TYPE_CACHE = new ConcurrentHashMap<>();

    /**
     * Cache of the resolved types by arbitrary keys. The reason is that resolution of some {@link OpenType Open types}
     * may depend on particular MBean. This is true for {@link javax.management.openmbean.CompositeType}
     * and {@link javax.management.openmbean.TabularType}.
     */
    static final Map<String, CachedType> CACHE = new ConcurrentHashMap<>();

    /**
     * Generally we should never assume the shape/type of a value from remote Jolokia Agent by
     * reflectively checking types from current JVM, but there are some really <em>known</em> classes we
     * could check.
     */
    private static final Map<String, CompositeTypeItems> KNOWN_COMPOSITE_TYPES_BY_STRUCTURE = new HashMap<>();
    private static final Map<String, OpenType<?>[]> KNOWN_COMPOSITE_TYPES = new HashMap<>();

    public static Converter<String> converter;

    static {
        KNOWN_COMPOSITE_TYPES_BY_STRUCTURE.put("com.sun.management.GcInfo", new CompositeTypeItems(new LinkedHashSet<>(List.of("duration", "endTime", "id", "memoryUsageAfterGc", "memoryUsageBeforeGc", "startTime")), null));
        KNOWN_COMPOSITE_TYPES_BY_STRUCTURE.put("com.sun.management.VMOption", new CompositeTypeItems(new LinkedHashSet<>(List.of("name", "origin", "value", "writeable")), null));
        KNOWN_COMPOSITE_TYPES_BY_STRUCTURE.put("java.lang.management.LockInfo", new CompositeTypeItems(new LinkedHashSet<>(List.of("className", "identityHashCode")), null));
        KNOWN_COMPOSITE_TYPES_BY_STRUCTURE.put("java.lang.management.MemoryUsage", new CompositeTypeItems(new LinkedHashSet<>(List.of("committed", "init", "max", "usage")), null));
        KNOWN_COMPOSITE_TYPES_BY_STRUCTURE.put("java.lang.management.MonitorInfo", new CompositeTypeItems(new LinkedHashSet<>(List.of("className", "identityHashCode", "lockedStackDepth", "lockedStackFrame")), null));
        // "daemon" and "priority" are not available in JDK 1.8
        KNOWN_COMPOSITE_TYPES_BY_STRUCTURE.put("java.lang.management.ThreadInfo", new CompositeTypeItems(
                new LinkedHashSet<>(List.of("blockedCount", "blockedTime", "inNative", "lockInfo", "lockName", "lockOwnerId", "lockOwnerName",
                        "lockedMonitors", "lockedSynchronizers", "stackTrace", "suspended", "threadId", "threadName", "threadState", "waitedCount", "waitedTime")),
                new LinkedHashSet<>(List.of("blockedCount", "blockedTime", "daemon", "inNative", "lockInfo", "lockName", "lockOwnerId", "lockOwnerName",
                        "lockedMonitors", "lockedSynchronizers", "priority", "stackTrace", "suspended", "threadId", "threadName", "threadState", "waitedCount", "waitedTime"))
        ));
        // see sun.management.StackTraceElementCompositeData. Let's use more than only sun.management.StackTraceElementCompositeData.V5_ATTRIBUTES
        KNOWN_COMPOSITE_TYPES_BY_STRUCTURE.put("java.lang.StackTraceElement", new CompositeTypeItems(
                new LinkedHashSet<>(List.of("className", "fileName", "lineNumber", "methodName", "nativeMethod")),
                new LinkedHashSet<>(List.of("classLoaderName", "className", "fileName", "lineNumber", "methodName", "moduleName", "moduleVersion", "nativeMethod"))
        ));
        KNOWN_COMPOSITE_TYPES_BY_STRUCTURE.put("jdk.management.jfr.ConfigurationInfo", new CompositeTypeItems(new LinkedHashSet<>(List.of("contents", "description", "label", "name", "provider", "settings")), null));
        KNOWN_COMPOSITE_TYPES_BY_STRUCTURE.put("jdk.management.jfr.EventTypeInfo", new CompositeTypeItems(new LinkedHashSet<>(List.of("categoryNames", "description", "id", "label", "name", "settingDescriptors")), null));
        // "toDisk" was "disk" in JDK 1.8
        KNOWN_COMPOSITE_TYPES_BY_STRUCTURE.put("jdk.management.jfr.RecordingInfo", new CompositeTypeItems(
                new LinkedHashSet<>(List.of("destination", "dumpOnExit", "duration", "id", "maxAge", "maxSize", "name", "settings", "size", "startTime", "state", "stopTime")),
                new LinkedHashSet<>(List.of("destination", "dumpOnExit", "duration", "id", "maxAge", "maxSize", "name", "settings", "size", "startTime", "state", "stopTime", "toDisk"))
        ));
        KNOWN_COMPOSITE_TYPES_BY_STRUCTURE.put("jdk.management.jfr.SettingDescriptorInfo", new CompositeTypeItems(new LinkedHashSet<>(List.of("contentType", "defaultValue", "description", "label", "name", "typeName")), null));

        try {
            CompositeType mapStringString = new CompositeType("java.util.Map<java.lang.String, java.lang.String>", "java.util.Map<java.lang.String, java.lang.String>",
                    new String[] { "key", "value" },
                    new String[] { "key", "value" },
                    new OpenType<?>[] { STRING, STRING }
            );
            TabularType settings = new TabularType("java.util.Map<java.lang.String, java.lang.String>", "java.util.Map<java.lang.String, java.lang.String>",
                    mapStringString, new String[] { "key" });
            CompositeType memoryUsage = new CompositeType("java.lang.management.MemoryUsage", "java.lang.management.MemoryUsage",
                    new String[] { "committed", "init", "max", "used" },
                    new String[] { "committed", "init", "max", "used" },
                    new OpenType<?>[] { LONG, LONG, LONG, LONG }
            );
            CompositeType mapStringMemoryUsage = new CompositeType("java.util.Map<java.lang.String, java.lang.management.MemoryUsage>", "java.util.Map<java.lang.String, java.lang.management.MemoryUsage>",
                    new String[] { "key", "value" },
                    new String[] { "key", "value" },
                    new OpenType<?>[] { STRING, memoryUsage }
            );
            TabularType memoryUsageMap = new TabularType("java.util.Map<java.lang.String, java.lang.management.MemoryUsage>", "java.util.Map<java.lang.String, java.lang.management.MemoryUsage>",
                    mapStringMemoryUsage, new String[] { "key" });
            CompositeType settingDescriptor = new CompositeType("jdk.management.jfr.SettingDescriptorInfo", "jdk.management.jfr.SettingDescriptorInfo",
                    new String[] { "contentType", "defaultValue", "description", "label", "name", "typeName" },
                    new String[] { "contentType", "defaultValue", "description", "label", "name", "typeName" },
                    new OpenType<?>[] { STRING, STRING, STRING, STRING, STRING, STRING }
            );
            CompositeType stackTraceElement = new CompositeType("java.lang.StackTraceElement", "java.lang.StackTraceElement",
                    new String[] { "classLoaderName", "className", "fileName", "lineNumber", "methodName", "moduleName", "moduleVersion", "nativeMethod" },
                    new String[] { "classLoaderName", "className", "fileName", "lineNumber", "methodName", "moduleName", "moduleVersion", "nativeMethod" },
                    new OpenType<?>[] { STRING, STRING, STRING, INTEGER, STRING, STRING, STRING, BOOLEAN }
            );
            CompositeType lockInfo = new CompositeType("java.lang.management.LockInfo", "java.lang.management.LockInfo",
                    new String[] { "className", "identityHashCode" },
                    new String[] { "className", "identityHashCode" },
                    new OpenType<?>[] { STRING, INTEGER }
            );
            CompositeType monitorInfo = new CompositeType("java.lang.management.MonitorInfo", "java.lang.management.MonitorInfo",
                    new String[] { "className", "identityHashCode", "lockedStackDepth", "lockedStackFrame" },
                    new String[] { "className", "identityHashCode", "lockedStackDepth", "lockedStackFrame" },
                    new OpenType<?>[] { STRING, INTEGER, INTEGER, stackTraceElement }
            );

            KNOWN_COMPOSITE_TYPES.put("com.sun.management.GcInfo", new OpenType<?>[] { LONG, LONG, LONG, memoryUsageMap, memoryUsageMap, LONG });
            KNOWN_COMPOSITE_TYPES.put("com.sun.management.VMOption", new OpenType<?>[] { STRING, STRING, STRING, BOOLEAN });
            KNOWN_COMPOSITE_TYPES.put("java.lang.management.LockInfo", new OpenType<?>[] { STRING, INTEGER });
            KNOWN_COMPOSITE_TYPES.put("java.lang.management.MemoryUsage", new OpenType<?>[] { LONG, LONG, LONG, LONG });
            KNOWN_COMPOSITE_TYPES.put("java.lang.management.MonitorInfo", new OpenType<?>[] { STRING, INTEGER, INTEGER, stackTraceElement });
            KNOWN_COMPOSITE_TYPES.put("java.lang.management.ThreadInfo", new OpenType<?>[] { LONG, LONG, BOOLEAN, BOOLEAN, lockInfo, STRING, LONG, STRING, new ArrayType<>(1, monitorInfo), new ArrayType<>(1, lockInfo), INTEGER, new ArrayType<>(1, stackTraceElement), BOOLEAN, LONG, STRING, STRING, LONG, LONG });
            KNOWN_COMPOSITE_TYPES.put("java.lang.StackTraceElement", new OpenType<?>[] { STRING, STRING, STRING, INTEGER, STRING, STRING, STRING, BOOLEAN });
            KNOWN_COMPOSITE_TYPES.put("jdk.management.jfr.ConfigurationInfo", new OpenType<?>[] { STRING, STRING, STRING, STRING, STRING, settings });
            KNOWN_COMPOSITE_TYPES.put("jdk.management.jfr.EventTypeInfo", new OpenType<?>[] { new ArrayType<>(1, STRING), STRING, LONG, STRING, STRING, new ArrayType<>(1, settingDescriptor) });
            KNOWN_COMPOSITE_TYPES.put("jdk.management.jfr.RecordingInfo", new OpenType<?>[] { STRING, BOOLEAN, LONG, LONG, LONG, LONG, STRING, settings, LONG, LONG, STRING, LONG, BOOLEAN });
            KNOWN_COMPOSITE_TYPES.put("jdk.management.jfr.SettingDescriptorInfo", new OpenType<?>[] { STRING, STRING, STRING, STRING, STRING, STRING });
        } catch (OpenDataException ignored) {
        }
    }

    private record CompositeTypeItems(Set<String> toCheck, Set<String> toUse) {
    }

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
     * <p>After implementing <a href="https://github.com/jolokia/jolokia/issues/966">#966</a>) we may have
     * a full type information with details about {@link javax.management.openmbean.CompositeType} or
     * {@link javax.management.openmbean.TabularType}. But when connecting to older Jolokia Agents, this information
     * may be missing.</p>
     *
     * <p>This method operates on two different caches:<ul>
     *     <li>Cache by {@code type} - when there's simple, unambiguous mapping</li>
     *     <li>Cache by {@code key} - when the type information depends on an MBean/attribute/operation
     *     being used - this is true for {@link CompositeData} and {@link TabularData}, where the class of the data
     *     is not sufficient to get full type information.</li>
     * </ul></p>
     *
     * @param key
     * @param type
     * @param foundOpenType - passed if available from Jolokia {@code list} response (since 2.5.0)
     */
    public static CachedType cache(String key, String type, OpenType<?> foundOpenType) throws ReflectionException {
        if (type == null) {
            throw new IllegalArgumentException("Can't cache null type");
        }

        if (CACHE.containsKey(key)) {
            CachedType cached = CACHE.get(key);
            if (cached.openType() == null && foundOpenType != null) {
                // let's replace the worse entry with a better one
                CACHE.remove(key);
            } else {
                return cached;
            }
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

        // if we deal with arrays, we need to know the actual element type - even for multi-dimensional arrays
        ArrayDiscovery check = findArrayType(cls);
        Class<?> typeToCheck = check == null ? cls : check.componentType;

        // is it a data which can be cached without ambiguities?
        boolean cacheType = typeToCheck != CompositeData.class && typeToCheck != TabularData.class;

        OpenType<?> openType = foundOpenType == null ? ObjectToOpenTypeConverter.knownSimpleOpenType(cls) : foundOpenType;
        if (openType == null) {
            // some discovery, but we can't do much about CompositeData and TabularData without known OpenType
            // we can only handle arrays
            if (cls.isArray()) {
                if (check != null && check.arrayType != null) {
                    openType = check.arrayType;
                }
            }
        }

        if (cacheType) {
            if (openType instanceof SimpleType) {
                // special, so we can distinguish "short" and "java.lang.Short"
                TYPE_CACHE.put(type, new CachedType(cls, openType, type));
            } else {
                TYPE_CACHE.put(type, new CachedType(cls, openType, openTypeName(openType, type)));
            }
            return TYPE_CACHE.get(type);
        } else {
            CACHE.put(key, new CachedType(cls, openType, openTypeName(openType, type)));
            return CACHE.get(key);
        }
    }

    /**
     * <p>This methods tries to create an {@link OpenType} based on actual JSON data returned by Jolokia Client.</p>
     *
     * <p>This function was previously used in {@code org.jolokia.client.jmxadapter.ToOpenTypeConverter}, but it
     * relied on type information (using reflection) obtained from types from <em>current</em> JVM instead
     * of from the <em>target</em> JVM. In practice types like {@link MemoryUsage} or {@link java.lang.management.ThreadInfo}
     * do not change often but we still aim for a generic solution.
     * Of course we need to be pragmatic, so there is some optimization involved.</p>
     *
     * <p>This method should be called for {@link CompositeData} or
     * {@link TabularData} (or arrays of these), because all other types should be handled by {@link #cache(String, String, OpenType)}</p>
     *
     * @param typeName from {@link MBeanAttributeInfo#getType()}, {@link MBeanOperationInfo#getReturnType()} or
     *                 {@link MBeanParameterInfo#getType()}.
     * @param rawValue actual object of one of possible JSON types (String, Number, Boolean, Array, Object)
     * @return
     */
    public static OpenType<?> buildOpenType(String typeName, Object rawValue) throws ReflectionException {
        Class<?> cls = ObjectToObjectConverter.knownTypeByName(typeName);
        if (cls == null) {
            cls = ClassUtil.classForName(typeName);
        }
        if (cls == null) {
            return null;
        }

        // return recursively built type
        return buildOpenType(cls, rawValue, 0, new HashSet<>());
    }

    /**
     * Recursively build an {@link OpenType} based on a hint and actual value. The hint at top level should be
     * Composite/TabularType or any dimensional array of these. Recursively we may pass further hints based on
     * the shape/pattern of the nested values.
     *
     * @param hint only at top level we have a definite hint about the target type. But recursively we may get new hints
     * @param value
     * @param depth
     * @param visited
     * @return
     */
    private static OpenType<?> buildOpenType(Class<?> hint, Object value, int depth, Set<Integer> visited) throws ReflectionException {
        if ((value instanceof JSONObject || value instanceof JSONArray) && !visited.add(System.identityHashCode(value))) {
            OpenDataException cause = new OpenDataException("Can't determine OpenType from recursive data structure");
            throw new ReflectionException(cause, cause.getMessage());
        }

        // is it an empty value?
        if (hint == null && (value == null || (value instanceof JSONObject obj && obj.isEmpty()) || (value instanceof JSONArray arr && arr.isEmpty()))) {
            // well... we can cache later when better data arrives
            return null;
        }

        // We have only four kinds of OpenType

        // 1. SimpleType can be deduced only from the value's class

        if (value != null) {
            SimpleType<?> simpleType = ObjectToOpenTypeConverter.knownSimpleOpenType(value.getClass());
            if (simpleType != null) {
                // we don't need the value actually
                return simpleType;
            }
        }

        // 2. ArrayType

        // now let's unwrap possible array and get to the actual element
        ArrayDiscovery check = findArrayType(hint);
        if (check == null && value instanceof JSONArray jsonArray && !jsonArray.isEmpty()) {
            Class<?> elementClass = jsonArray.get(0).getClass();
            check = findArrayType(ClassUtil.classForName("[L" + elementClass.getName() + ";"));
        }
        int dimension = check != null ? check.dimensions : 0;
        hint = check != null ? check.componentType : hint;

        if (check != null && check.arrayType != null) {
            return check.arrayType;
        }

        // Now we first have to check the data itself

        if (value instanceof JSONArray json) {
            if (dimension == 0) {
                // we have an array value, but no array class hint, so we can't do much
                return null;
            }

            // we need to dig into each dimension and get some data to investigate
            int level = 0;
            JSONArray currentArray = json;
            Object elementToCheck = null;
            while (!currentArray.isEmpty() && currentArray.get(0) != null) {
                elementToCheck = currentArray.get(0);
                level++;
                if (level == dimension) {
                    break;
                } else {
                    if (!(elementToCheck instanceof JSONArray)) {
                        // we run out of nested arrays
                        return null;
                    } else {
                        currentArray = (JSONArray) elementToCheck;
                    }
                }
            }
            if (level < dimension) {
                // we run out of nested arrays
                return null;
            }

            // time for recursion
            OpenType<?> openType = buildOpenType(hint, elementToCheck, level + 1, visited);
            if (openType == null) {
                return null;
            }
            try {
                return new ArrayType<>(dimension, openType);
            } catch (OpenDataException e) {
                throw new ReflectionException(e, e.getMessage());
            }
        }

        // either we're recursively called with nested array element or we're asked to build a type
        // for Composite/Tabular data

        if (!(value instanceof JSONObject json) || json.isEmpty()) {
            // nothing more to investigate
            return null;
        }

        // special from org.jolokia.converter.json.simplifier.ObjectNameSimplifier
        if (json.size() == 1 && json.containsKey("objectName")) {
            if (dimension == 0) {
                return SimpleType.OBJECTNAME;
            } else {
                try {
                    return new ArrayType<>(dimension, SimpleType.OBJECTNAME);
                } catch (OpenDataException e) {
                    throw new ReflectionException(e, e.getMessage());
                }
            }
        }

        // 3. TabularType first (because CompositeType has arbitrary structure)
        //    See org.jolokia.converter.object.TabularDataConverter

        TabularType tabularType;

        // 3.a. Easiest to deserialize variant #2
        tabularType = findJolokiaEncodedTabularType(hint, depth, visited, json);

        // 3.b. Still detectable variant #3 where the index uses multiple SimpleType items and Jolokia
        //      serialized it as nested JSONObjects
        if (tabularType == null) {
            tabularType = findJolokiaNestedTabularType(hint, depth, visited, json);
        }

        // 3.c. Hardest to detect variant #1, so we rely only on the hint
        //      this may be an MXBean Map or a non-MXBean TabularData with single-element index of SimpleType
        //      so we fallback to key/value pattern
        //      each key/value pair of this JSONObject could originally be a separate row in the TabularData
        //      we'll attempt to recursively build TabularType passing down a TabularData.class hint
        //      (which will be ignored if the value is of simple or array type)
        if (tabularType == null && hint == TabularData.class) {
            try {
                Object v = null;
                for (Object o : json.values()) {
                    if (o != null) {
                        v = o;
                        break;
                    }
                }
                if (v == null) {
                    return null;
                }

                SimpleType<?> simpleRowType = v instanceof JSONObject || v instanceof JSONArray
                        ? null : ObjectToOpenTypeConverter.knownSimpleOpenType(v.getClass());
                CompositeType finalRowType = null;
                String typeName = null;
                if (simpleRowType == null) {
                    OpenType<?> rowType = buildOpenType(TabularData.class, v, depth + 1, visited);
                    if (rowType != null) {
                        // map of maps or map of arrays
                        typeName = String.format("java.util.Map<java.lang.String, %s>", rowType.getTypeName());
                        finalRowType = new CompositeType(typeName, typeName,
                                new String [] { "key", "value" },
                                new String [] { "key", "value" },
                                new OpenType<?>[] { SimpleType.STRING, rowType }
                        );
                    }
                } else {
                    // map of simple types
                    typeName = String.format("java.util.Map<java.lang.String, %s>", simpleRowType.getTypeName());
                    finalRowType = new CompositeType(typeName, typeName,
                            new String [] { "key", "value" },
                            new String [] { "key", "value" },
                            new OpenType<?>[] { SimpleType.STRING, simpleRowType }
                    );
                }
                if (finalRowType != null) {
                    tabularType = new TabularType(typeName, typeName, finalRowType, new String[] { "key" });
                }
            } catch (OpenDataException e) {
                throw new JMRuntimeException(e.getMessage());
            }
        }

        if (tabularType != null) {
            if (dimension == 0) {
                return tabularType;
            } else {
                try {
                    return new ArrayType<>(dimension, tabularType);
                } catch (OpenDataException e) {
                    throw new ReflectionException(e, e.getMessage());
                }
            }
        }

        // 4. CompositeType
        //    A CompositeData object associates *string* keys with the values of each data item.
        //    The methods of the class then search for and return data items based on their *string key*.

        if (hint != TabularData.class) {
            try {
                // is it a known composite type?
                for (Map.Entry<String, CompositeTypeItems> entry : KNOWN_COMPOSITE_TYPES_BY_STRUCTURE.entrySet()) {
                    if (json.keySet().containsAll(entry.getValue().toCheck())) {
                        Set<String> items = entry.getValue().toUse();
                        if (items == null) {
                            items = entry.getValue().toCheck();
                        }
                        return new CompositeType(entry.getKey(), entry.getKey(), items.toArray(String[]::new),
                                items.toArray(String[]::new), KNOWN_COMPOSITE_TYPES.get(entry.getKey()));
                    }
                }

                // see: org.jolokia.converter.json.CompositeDataAccessor.compositeDataToJSON

                String[] keys = new String[json.size()];
                OpenType<?>[] types = new OpenType<?>[json.size()];
                int idx = 0;
                for (Map.Entry<String, Object> item : json.entrySet()) {
                    keys[idx] = item.getKey();
                    types[idx++] = buildOpenType(null, item.getValue(), depth + 1, visited);
                }

                CompositeType type = new CompositeType(JSONObject.class.getName(), JSONObject.class.getName(), keys, keys, types);
                if (dimension == 0) {
                    return type;
                } else {
                    try {
                        return new ArrayType<>(dimension, type);
                    } catch (OpenDataException e) {
                        throw new ReflectionException(e, e.getMessage());
                    }
                }
            } catch (OpenDataException e) {
                throw new ReflectionException(e, e.getMessage());
            }
        }

        return null;
    }

    /**
     * Try to create {@link TabularType} encoded by Jolokia using {@code indexNames} and {@code values} arrays
     * @param hint
     * @param depth
     * @param visited
     * @param json
     * @return
     * @throws ReflectionException
     */
    private static TabularType findJolokiaEncodedTabularType(Class<?> hint, int depth, Set<Integer> visited, JSONObject json)
            throws ReflectionException {
        TabularType tabularType = null;

        if (hint != CompositeData.class && json.size() == 2
                && json.containsKey("indexNames") && json.get("indexNames") instanceof JSONArray index
                && json.containsKey("values") && json.get("values") instanceof JSONArray values) {
            if (values.isEmpty() || index.isEmpty()) {
                return null;
            }
            String[] idx = new String[index.size()];
            int i = 0;
            for (Object v : index) {
                if (v instanceof String s) {
                    idx[i++] = s;
                } else {
                    // won't check further
                    return null;
                }
            }
            Object row = values.iterator().next();
            OpenType<?> rowType = buildOpenType(CompositeData.class, row, depth + 1, visited);
            if (!(rowType instanceof CompositeType ct)) {
                return null;
            }
            try {
                tabularType = new TabularType(JSONObject.class.getName(), JSONObject.class.getName(), ct, idx);
            } catch (OpenDataException e) {
                throw new JMRuntimeException(e.getMessage());
            }
        }

        return tabularType;
    }

    private static TabularType findJolokiaNestedTabularType(Class<?> hint, int depth, Set<Integer> visited, JSONObject json)
            throws ReflectionException {
        TabularType tabularType = null;

        Map<String, Integer> keyValues = new LinkedHashMap<>();
        int count = 0;
        JSONObject checking = json;
        while (!checking.isEmpty()) {
            // each level until the last one may contain multiple entries, but we'll be checking
            // only left-most instead of entire DFS
            String k = checking.keySet().iterator().next();
            keyValues.putIfAbsent(k, 0);
            keyValues.put(k, keyValues.get(k) + 1);
            count++;
            if (!(checking.get(k) instanceof JSONObject nested)) {
                break;
            }
            checking = nested;
            // are we at the bottom?
            final int[] matches = new int[] { 0 };
            keyValues.forEach((key, c) -> {
                nested.values().forEach(v -> {
                    SimpleType<?> valueType = ObjectToOpenTypeConverter.knownSimpleOpenType(v.getClass());
                    if (valueType != null) {
                        String stringValue = (String) converter.convert(String.class.getName(), v);
                        if (stringValue.equals(key)) {
                            matches[0]++;
                        }
                    }
                });
            });
            if (matches[0] == count) {
                break;
            }
        }
        if (checking.size() >= count && converter != null) {
            // the current map should contain all the values, including the values of parent keys
            // up to the top
            List<String> test = new ArrayList<>();
            keyValues.forEach((k, c) -> {
                for (int i = 0; i < c; i++) {
                    test.add(k);
                }
            });
            List<String> reconstructedIndex = new ArrayList<>(test.size());
            // we need to preserve the order
            for (String k: new ArrayList<>(test)) {
                for (Map.Entry<String, Object> e : checking.entrySet()) {
                    if (e.getValue() == null) {
                        continue;
                    }
                    SimpleType<?> valueType = ObjectToOpenTypeConverter.knownSimpleOpenType(e.getValue().getClass());
                    if (valueType != null) {
                        String stringValue = (String) converter.convert(String.class.getName(), e.getValue());
                        if (k.equals(stringValue)) {
                            if (test.remove(stringValue)) {
                                // unfortunately there's no way to reconstruct the index in proper order if the value
                                // we check contains the same values, for example:
                                // "id2": {
                                //   "id2": {
                                //     "2": {
                                //       "key1": "id2",
                                //       "key2": "id2",
                                //       "value2": "some value",
                                //       "value1": 42,
                                //       "key3": 2
                                //     }
                                //   }
                                // },
                                reconstructedIndex.add(e.getKey());
                            }
                        }
                    }
                }
            }

            if (test.isEmpty()) {
                OpenType<?> rowType = buildOpenType(CompositeData.class, checking, depth + 1, visited);
                if (!(rowType instanceof CompositeType ct)) {
                    return null;
                }
                try {
                    tabularType = new TabularType(JSONObject.class.getName(), JSONObject.class.getName(), ct,
                            reconstructedIndex.toArray(String[]::new));
                } catch (OpenDataException e) {
                    throw new JMRuntimeException(e.getMessage());
                }
            }
        }

        return tabularType;
    }

    /**
     * Prepare nice name for the {@link OpenType} which makes array type names better.
     * @param openType
     * @param defaultName
     * @return
     */
    private static String openTypeName(OpenType<?> openType, String defaultName) {
        if (openType == null) {
            return defaultName;
        }
        String typeName;
        if (openType instanceof ArrayType<?> arrayType && !arrayType.isPrimitiveArray()) {
            typeName = "[".repeat(arrayType.getDimension()) + "L" + arrayType.getElementOpenType().getTypeName() + ";";
        } else {
            typeName = openType.getTypeName();
        }

        return typeName;
    }

    /**
     * Try to find an {@link ArrayType} which should succeed if the element type is not {@link CompositeData}
     * or {@link TabularData}. Mind that {@link ArrayType} is never an element of {@link ArrayType} as these
     * are flattened.
     *
     * @param cls
     * @return
     */
    private static ArrayDiscovery findArrayType(Class<?> cls) throws ReflectionException {
        if (cls == null || !cls.isArray()) {
            return null;
        }
        ArrayType<?> openType = null;
        Class<?> componentType = cls;
        int dim = 0;
        while (componentType.isArray()) {
            dim++;
            componentType = componentType.getComponentType();
        }
        if (componentType.isPrimitive()) {
            return new ArrayDiscovery(ArrayType.getPrimitiveArrayType(cls), componentType, dim);
        } else {
            // ArrayType never has another ArrayType as the element, because these are flattened
            // if asked for a type like java.io.File, we'll simply get null
            // For CompositeTabular element types, we'll the information, but no actual OpenType - we'll
            // have to do more investigation
            SimpleType<?> componentOpenType = ObjectToOpenTypeConverter.knownSimpleOpenType(componentType);
            if (componentOpenType != null) {
                try {
                    return new ArrayDiscovery(new ArrayType<>(dim, componentOpenType), componentType, dim);
                } catch (OpenDataException e) {
                    throw new ReflectionException(e, e.getMessage());
                }
            }
            return new ArrayDiscovery(null, componentType, dim);
        }
    }

    /**
     * When building {@link ArrayType} we are curious about the process - in case we can't easily map
     * the element type
     *
     * @param arrayType
     * @param componentType
     * @param dimensions
     */
    private record ArrayDiscovery(ArrayType<?> arrayType, Class<?> componentType, int dimensions) {
    }

}
