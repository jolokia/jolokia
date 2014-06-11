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
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Stack<String> pPathPart, boolean jsonify) throws AttributeNotFoundException {
        String pathPart = pPathPart.isEmpty() ? null : pPathPart.pop();
        Enum en = (Enum) pValue;
        String name = en.name();
        if (pathPart != null) {
            if (name.equals(pathPart)) {
                return name;
            } else {
                return pConverter.getValueFaultHandler().handleException(
                        new AttributeNotFoundException("Enum value '" + name + "' doesn't match path '" + pathPart + "'" ));
            }
        }
        return jsonify ? name : en;
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
