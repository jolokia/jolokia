package org.jolokia.server.core.util;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.management.AttributeNotFoundException;
import javax.management.openmbean.*;

import org.jolokia.server.core.service.api.AbstractJolokiaService;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.jolokia.json.JSONStructure;
import org.jolokia.json.JSONObject;
import org.jolokia.json.parser.JSONParser;
import org.jolokia.json.parser.ParseException;

/**
 * @author roland
 * @since 20.02.14
 */
public class TestSerializer extends AbstractJolokiaService<Serializer> implements Serializer {
    public TestSerializer() {
        super(Serializer.class, 0);
    }

    public Object serialize(Object pValue, List<String> pPathParts, SerializeOptions pOptions) throws AttributeNotFoundException {
        if (pValue instanceof Map) {
            return pValue;
        } else {
            JSONObject ret = new JSONObject();
            ret.put("testClass",pValue.getClass().toString());
            ret.put("testString",pValue.toString());
            return ret;
        }
    }

    public Object deserialize(String pExpectedClassName, Object pValue) {
        return pValue;
    }

    public Object setInnerValue(Object pOuterObject, Object pNewValue, List<String> pPathParts) {
        return null;
    }

    @SuppressWarnings("unchecked")
    public Object deserializeOpenType(OpenType<?> pOpenType, Object pValue) {
        try {
            if (pOpenType instanceof CompositeType) {
                JSONStructure val = (JSONStructure) new JSONParser().parse(pValue.toString());
                return new CompositeDataSupport((CompositeType) pOpenType, (Map<String, ?>) val);
            }
            return null;
        } catch (ParseException | OpenDataException | IOException e) {
            throw new IllegalArgumentException(pOpenType + " " + pValue);
        }
    }
}
