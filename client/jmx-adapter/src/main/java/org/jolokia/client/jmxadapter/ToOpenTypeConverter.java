package org.jolokia.client.jmxadapter;

import static javax.management.openmbean.SimpleType.BIGDECIMAL;
import static javax.management.openmbean.SimpleType.BIGINTEGER;
import static javax.management.openmbean.SimpleType.BOOLEAN;
import static javax.management.openmbean.SimpleType.BYTE;
import static javax.management.openmbean.SimpleType.CHARACTER;
import static javax.management.openmbean.SimpleType.DATE;
import static javax.management.openmbean.SimpleType.DOUBLE;
import static javax.management.openmbean.SimpleType.FLOAT;
import static javax.management.openmbean.SimpleType.INTEGER;
import static javax.management.openmbean.SimpleType.LONG;
import static javax.management.openmbean.SimpleType.OBJECTNAME;
import static javax.management.openmbean.SimpleType.SHORT;
import static javax.management.openmbean.SimpleType.STRING;
import static javax.management.openmbean.SimpleType.VOID;

import com.sun.management.VMOption;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadInfo;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularType;
import org.jolokia.converter.Converters;
import org.jolokia.util.ClassUtil;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

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

  private static Map<String, OpenType<?>> TYPE_SPECIFICATIONS;

  public static Object returnOpenTypedValue(String name, Object rawValue) throws OpenDataException {
    final OpenType<?> type = recursivelyBuildOpenType(name, rawValue);
    if (type == null) {
      return rawValue;
    } else if (type.isArray() && ((ArrayType<?>) type).isPrimitiveArray()) {
      return toPrimitiveArray((ArrayType<?>) type, (JSONArray) rawValue);
    } else {
      return new Converters().getToOpenTypeConverter().convertToObject(type, rawValue);
    }
  }

  /**
   * This only cover known cases. Might consider making it complete and
   * include in jolokia core ArrayTypeConverter
   *
   * @param type Array type representing a primitive array
   * @param rawValue JSONArray with the values (boxed types)
   * @return A primitive array
   */
  private static Object toPrimitiveArray(ArrayType<?> type, JSONArray rawValue) {
    if (LONG.equals(type.getElementOpenType())) {
      long[] longArray = new long[rawValue.size()];
      for (int i = 0; i < rawValue.size(); i++) {
        longArray[i] = ((Number) rawValue.get(i)).longValue();
      }
      return longArray;
    } else if (INTEGER.equals(type.getElementOpenType())) {
      int[] intArray = new int[rawValue.size()];
      for (int i = 0; i < rawValue.size(); i++) {
        intArray[i] = ((Number) rawValue.get(i)).intValue();
      }
      return intArray;
    } else if (DOUBLE.equals(type.getElementOpenType())) {
      double[] doubleArray = new double[rawValue.size()];
      for (int i = 0; i < rawValue.size(); i++) {
        doubleArray[i] = ((Number) rawValue.get(i)).doubleValue();
      }
      return doubleArray;
    } else if (BOOLEAN.equals(type.getElementOpenType())) {
      boolean[] booleanArray = new boolean[rawValue.size()];
      for (int i = 0; i < rawValue.size(); i++) {
        booleanArray[i] = rawValue.get(i) == Boolean.TRUE;
      }
      return booleanArray;
    }
    return rawValue.toArray(
        (Object[]) Array
            .newInstance(ClassUtil.classForName(type.getElementOpenType().getClassName()),
                rawValue.size()));
  }

  public static OpenType<?> recursivelyBuildOpenType(String name, Object rawValue)
      throws OpenDataException {
    for (SimpleType<?> type : typeArray) {
      if (type.isValue(rawValue)
          || (type.getClassName() != null && type.equals(cachedType(name)))) {
        return type;
      }
    }
    if (rawValue instanceof JSONArray) {
      final JSONArray array = (JSONArray) rawValue;
      if (array.size() > 0) {
        final OpenType<?> elementType = recursivelyBuildOpenType(name + ".item", array.get(0));
        if (elementType instanceof SimpleType && cachedType(name) != null) {
          return cachedType(name);
        } else {
          return ArrayType.getArrayType(elementType);
        }
      }
    } else if (tabularContentType(name) != null) {
      final String typeName =
          "Map<java.lang.String," + tabularContentType(name).getClassName() + ">";
      return new TabularType(
          typeName,
          typeName,
          new CompositeType(
              typeName,
              typeName,
              new String[]{"key", "value"},
              new String[]{"key", "value"},
              new OpenType<?>[]{STRING, tabularContentType(name)}),
          new String[]{"key"});

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

  static OpenType<?> cachedType(final String name) throws OpenDataException {
    if (TYPE_SPECIFICATIONS == null) {
      TYPE_SPECIFICATIONS = new HashMap<String, OpenType<?>>();
      //Specifically override types of some central Java types to suit JConsole and jvisualvm tools
      cacheType(
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
      cacheType(
          introspectComplexTypeFrom(VMOption.class),
          "com.sun.management:type=HotSpotDiagnostic.DiagnosticOptions.item",
          "com.sun.management:type=HotSpotDiagnostic.getVMOption");
      cacheType(
          new ArrayType<OpenType<?>>(1, introspectComplexTypeFrom(VMOption.class)),
          "com.sun.management:type=HotSpotDiagnostic.DiagnosticOptions"
      );
      cacheType(
          introspectComplexTypeFrom(ThreadInfo.class),
          "java.lang:type=Threading.getThreadInfo.item", "java.lang:type=Threading.getThreadInfo");
      cacheType(ArrayType.getPrimitiveArrayType(long[].class),
          "java.lang:type=Threading.AllThreadIds");
      cacheType(introspectComplexTypeFrom(ThreadInfo.class),
          "java.lang:type=Threading.dumpAllThreads.item");
    }
    return TYPE_SPECIFICATIONS.get(name);
  }

  static void cacheType(OpenType<?> type, String... names) {
    for (String name : names) {
      TYPE_SPECIFICATIONS.put(name, type);
    }
  }

  private static OpenType<?> introspectComplexTypeFrom(Class<?> klass) throws OpenDataException {
    if (CompositeData.class.equals(klass) || TabularData.class.equals(klass)) {
      //do not attempt to read from these classes, will have to be created from the "real" class runtime
      return null;
    }
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
      return new ArrayType<OpenType<?>>(1, componentType);
    }

    List<String> names = new LinkedList<String>();
    List<OpenType<?>> types = new LinkedList<OpenType<?>>();
    Class<?> classToIntrospect = klass;
    while (classToIntrospect != null && !classToIntrospect.equals(Object.class)) {
      for (Method method : classToIntrospect.getDeclaredMethods()) {
        // only introspect instance fields
        if ((method.getModifiers() & Modifier.STATIC) == 0
            && (method.getModifiers() & Modifier.PUBLIC) != 0
            && method.getParameterTypes().length == 0) {
          if (method.getName().startsWith("get")) {
            names.add(
                method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4));
            types.add(introspectComplexTypeFrom(method.getReturnType()));
          } else if (method.getName().startsWith("is")) {
            names.add(
                method.getName().substring(2, 3).toLowerCase() + method.getName().substring(3));
            types.add(introspectComplexTypeFrom(method.getReturnType()));
          }
        }
      }
      classToIntrospect = classToIntrospect.getSuperclass();
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

  static OpenType<?> typeFor(final String attributeType) throws OpenDataException {
    Class<?> klass = ClassUtil.classForName(attributeType);
    if (klass == null) {
      if (attributeType.equals("int")) {
        klass = Integer.class;
      } else if (attributeType.equals("long")) {
        klass = Long.class;
      } else if (attributeType.equals("boolean")) {
        klass = Boolean.class;
      } else if (attributeType.equals("double")) {
        klass = Double.class;
      } else {
        System.err.println("Unrecognized attribute type " + attributeType);
        return null;
      }
    }
    return typeFor(klass);
  }

  static OpenType<?> typeFor(Class<?> attributeClass) throws OpenDataException {
    for (SimpleType<?> simpleType : typeArray) {
      if (simpleType.getClass().isAssignableFrom(attributeClass)) {
        return simpleType;
      }
    }
    return introspectComplexTypeFrom(attributeClass);
  }
}
