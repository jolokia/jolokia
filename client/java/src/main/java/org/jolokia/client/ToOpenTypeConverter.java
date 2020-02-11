package org.jolokia.client;

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

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.lang.model.type.PrimitiveType;
import javax.management.openmbean.ArrayType;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import org.jolokia.converter.Converters;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Attempt to produce openmbean results to emulate a native JMX
 * connection by reverse engineering type information from JSON response from Jolokia
 * and use the appropriate converter to convert to corresponding objects
 */
public class ToOpenTypeConverter {

  private static final SimpleType<?>[] typeArray = {
      VOID, BOOLEAN, CHARACTER, BYTE, SHORT, INTEGER, LONG, FLOAT,
      DOUBLE, STRING, BIGDECIMAL, BIGINTEGER, DATE, OBJECTNAME,
  };

  private static Map<String,String> OVERRIDDEN_COMPLEX_TYPES;



  public static Object returnOpenTypedValue(String name, Object rawValue) throws OpenDataException {
    return new Converters().getToOpenTypeConverter().convertToObject(recursivelyBuildOpenType(name, rawValue), rawValue);
  }


  public static OpenType<?> recursivelyBuildOpenType(String name, Object rawValue) throws OpenDataException {
    for(SimpleType<?> type : typeArray) {
      if(type.isValue(rawValue)) {
        return type;
      }
    }
    if(rawValue instanceof JSONArray) {
      final JSONArray array = (JSONArray) rawValue;
      if(array.size() > 0) {
        final OpenType<?> elementType = recursivelyBuildOpenType(null, array.get(0));
        if(elementType instanceof PrimitiveType) {
          return ArrayType.getPrimitiveArrayType(array.get(0).getClass());
        } else {
          return ArrayType.getArrayType(elementType);
        }

      }
    } else if (rawValue instanceof JSONObject) {
      final JSONObject structure = (JSONObject) rawValue;
      if(structure.containsKey("objectName")){
        return OBJECTNAME;
      }
      final String[] keys = new String[structure.size()];
        final OpenType<?>[] types=new OpenType[structure.size()];
        int index=0;
        for(Object element : structure.entrySet()){
          @SuppressWarnings("unchecked")
          Map.Entry<String,Object> entry= (Entry<String, Object>) element;
          keys[index]=entry.getKey();
          types[index++]=recursivelyBuildOpenType(name + "." + entry.getKey(), entry.getValue());
      }
        return new CompositeType(detectTypeName(name), "composite", keys, keys, types);
    }

    return new CompositeType("object", "object", new String[0], new String[0], new OpenType[0]);

  }

  private static String detectTypeName(final String name) {
    if(OVERRIDDEN_COMPLEX_TYPES == null) {
      OVERRIDDEN_COMPLEX_TYPES=new HashMap<String, String>();
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=Memory.NonHeapMemoryUsage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=Metaspace.PeakUsage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=Code Cache.PeakUsage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=Code Cache.Usage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Old Gen.CollectionUsage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Old Gen.PeakUsage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Old Gen.Usage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=GarbageCollector,name=PS Scavenge.LastGcInfo", "sun.management.PS Scavenge.GcInfoCompositeType");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Eden Space.CollectionUsage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Eden Space.PeakUsage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Eden Space.Usage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=Compressed Class Space.PeakUsage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=Compressed Class Space.Usage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=Metaspace.Usage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=Memory.HeapMemoryUsage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Survivor Space.CollectionUsage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Survivor Space.PeakUsage", "java.lang.management.MemoryUsage");
      OVERRIDDEN_COMPLEX_TYPES.put("java.lang:type=MemoryPool,name=PS Survivor Space.Usage", "java.lang.management.MemoryUsage");

    }
    final String overridden=OVERRIDDEN_COMPLEX_TYPES.get(name);
    return overridden != null ? overridden : "complex";
  }
}
