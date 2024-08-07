package org.jolokia.service.serializer;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.management.AttributeNotFoundException;
import javax.management.openmbean.OpenType;

import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.server.core.service.serializer.Serializer;
import org.jolokia.service.serializer.json.ObjectToJsonConverter;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.jolokia.service.serializer.object.OpenTypeDeserializer;
import org.jolokia.service.serializer.object.StringToObjectConverter;
import org.jolokia.server.core.service.api.AbstractJolokiaService;

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

/**
 * Wrapper class holding various converters
 *
 * @author roland
 * @since 02.08.11
 */
public class JolokiaSerializer extends AbstractJolokiaService<Serializer> implements Serializer {

    // From object to json:
    private ObjectToJsonConverter toJsonConverter;

    // From string/json to object:
    private final StringToObjectConverter toObjectConverter;
    private final OpenTypeDeserializer toOpenTypeConverter;

    /**
     * Default constructor
     */
    public JolokiaSerializer() {
        this(100);
    }

    /**
     * Create converters (string-to-object, string-to-openType and object-to-json)
     *
     * @param pOrder order to use
     */
    public JolokiaSerializer(int pOrder) {
        super(Serializer.class,pOrder);
        toObjectConverter = new StringToObjectConverter();
        toOpenTypeConverter = new OpenTypeDeserializer(toObjectConverter);
        // default version where context is not available
        toJsonConverter = new ObjectToJsonConverter(toObjectConverter, null);
    }

    @Override
    public void init(JolokiaContext pJolokiaContext) {
        super.init(pJolokiaContext);
        toJsonConverter = new ObjectToJsonConverter(toObjectConverter, pJolokiaContext);
    }

    /** {@inheritDoc} */
    public Object serialize(Object pValue, List<String> pPathParts, SerializeOptions pOptions) throws AttributeNotFoundException {
        return toJsonConverter.serialize(pValue,pPathParts,pOptions);
    }

    /** {@inheritDoc} */
    public Object deserialize(String pExpectedClassName, Object pValue) {
        return toObjectConverter.deserialize(pExpectedClassName,pValue);
    }

    /** {@inheritDoc} */
    public Object setInnerValue(Object pOuterObject, Object pNewValue, List<String> pPathParts) throws AttributeNotFoundException, IllegalAccessException, InvocationTargetException {
        return toJsonConverter.setInnerValue(pOuterObject,pNewValue,pPathParts);
    }

    /** {@inheritDoc} */
    public Object deserializeOpenType(OpenType<?> pOpenType, Object pValue) {
        return toOpenTypeConverter.deserialize(pOpenType,pValue);
    }

    public JolokiaSerializer makeForgiving() {
        toOpenTypeConverter.makeForgiving();
        return this;
    }
}
