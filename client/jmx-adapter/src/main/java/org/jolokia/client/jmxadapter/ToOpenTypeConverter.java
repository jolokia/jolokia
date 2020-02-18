package org.jolokia.client.jmxadapter;

import com.sun.management.VMOption;
import org.jolokia.converter.Converters;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import javax.lang.model.type.PrimitiveType;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static javax.management.openmbean.SimpleType.*;

/**
 * Attempt to produce openmbean results to emulate a native JMX connection by reverse engineering
 * type information from JSON response from Jolokia and use the appropriate converter to convert to
 * corresponding objects
 */
public class ToOpenTypeConverter {

  private static final SimpleType<?>[] typeArray = {
    VOID,
    BOOLEAN,
    CHARACTER,
    BYTE,
    SHORT,
    INTEGER,
    LONG,
    FLOAT,
    DOUBLE,
    STRING,
    BIGDECIMAL,
    BIGINTEGER,
    DATE,
    OBJECTNAME,
  };
  private static HashMap<String, OpenType<?>> TABULAR_CONTENT_TYPE;

  private static Map<String, OpenType<?>> OVERRIDDEN_COMPLEX_TYPES;

  public static Object returnOpenTypedValue(String name, Object rawValue) throws OpenDataException {
    final OpenType<?> type = recursivelyBuildOpenType(name, rawValue);
    if (type == null) {
      return rawValue;
    } else {
      return new Converters().getToOpenTypeConverter().convertToObject(type, rawValue);
    }
  }

  public static OpenType<?> recursivelyBuildOpenType(String name, Object rawValue)
      throws OpenDataException {
    for (SimpleType<?> type : typeArray) {
      if (type.isValue(rawValue)
          || (type.getClassName() != null && type.getClassName().equals(cachedType(name)))) {
        return type;
      }
    }
    if (rawValue instanceof JSONArray) {
      final JSONArray array = (JSONArray) rawValue;
      if (array.size() > 0) {
        final OpenType<?> elementType = recursivelyBuildOpenType(name + ".item", array.get(0));
        if (elementType instanceof PrimitiveType) {
          return ArrayType.getPrimitiveArrayType(array.get(0).getClass());
        } else {
          return ArrayType.getArrayType(elementType);
        }
      }
    } else if (tabularContentType(name) != null) {
      final JSONObject structure = (JSONObject) rawValue;
      final String typeName =
          "Map<java.lang.String," + tabularContentType(name).getClassName() + ">";
      return new TabularType(
          typeName,
          typeName,
          new CompositeType(
              typeName,
              typeName,
              new String[] {"key", "value"},
              new String[] {"key", "value"},
              new OpenType<?>[] {STRING, tabularContentType(name)}),
          new String[] {"key"});

    } else if (cachedType(name) != null) {
      return cachedType(name);
    } else if (rawValue instanceof JSONObject) {
      final JSONObject structure = (JSONObject) rawValue;
      final String[] keys = new String[structure.size()];
      final OpenType<?>[] types = new OpenType[structure.size()];
      int index = 0;
      for (Object element : structure.entrySet()) {
        @SuppressWarnings("unchecked")
        Map.Entry<String, Object> entry = (Entry<String, Object>) element;
        keys[index] = entry.getKey();
        types[index++] = recursivelyBuildOpenType(name + "." + entry.getKey(), entry.getValue());
      }
      return new CompositeType("complex", "complex", keys, keys, types);
    }
    // should probably never happen, to signify type could not be found
    return null;
  }

  private static OpenType<?> cachedType(final String name) throws OpenDataException {
    if (OVERRIDDEN_COMPLEX_TYPES == null) {
      OVERRIDDEN_COMPLEX_TYPES = new HashMap<String, OpenType<?>>();
      cacheComplexType(
          introspectComplexTypeFrom(MemoryUsage.class),
          "java.lang:type=Memory.NonHeapMemoryUsage",
          "java.lang:type=MemoryPool,name=Metaspace.PeakUsage",
          "java.lang:type=MemoryPool,name=Code Cache.PeakUsage",
          "java.lang:type=MemoryPool,name=Code Cache.Usage",
          "java.lang:type=MemoryPool,name=PS Old Gen.CollectionUsage",
          "java.lang:type=MemoryPool,name=PS Old Gen.PeakUsage",
          "java.lang:type=MemoryPool,name=PS Old Gen.Usage",
          "java.lang:type=MemoryPool,name=PS Eden Space.CollectionUsage",
          "java.lang:type=MemoryPool,name=PS Eden Space.PeakUsage",
          "java.lang:type=MemoryPool,name=Compressed Class Space.PeakUsage",
          "java.lang:type=MemoryPool,name=Compressed Class Space.Usage",
          "java.lang:type=MemoryPool,name=Metaspace.Usage",
          "java.lang:type=MemoryPool,name=PS Eden Space.Usage",
          "java.lang:type=Memory.HeapMemoryUsage",
          "java.lang:type=MemoryPool,name=PS Survivor Space.CollectionUsage",
          "java.lang:type=MemoryPool,name=PS Survivor Space.PeakUsage",
          "java.lang:type=MemoryPool,name=PS Perm Gen.CollectionUsage",
          "java.lang:type=MemoryPool,name=PS Perm Gen.Usage",
          "java.lang:type=MemoryPool,name=PS Survivor Space.Usage",
          "java.lang:type=MemoryPool,name=PS Perm Gen.PeakUsage");
      cacheComplexType(
          introspectComplexTypeFrom(VMOption.class),
          "com.sun.management:type=HotSpotDiagnostic.DiagnosticOptions.item",
          "com.sun.management:type=HotSpotDiagnostic.getVMOption");
      cacheComplexType(
          introspectComplexTypeFrom(ThreadInfo.class), "java.lang:type=Threading.getThreadInfo");
    }
    return OVERRIDDEN_COMPLEX_TYPES.get(name);
  }

  private static void cacheComplexType(OpenType<?> type, String... names) {
    for (String name : names) {
      OVERRIDDEN_COMPLEX_TYPES.put(name, type);
    }
  }

  private static OpenType<?> introspectComplexTypeFrom(Class<?> klass) throws OpenDataException {
    if (klass.isEnum()) {
      return STRING;
    }

    if (klass.isPrimitive()) {
      for (SimpleType<?> type : typeArray) {
        if (type.getTypeName()
            .substring(type.getTypeName().lastIndexOf('.') + 1)
            .toLowerCase()
            .startsWith(klass.getSimpleName())) {
          return type;
        }
      }
    }

    for (SimpleType<?> type : typeArray) {
      if (klass.getName().equals(type.getClassName())) {
        return type;
      }
    }

    if (klass.isArray()) {
      OpenType<?> componentType = introspectComplexTypeFrom(klass.getComponentType());
      return new ArrayType(1, componentType);
    }

    List<String> names = new LinkedList<String>();
    List<OpenType<?>> types = new LinkedList<OpenType<?>>();
    for (Method method : klass.getDeclaredMethods()) {
      // only introspect instance fields
      if ((method.getModifiers() & Modifier.STATIC) == 0 && (method.getModifiers() & Modifier.PUBLIC) != 0 && method.getParameterTypes().length == 0) {
        if(method.getName().startsWith("get")) {
          names.add(method.getName().substring(3,4).toLowerCase() +  method.getName().substring(4));
          types.add(introspectComplexTypeFrom(method.getReturnType()));
        } else if( method.getName().startsWith("is")) {
          names.add(method.getName().substring(2,3).toLowerCase() + method.getName().substring(3));
          types.add(introspectComplexTypeFrom(method.getReturnType()));
        }
      }
    }
    return new CompositeType(
        klass.getName(),
        klass.getName(),
        names.toArray(new String[0]),
        names.toArray(new String[0]),
        types.toArray(new OpenType[0]));
  }

  private static OpenType<?> tabularContentType(final String attribute) throws OpenDataException {
    if (TABULAR_CONTENT_TYPE == null) {
      TABULAR_CONTENT_TYPE = new HashMap<String, OpenType<?>>();
      TABULAR_CONTENT_TYPE.put("java.lang:type=Runtime.SystemProperties", STRING);
      TABULAR_CONTENT_TYPE.put(
          "java.lang:type=GarbageCollector,name=PS Scavenge.LastGcInfo.memoryUsageAfterGc",
          introspectComplexTypeFrom(MemoryUsage.class));
      TABULAR_CONTENT_TYPE.put(
          "java.lang:type=GarbageCollector,name=PS Scavenge.LastGcInfo.memoryUsageBeforeGc",
          introspectComplexTypeFrom(MemoryUsage.class));
    }
    return TABULAR_CONTENT_TYPE.get(attribute);
  }
}
