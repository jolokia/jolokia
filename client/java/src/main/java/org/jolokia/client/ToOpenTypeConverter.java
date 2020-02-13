package org.jolokia.client;

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
import java.util.HashMap;
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
      VOID, BOOLEAN, CHARACTER, BYTE, SHORT, INTEGER, LONG, FLOAT,
      DOUBLE, STRING, BIGDECIMAL, BIGINTEGER, DATE, OBJECTNAME,
  };
  private static Map<String, String> TABULAR_CONTENT_TYPE;

  private static Map<String, String> OVERRIDDEN_COMPLEX_TYPES;

  public static Object returnOpenTypedValue(String name, Object rawValue) throws OpenDataException {
    final OpenType<?> type = recursivelyBuildOpenType(name, rawValue);
    if(type == null) {
      return rawValue;
    } else {
      return new Converters().getToOpenTypeConverter()
          .convertToObject(type, rawValue);
    }
  }

  public static OpenType<?> recursivelyBuildOpenType(String name, Object rawValue)
      throws OpenDataException {
    for (SimpleType<?> type : typeArray) {
      if (type.isValue(rawValue)) {
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
      final String typeName = "Map<java.lang.String," + tabularContentType(name) + ">";
      return new TabularType(typeName, typeName,
          new CompositeType(typeName, typeName, new String[]{"key", "value"},
              new String[]{"key", "value"}, new OpenType<?>[]{
              STRING,
              recursivelyBuildOpenType(name + ".value", structure.values().iterator().next())
          }), new String[]{"key"});

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
      return new CompositeType(detectTypeName(name), detectTypeName(name), keys, keys, types);
    }
    //should probably never happen, to signify type could not be found
    return null;

  }

  private static String detectTypeName(final String name) {
    if (OVERRIDDEN_COMPLEX_TYPES == null) {
      OVERRIDDEN_COMPLEX_TYPES = new HashMap<String, String>();
      OVERRIDDEN_COMPLEX_TYPES
          .put("java.lang:type=Memory.NonHeapMemoryUsage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=Metaspace.PeakUsage",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=Code Cache.PeakUsage",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=Code Cache.Usage",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Old Gen.CollectionUsage",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Old Gen.PeakUsage",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Old Gen.Usage",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=GarbageCollector,name=PS Scavenge.LastGcInfo",
          "sun.management.PS Scavenge.GcInfoCompositeType");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Eden Space.CollectionUsage",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Eden Space.PeakUsage",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Eden Space.Usage",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES
          .put("java.lang:type=MemoryPool,name=Compressed Class Space.PeakUsage",
              "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=Compressed Class Space.Usage",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=Metaspace.Usage",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES
          .put("java.lang:type=Memory.HeapMemoryUsage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES
          .put("java.lang:type=MemoryPool,name=PS Survivor Space.CollectionUsage",
              "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Survivor Space.PeakUsage",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Survivor Space.Usage",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put(
          "java.lang:type=GarbageCollector,name=PS Scavenge.LastGcInfo.memoryUsageAfterGc.value",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put(
          "java.lang:type=GarbageCollector,name=PS Scavenge.LastGcInfo.memoryUsageBeforeGc.value",
          "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES
          .put("com.sun.management:type=HotSpotDiagnostic.DiagnosticOptions.item",
              "com.sun.management.VMOption");
      OVERRIDDEN_COMPLEX_TYPES
          .put("com.sun.management:type=HotSpotDiagnostic.getVMOption",
              "com.sun.management.VMOption");

    }
    final String overridden = OVERRIDDEN_COMPLEX_TYPES.get(name);
    return overridden != null ? overridden : "complex";
  }

  private static String tabularContentType(final String attribute) {
    if (TABULAR_CONTENT_TYPE == null) {
      TABULAR_CONTENT_TYPE = new HashMap<String, String>();
      TABULAR_CONTENT_TYPE.put("java.lang:type=Runtime.SystemProperties", "java.lang.String");
      TABULAR_CONTENT_TYPE
          .put("java.lang:type=GarbageCollector,name=PS Scavenge.LastGcInfo.memoryUsageAfterGc",
              "java.lang.management.MemoryUsage");
      TABULAR_CONTENT_TYPE
          .put("java.lang:type=GarbageCollector,name=PS Scavenge.LastGcInfo.memoryUsageBeforeGc",
              "java.lang.management.MemoryUsage");
    }
    return TABULAR_CONTENT_TYPE.get(attribute);
  }
}
