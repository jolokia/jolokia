package org.jolokia.server.core.service.serializer;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.management.AttributeNotFoundException;
import javax.management.openmbean.OpenType;

import org.jolokia.server.core.service.api.JolokiaService;

/**
 * Interface defining a Jolokia serializer which is also a plugable service. This interface
 * is still connected to <em>json-simple</em>, but this dependency might be removed in
 * the future.
 *
 * @author roland
 * @since 02.10.13
 */
public interface Serializer extends JolokiaService<Serializer> {

    /**
     * Convert the return value to a JSON object.
     *
     * @param pValue the value to convert
     * @param pPathParts path parts to use for extraction
     * @param pOptions options used for parsing
     * @return the converter object. This either a subclass of {@link org.json.simple.JSONAware} or a basic data type like String or Long.
     * @throws AttributeNotFoundException if within an path an attribute could not be found
     */
    Object serialize(Object pValue, List<String> pPathParts, SerializeOptions pOptions)
            throws AttributeNotFoundException;

    /**
     * Convert value from a either a given object or its string representation.
     * If the value is already assignable to the given class name it is returned directly.
     *
     * @param pExpectedClassName type name of the expected type
     * @param pValue value to either take directly or to convert from its string representation.
     * @return the converted object which is of type <code>pExpectedClassName</code>
     */
    Object deserialize(String pExpectedClassName, Object pValue);

    /**
     * Set an inner value of a complex object. A given path must point to the attribute/index to set within the outer object.
     *
     * @param pOuterObject the object to dive in
     * @param pNewValue the value to set
     * @param pPathParts the path within the outer object. This object will be modified and must be a modifiable list.
     * @return the old value
     *
     * @throws AttributeNotFoundException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    WriteRequestValues setInnerValue(Object pOuterObject, Object pNewValue, List<String> pPathParts)
            throws AttributeNotFoundException, IllegalAccessException, InvocationTargetException;

    /**
     * Handle conversion for OpenTypes. The value is expected to be in JSON (either
     * an {@link org.json.simple.JSONAware} object or its string representation.
     *
     * @param pOpenType target type
     * @param pValue value to convert from
     * @return the converted value
     */
    Object deserializeOpenType(OpenType<?> pOpenType, Object pValue);
}
