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

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.InvalidOpenTypeException;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularType;
import org.jolokia.converter.Converters;
import org.jolokia.converter.object.StringToOpenTypeConverter;
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
  static final StringToOpenTypeConverter CONVERTER = new Converters().getToOpenTypeConverter()
      .makeForgiving();
  private static HashMap<String, OpenType<?>> TABULAR_CONTENT_TYPE;

  private static Map<String, OpenType<?>> TYPE_SPECIFICATIONS;

  public static Object returnOpenTypedValue(String name, Object rawValue,
      String typeFromMBeanInfo) throws OpenDataException {
    //special case, empty array with no type information, return object (empty list) itself
    if ( "java.util.List".equals(typeFromMBeanInfo) && rawValue instanceof JSONArray) {
      return rawValue;//JSONArray can act as a list
    } else if ("java.util.Set".equals(typeFromMBeanInfo) && rawValue instanceof JSONArray ){
      return new HashSet<Object>((Collection<?>)rawValue);
    } else if( "java.util.Map".equals(typeFromMBeanInfo) && rawValue instanceof JSONObject) {
      return rawValue;//JSONObject is a valid Map as it is
    }else if (rawValue instanceof JSONArray && ((JSONArray) rawValue).isEmpty()) {
      final OpenType<?> type = cachedType(name);
      if (type != null) {
        return new Converters().getToOpenTypeConverter().convertToObject(type, rawValue);
      } else {
        return rawValue;
      }
    }

    final OpenType<?> type = recursivelyBuildOpenType(name, rawValue, typeFromMBeanInfo);
    if (type == null) {
      return rawValue;
    } else if (type.isArray() && ((ArrayType<?>) type).isPrimitiveArray()) {
      return toPrimitiveArray((ArrayType<?>) type, (JSONArray) rawValue);
    } else {
      return CONVERTER.convertToObject(type, rawValue);
    }
  }

  /**
   * This only cover known cases. Might consider making it complete and include in jolokia core
   * ArrayTypeConverter
   *
   * @param type     Array type representing a primitive array
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
    } else if (BYTE.equals(type.getElementOpenType())) {
      byte[] byteArray = new byte[rawValue.size()];
      for (int i = 0; i < rawValue.size(); i++) {
        byteArray[i] = ((Number) rawValue.get(i)).byteValue();
      }
      return byteArray;
    }
    return rawValue.toArray(
        (Object[]) Array
            .newInstance(ClassUtil.classForName(type.getElementOpenType().getClassName()),
                rawValue.size()));
  }

  /**
   * Try to figure out open type, order of preference 1. handle simple objects, respect type
   * introspected from MBeanInfo if any 2. Handle arrays (comes before cached types due to issues
   * specifying multiple return values for all the Threading overloaded methods) 3. Handle hard
   * coded tabular return types (important for visual presentation in certain tools) 4. Use cached
   * type for attribute/item : either hardcoded to please JConsole / JVisualVM or introspected from
   * MBeanInfo 5. Dynamically build structured type from contents (will struggle with null values
   * for unknown entities) 6. Fail
   */
  public static OpenType<?> recursivelyBuildOpenType(String name, Object rawValue,
      String typeFromMBeanInfo)
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
        final OpenType<?> elementType = recursivelyBuildOpenType(name + ".item", array.get(0),
            typeFromMBeanInfo);
        if (elementType instanceof SimpleType && cachedType(name) != null) {
          return cachedType(name);
        } else {
          return ArrayType.getArrayType(elementType);
        }
      }
    } else if (tabularContentType(name) != null ) {
      final String typeName =
          "java.util.Map<java.lang.String, " + tabularContentType(name).getClassName() + ">";
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
      if("javax.management.openmbean.TabularData".equals(typeFromMBeanInfo)) {
        //keys are typically String, if the structure is emtpy wrong type will problably not cause too much problem
        OpenType<?> keyType=STRING, valueType= STRING;
        final Iterator<Map.Entry<?,?>> iterator = structure.entrySet().iterator();
        if(iterator.hasNext()){
          final Map.Entry<?,?> sample = iterator.next();
          keyType=recursivelyBuildOpenType(name+".key", sample.getKey(), null);
          valueType=recursivelyBuildOpenType(name+".value", sample.getValue(), null);
        }
        final String typeName =
            "java.util.Map<" + keyType.getClassName() +", "+ valueType.getClassName() + ">";
        return new TabularType(
            typeName,
            typeName,
            new CompositeType(
                typeName,
                typeName,
                new String[]{"key", "value"},
                new String[]{"key", "value"},
                new OpenType<?>[]{keyType, valueType}),
            new String[]{"key"});
      }
      final String[] keys = new String[structure.size()];
      final OpenType<?>[] types = new OpenType[structure.size()];
      int index = 0;
      for (Object element : structure.entrySet()) {
        @SuppressWarnings("unchecked")
        Map.Entry<String, Object> entry = (Entry<String, Object>) element;
        keys[index] = entry.getKey();
        types[index++] = recursivelyBuildOpenType(name + "." + entry.getKey(), entry.getValue(),
            typeFromMBeanInfo);
      }
      if (types.length == 0) {
        throw new InvalidOpenTypeException("No subtypes for " + name);
      } else {
        return new CompositeType("complex", "complex", keys, keys, types);
      }
    }
    if( rawValue == null ) {
      return VOID;
    }
    // should probably never happen, to signify type could not be found
    throw new InvalidOpenTypeException("Unable to figure out type for " + rawValue);
  }

  static OpenType<?> cachedType(final String name) throws OpenDataException {
    if (TYPE_SPECIFICATIONS == null) {
      TYPE_SPECIFICATIONS = new HashMap<String, OpenType<?>>();
      //Specifically override types of some central Java types to suit JConsole and jvisualvm tools
      //overrides follow QName.attribute.innerAttribute recursively
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
          "java.lang:type=MemoryPool,name=PS Perm Gen.PeakUsage",
          //openj9 follows
          "java.lang:type=MemoryPool,name=tenured-LOA.CollectionUsage",
          "java.lang:type=MemoryPool,name=class storage.PeakUsage",
          "java.lang:type=MemoryPool,name=miscellaneous non-heap storage.PeakUsage",
          "java.lang:type=MemoryPool,name=nursery-survivor.CollectionUsage",
          "java.lang:type=MemoryPool,name=JIT code cache.PeakUsage",
          "java.lang:type=GarbageCollector,name=global.LastGcInfo",
          "java.lang:type=MemoryPool,name=JIT data cache.PeakUsage",
          "java.lang:type=MemoryPool,name=tenured-SOA.CollectionUsage",
          "java.lang:type=MemoryPool,name=nursery-allocate.CollectionUsage",
          "java.lang:type=MemoryPool,name=tenured-LOA.PeakUsage",
          "java.lang:type=MemoryPool,name=class storage.Usage",
          "java.lang:type=MemoryPool,name=miscellaneous non-heap storage.Usage",
          "java.lang:type=MemoryPool,name=nursery-survivor.PeakUsage",
          "java.lang:type=MemoryPool,name=JIT code cache.PeakUsage",
          "java.lang:type=MemoryPool,name=JIT data cache.Usage",
          "java.lang:type=MemoryPool,name=tenured-SOA.PeakUsage",
          "java.lang:type=MemoryPool,name=tenured-LOA.PreCollectionUsage",
          "java.lang:type=MemoryPool,name=nursery-survivor.PreCollectionUsage",
          "java.lang:type=MemoryPool,name=JIT code cache.Usage",
          "java.lang:type=MemoryPool,name=tenured-SOA.PreCollectionUsage",
          "java.lang:type=MemoryPool,name=nursery-allocate.PeakUsage",
          "java.lang:type=MemoryPool,name=tenured-LOA.Usage",
          "java.lang:type=MemoryPool,name=nursery-survivor.Usage",
          "java.lang:type=MemoryPool,name=tenured-SOA.Usage",
          "java.lang:type=MemoryPool,name=nursery-allocate.PreCollectionUsage",
          "java.lang:type=MemoryPool,name=nursery-allocate.Usage");
      //may not exist on non Oracle/Openjdk jvms
      final Class<?> vmOptionClass = ClassUtil
          .classForName("com.sun.management.VMOption");
      if(vmOptionClass != null) {
        cacheType(
            introspectComplexTypeFrom(vmOptionClass),
            "com.sun.management:type=HotSpotDiagnostic.DiagnosticOptions.item",
            "com.sun.management:type=HotSpotDiagnostic.getVMOption");
        cacheType(
            new ArrayType<OpenType<?>>(1, introspectComplexTypeRequireNonNull(vmOptionClass)),
            "com.sun.management:type=HotSpotDiagnostic.DiagnosticOptions"
        );
      }

      //may not exist on all vms
      final Class<?> gcInfo=ClassUtil.classForName("com.sun.management.GcInfo");
      if(gcInfo != null) {
        cacheType(introspectComplexTypeFrom(gcInfo), "java.lang:type=GarbageCollector,name=scavenge.LastGcInfo", "java.lang:type=GarbageCollector,name=global.LastGcInfo" );
      }
      cacheType(
          introspectComplexTypeFrom(ThreadInfo.class),
          "java.lang:type=Threading.getThreadInfo.item", "java.lang:type=Threading.getThreadInfo");
      cacheType(ArrayType.getPrimitiveArrayType(long[].class),
          "java.lang:type=Threading.AllThreadIds");
      cacheType(introspectComplexTypeFrom(ThreadInfo.class),
          "java.lang:type=Threading.dumpAllThreads.item");
      cacheType(introspectComplexTypeFrom(ClassLoadingMXBean.class), "java.lang:type=ClassLoading");
      cacheType(introspectComplexTypeFrom(CompilationMXBean.class), "java.lang:type=Compilation");
      cacheType(introspectComplexTypeFrom(MemoryMXBean.class), "java.lang:type=Memory");
      cacheType(introspectComplexTypeFrom(OperatingSystemMXBean.class),
          "java.lang:type=OperatingSystem");
      cacheType(introspectComplexTypeFrom(RuntimeMXBean.class), "java.lang:type=Runtime");
      cacheType(introspectComplexTypeFrom(ThreadMXBean.class), "java.lang:type=Threading");
      //The below relies on type information in the client JVM (out of convenience) openjdk 11+
      //are required to make make flight recordings work in Java Mission Control
      final Class<?> recordingClass = ClassUtil.classForName("jdk.management.jfr.RecordingInfo");
      if (recordingClass != null) {
        //the array type is needed for the JFR Proxy in JMC (Also supports alternate object names)
        cacheType(ArrayType.getArrayType(introspectComplexTypeFrom(recordingClass)),
            "jdk.management.jfr:type=FlightRecorder.Recordings",
            "jdk.jfr.management:type=FlightRecorder.Recordings");
        //the item type is needed for interpreting return values
        cacheType(introspectComplexTypeFrom(recordingClass),
            "jdk.management.jfr:type=FlightRecorder.Recordings.item",
            "jdk.jfr.management:type=FlightRecorder.Recordings.item");
      }
      final Class<?> configurationClass = ClassUtil
          .classForName("jdk.management.jfr.ConfigurationInfo");
      if (configurationClass != null) {
        cacheType(ArrayType.getArrayType(introspectComplexTypeFrom(configurationClass)),
            "jdk.management.jfr:type=FlightRecorder.Configurations",
            "jdk.jfr.management:type=FlightRecorder.Configurations");
      }
      final Class<?> eventTypesClass = ClassUtil.classForName("jdk.management.jfr.EventTypeInfo");
      if (eventTypesClass != null) {
        cacheType(ArrayType.getArrayType(introspectComplexTypeFrom(eventTypesClass)),
            "jdk.management.jfr:type=FlightRecorder.EventTypes",
            "jdk.jfr.management:type=FlightRecorder.EventTypes");
      }
      cacheType(ArrayType.getPrimitiveArrayType(byte[].class),
          "jdk.management.jfr:type=FlightRecorder.readStream",
          "jdk.jfr.management:type=FlightRecorder.readStream");

    }
    //may be null on Java 10
    cacheType(STRING, "jdk.management.jfr:type=FlightRecorder.EventTypes.item.description");
    cacheType(STRING, "jdk.management.jfr:type=FlightRecorder.getRecordingOptions.destination");
    return TYPE_SPECIFICATIONS.get(name);
  }

  /**
   * Specify type to use for an attribute, operation or an attribute within the response.
   * Syntax
   * <pre>
   *   cacheType(TYPE, "ObjectName.Operation/Attribute.item(if array).attribute.innerattribute")
   * </pre>
   * @param type type to apply for reverse engineering
   * @param names One or more items to type according to syntax given above
   */
  public static void cacheType(OpenType<?> type, String... names) {
    for (String name : names) {
      TYPE_SPECIFICATIONS.put(name, type);
    }
  }

  private static OpenType<?> introspectComplexTypeRequireNonNull(Class<?> klass)
      throws OpenDataException {
    final OpenType<?> type = introspectComplexTypeFrom(klass);
    if (type == null) {
      throw new InvalidOpenTypeException("Unable to detect opentype for " + klass);
    }
    return type;
  }

  /**
   * @param klass The Java type
   * @return OpenType deducted from Java type by reflection
   * @throws OpenDataException
   */
  public static OpenType<?> introspectComplexTypeFrom(Class<?> klass) throws OpenDataException {
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
      return new ArrayType<OpenType<?>>(1,
          introspectComplexTypeRequireNonNull(klass.getComponentType()));
    }

    List<String> names = new LinkedList<String>();
    List<OpenType<?>> types = new LinkedList<OpenType<?>>();
    Class<?> classToIntrospect = klass;
    while (classToIntrospect != null && !classToIntrospect.equals(Object.class)) {
      for (Method method : classToIntrospect.getDeclaredMethods()) {
        // only introspect public instance methods
        if (!Modifier.isStatic(method.getModifiers())
            && Modifier.isPublic(method.getModifiers())
            && method.getParameterTypes().length == 0) {
          if (method.getName().startsWith("get")) {
            final String nameWithoutPrefix =
                method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4);
            recursivelyBuildSubtype(klass, names, types, method.getReturnType(), nameWithoutPrefix);
          } else if (method.getName().startsWith("is")) {
            final String nameWithoutPrefix =
                method.getName().substring(2, 3).toLowerCase() + method.getName().substring(3);
            recursivelyBuildSubtype(klass, names, types, method.getReturnType(), nameWithoutPrefix);
          }
        }
      }
      classToIntrospect = classToIntrospect.getSuperclass();
    }
    if (types.isEmpty()) {
      throw new InvalidOpenTypeException(
          "Found no fields to build composite type for class " + klass);
    }
    return new CompositeType(
        klass.getName(),
        klass.getName(),
        names.toArray(new String[0]),
        names.toArray(new String[0]),
        types.toArray(new OpenType[0]));
  }

  private static void recursivelyBuildSubtype(Class<?> klass, List<String> names,
      List<OpenType<?>> types, Class<?> subType, String nameWithoutPrefix)
      throws OpenDataException {
    if (klass.equals(subType)) {
      throw new OpenDataException(
          "Unable to support recursive types, abort and allow default handling to take place");
    }
    names.add(
        nameWithoutPrefix);
    types.add(introspectComplexTypeFrom(subType));
  }

  private static OpenType<?> tabularContentType(final String attribute) throws OpenDataException {
    if (TABULAR_CONTENT_TYPE == null) {
      TABULAR_CONTENT_TYPE = new HashMap<String, OpenType<?>>();
      TABULAR_CONTENT_TYPE.put(
          "java.lang:name=PS Scavenge,type=GarbageCollector.LastGcInfo.memoryUsageAfterGc",
          introspectComplexTypeFrom(MemoryUsage.class));
      TABULAR_CONTENT_TYPE.put(
          "java.lang:name=PS Scavenge,type=GarbageCollector.LastGcInfo.memoryUsageBeforeGc",
          introspectComplexTypeFrom(MemoryUsage.class));
      TABULAR_CONTENT_TYPE.put(
          "java.lang:name=PS MarkSweep,type=GarbageCollector.LastGcInfo.memoryUsageAfterGc",
          introspectComplexTypeFrom(MemoryUsage.class));
      TABULAR_CONTENT_TYPE.put(
          "java.lang:name=PS MarkSweep,type=GarbageCollector.LastGcInfo.memoryUsageBeforeGc",
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
        //this just means that the klass is not present in the connecting vm, fall back to default handling
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
