package org.jolokia.agent.service.serializer;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import javax.management.AttributeNotFoundException;
import javax.management.openmbean.OpenType;

import org.jolokia.agent.service.serializer.json.ObjectToJsonConverter;
import org.jolokia.agent.core.service.serializer.SerializeOptions;
import org.jolokia.agent.service.serializer.object.OpenTypeDeserializer;
import org.jolokia.agent.service.serializer.object.StringToObjectConverter;
import org.jolokia.agent.core.service.AbstractJolokiaService;
import org.jolokia.agent.core.service.serializer.JmxSerializer;

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
public class Converters extends AbstractJolokiaService<JmxSerializer> implements JmxSerializer {

    // From object to json:
    private ObjectToJsonConverter toJsonConverter;

    // From string/json to object:
    private StringToObjectConverter toObjectConverter;
    private OpenTypeDeserializer toOpenTypeConverter;

    /**
     * Default constructor
     */
    public Converters() {
        this(100);
    }

    /**
     * Create converters (string-to-object, string-to-openType and object-to-json)
     *
     * @param pOrder order to use
     */
    public Converters(int pOrder) {
        super(JmxSerializer.class,pOrder);
        toObjectConverter = new StringToObjectConverter();
        toOpenTypeConverter = new OpenTypeDeserializer(toObjectConverter);
        toJsonConverter = new ObjectToJsonConverter(toObjectConverter);
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
}
