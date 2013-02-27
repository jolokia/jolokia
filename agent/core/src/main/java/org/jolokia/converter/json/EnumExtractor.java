package org.jolokia.converter.json;

import java.lang.reflect.InvocationTargetException;
import java.util.Stack;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.StringToObjectConverter;

/**
 * Extractor for extracting enums. Enums are represented by the canonical name (Enum.name()).
 *
 * @author roland
 * @since 18.02.13
 */
public class EnumExtractor implements Extractor {

    /** {@inheritDoc} */
    public Class getType() {
        return Enum.class;
    }

    /** {@inheritDoc} */
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pExtraArgs, boolean jsonify) throws AttributeNotFoundException {
        if (!jsonify) {
            return pValue;
        }
        Enum en = (Enum) pValue;
        return en.name();
    }

    /** {@inheritDoc} */
    public Object setObjectValue(StringToObjectConverter pConverter, Object pInner, String pAttribute, Object pValue) throws IllegalAccessException, InvocationTargetException {
        throw new IllegalArgumentException("An enum itself is immutable an cannot change its value");
    }

    /** {@inheritDoc} */
    public boolean canSetValue() {
        return false;
    }
}
