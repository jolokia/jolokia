package org.jolokia.service.serializer;

import com.google.gson.*;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import org.jolokia.server.core.service.api.AbstractJolokiaService;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.server.core.service.serializer.WriteRequestValues;
import org.jolokia.server.core.util.ClassUtil;
import org.jolokia.server.core.util.EscapeUtil;
import org.jolokia.service.serializer.object.OpenTypeDeserializer;
import org.jolokia.service.serializer.object.StringToObjectConverter;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.management.AttributeNotFoundException;
import javax.management.openmbean.OpenType;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class GSonSerializer extends AbstractJolokiaService<Serializer> implements Serializer {

    private final OpenTypeDeserializer toOpenTypeConverter;
    private final Configuration jsonPathconfiguration;

    public GSonSerializer(int pOrderId) {
        super(Serializer.class, pOrderId);
        toOpenTypeConverter = new OpenTypeDeserializer(new StringToObjectConverter());
        jsonPathconfiguration = new Configuration.ConfigurationBuilder()
                .jsonProvider(new GsonJsonProvider())
                .mappingProvider(new GsonMappingProvider(new Callable<Gson>() {
                    public Gson call() throws Exception {
                        return getGson();
                    }
                }))
                .options(EnumSet.noneOf(Option.class))
                .evaluationListener(Collections.<EvaluationListener>emptyList())
                .build();
    }

    public Object serialize(Object pValue, List<String> pPathParts, SerializeOptions pOptions) throws AttributeNotFoundException {
        if (pValue == null) {
            return null;
        }
        if (pValue instanceof JSONAware) {
            return pValue;
        }
        String path = EscapeUtil.combineToPath(pPathParts);
        try {
            Gson gson = getGson();
            String json = gson.toJson(pValue);
            if (path != null) {
                json = JsonPath.parse(json, jsonPathconfiguration).read(path).toString();
            }
            return wrapString(json);
        } catch (PathNotFoundException e) {
            getJolokiaContext().error(String.format("Unexpected error when serializing %s", pValue.getClass().getSimpleName()), e);
            return null;
        } catch (InvalidJsonException e) {
            getJolokiaContext().error(String.format("Unexpected error when serializing %s", pValue.getClass().getSimpleName()), e);
            return null;
        } catch (JsonIOException e) {
            getJolokiaContext().error(String.format("Unexpected error when serializing %s", pValue.getClass().getSimpleName()), e);
            return null;
        }
    }

    public Object deserialize(String pExpectedClassName, Object pValue) {
        if (pValue == null) {
            return null;
        }
        Class<?> expectedClass = ClassUtil.classForName(pExpectedClassName);
        if (expectedClass == null) {
            return null;
        }
        if (pValue instanceof String) {
            if (((String) pValue).isEmpty() && expectedClass == String.class) {
                return pValue;
            }
        }
        try {
            Gson gson = getGson();
            return gson.fromJson(toJSON(pValue).toJSONString(), expectedClass);
        } catch (JsonSyntaxException e) {
            getJolokiaContext().error(String.format("Unexpected error when deserializing %s", pValue), e);
            return null;
        }
    }

    private Gson getGson() {
        return new GsonBuilder()
                .serializeNulls()
                .serializeSpecialFloatingPointValues()
                .create();
    }

    public WriteRequestValues setInnerValue(Object pOuterObject, Object pNewValue, List<String> pPathParts) throws AttributeNotFoundException, IllegalAccessException, InvocationTargetException {
        if (pOuterObject == null) {
            return null;
        }
        String originalJson = ((JSONAware) serialize(pOuterObject, Collections.<String>emptyList(), null)).toJSONString();
        JsonElement newValue = (JsonElement) deserialize(JsonElement.class.getName(), pNewValue);
        JSONAware originalValue = (JSONAware) serialize(pOuterObject, pPathParts, null);

        String updatedJson;
        if (originalValue == null) {
            String lastPathElement = pPathParts.remove(pPathParts.size() - 1);
            String path = buildPath(pPathParts);
            updatedJson = JsonPath.parse(originalJson, jsonPathconfiguration).put(path, lastPathElement, newValue).jsonString();
        } else {
            String path = buildPath(pPathParts);
            updatedJson = JsonPath.parse(originalJson, jsonPathconfiguration).set(path, newValue).jsonString();
        }
        return new WriteRequestValues(deserialize(pOuterObject.getClass().getName(), updatedJson), originalValue);
    }

    public Object deserializeOpenType(OpenType<?> pOpenType, Object pValue) {
        return toOpenTypeConverter.deserialize(pOpenType, pValue);
    }

    private String buildPath(List<String> pPathParts) {
        String path = EscapeUtil.combineToPath(pPathParts);
        if (path == null) {
            path = "$";
        }
        return path;
    }

    private JSONAware toJSON(final Object pValue) {
        if (pValue == null) {
            return wrapNull();
        }
        Class givenClass = pValue.getClass();
        if (JSONAware.class.isAssignableFrom(givenClass)) {
            return (JSONAware) pValue;

        } else if (pValue instanceof Map) {
            return new JSONObject((Map) pValue);
        } else {
            try {
                JSONAware result = (JSONAware) new JSONParser().parse(pValue.toString());
                if (result == null) {
                    return wrapNull();
                } else {
                    return result;
                }
            } catch (ParseException e) {
                return wrapWithToString(pValue);
            } catch (ClassCastException exp) {
                return wrapWithToString(pValue);
            }
        }
    }

    private JSONAware wrapNull() {
        return new JSONAware() {
            public String toJSONString() {
                return null;
            }

            @Override
            public String toString() {
                return toJSONString();
            }
        };
    }

    private JSONAware wrapWithToString(final Object pValue) {
        return new JSONAware() {
            public String toJSONString() {
                return pValue.toString();
            }

            @Override
            public String toString() {
                return toJSONString();
            }
        };
    }

    private JSONAware wrapString(final String value) {
        return new JSONAware() {
            public String toJSONString() {
                return value;
            }

            @Override
            public String toString() {
                return value;
            }
        };
    }
}
