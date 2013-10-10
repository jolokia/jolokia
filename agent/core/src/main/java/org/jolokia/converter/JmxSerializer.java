package org.jolokia.converter;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.management.AttributeNotFoundException;
import javax.management.openmbean.OpenType;

import org.jolokia.converter.json.SerializeOptions;
import org.jolokia.service.JolokiaService;

/**
 * @author roland
 * @since 02.10.13
 */
public interface JmxSerializer extends JolokiaService<JmxSerializer> {

    public Object serialize(Object pValue, List<String> pPathParts, SerializeOptions pOptions) throws AttributeNotFoundException;

    public Object deserialize(String pExpectedClassName, Object pValue);

    public Object setInnerValue(Object pOuterObject, Object pNewValue, List<String> pPathParts)
            throws AttributeNotFoundException, IllegalAccessException, InvocationTargetException;

    Object deserializeOpenType(OpenType<?> pOpenType, Object pValue);
}
