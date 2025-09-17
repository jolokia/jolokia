/*
 * Copyright 2009-2013 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.service.serializer;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.management.AttributeNotFoundException;
import javax.management.openmbean.OpenType;

import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.service.serializer.json.ObjectToJsonConverter;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.jolokia.service.serializer.object.Converter;
import org.jolokia.service.serializer.object.ObjectToOpenTypeConverter;
import org.jolokia.service.serializer.object.ObjectToObjectConverter;
import org.jolokia.server.core.service.api.AbstractJolokiaService;

/**
 * <p>Pluggable {@link org.jolokia.server.core.service.api.JolokiaService} for object (de)serialization</p>
 *
 * <p>This service delegates to more granular delegates and provides various type conversion operations:<ul>
 *     <li></li>
 * </ul> (string-to-object, string-to-openType and object-to-json)</p>
 *
 * @author roland
 * @since 02.08.11
 */
public class JolokiaSerializer extends AbstractJolokiaService<Serializer> implements Serializer {

    /**
     * Most generic converted for any values (usually Strings) to objects of target class
     * specified as {@link String}. It is used by other, specialized converters.
     */
    private final Converter<String> objectToObjectConverter;

    /**
     * Deserializer from String, {@link org.jolokia.json.JSONStructure} or other supported objects
     * to objects of class specified as {@link OpenType} for specialized JMX object conversion.
     */
    private final Converter<OpenType<?>> objectToOpenTypeConverter;

    // From object to json:
    private ObjectToJsonConverter toJsonConverter;

    /**
     * Default constructor - order=100, non-forgiving
     */
    public JolokiaSerializer() {
        this(100, false);
    }

    /**
     * Create a serializer with <em>forgiving</em> option
     * @param forgiving
     */
    public JolokiaSerializer(boolean forgiving) {
        this(100, forgiving);
    }

    /**
     * Create Jolokia serializer (known as <em>converters</em> in Jolokia 1.x)
     *
     * @param pOrder     order to use
     * @param pForgiving
     */
    public JolokiaSerializer(int pOrder, boolean pForgiving) {
        super(Serializer.class, pOrder);

        // generic converter of any values (primitive, basic like dates and arrays)
        objectToObjectConverter = new ObjectToObjectConverter();

        objectToOpenTypeConverter = new ObjectToOpenTypeConverter(objectToObjectConverter, pForgiving);

        // default version where JolokiaContext is not available
        toJsonConverter = new ObjectToJsonConverter((ObjectToObjectConverter) objectToObjectConverter,
            (ObjectToOpenTypeConverter) objectToOpenTypeConverter, null);
    }

    @Override
    public void init(JolokiaContext pJolokiaContext) {
        super.init(pJolokiaContext);
        ((ObjectToObjectConverter) objectToObjectConverter).setJolokiaContext(pJolokiaContext);
        toJsonConverter = new ObjectToJsonConverter((ObjectToObjectConverter) objectToObjectConverter,
            (ObjectToOpenTypeConverter) objectToOpenTypeConverter, pJolokiaContext);
    }

    @Override
    public Object serialize(Object pValue, List<String> pPathParts, SerializeOptions pOptions) throws AttributeNotFoundException {
        return toJsonConverter.serialize(pValue, pPathParts, pOptions);
    }

    @Override
    public Object deserialize(String pExpectedClassName, Object pValue) {
        return objectToObjectConverter.convert(pExpectedClassName, pValue);
    }

    @Override
    public Object setInnerValue(Object pOuterObject, Object pNewValue, List<String> pPathParts) throws AttributeNotFoundException, IllegalAccessException, InvocationTargetException {
        return toJsonConverter.setInnerValue(pOuterObject, pNewValue, pPathParts);
    }

    @Override
    public Object deserializeOpenType(OpenType<?> pOpenType, Object pValue) {
        return objectToOpenTypeConverter.convert(pOpenType, pValue);
    }

}
