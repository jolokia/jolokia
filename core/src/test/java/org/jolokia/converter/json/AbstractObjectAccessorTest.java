/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jolokia.converter.json;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.ObjectToObjectConverter;
import org.jolokia.core.service.serializer.SerializeOptions;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/**
 * @author roland
 * @since 13.08.11
 */
abstract public class AbstractObjectAccessorTest {

    protected ObjectAccessor objectAccessor;
    protected ObjectToJsonConverter converter;
    protected ObjectToObjectConverter objectToObjectConverter;

    @BeforeMethod
    public void setup() {
        objectAccessor = createExtractor();
        objectToObjectConverter = new ObjectToObjectConverter();
        converter = new ObjectToJsonConverter(objectToObjectConverter, null, null);
        converter.setupContext(new SerializeOptions.Builder()
            .useAttributeFilter(true)
            .maxCollectionSize(5)
            .build());
    }

    @AfterMethod
    public void teardown() {
        converter.clearContext();
    }

    protected Object extractJson(Object pValue, String... extraArgs) throws AttributeNotFoundException {
        return extract(pValue, extraArgs, true);
    }

    protected Object extractObject(Object pValue, String... extraArgs) throws AttributeNotFoundException {
        return extract(pValue, extraArgs, false);
    }

    protected Object setObject(Object pInner, String pAttribute, Object pValue) throws InvocationTargetException, IllegalAccessException {
        return objectAccessor.setObjectValue(objectToObjectConverter, pInner, pAttribute, pValue);
    }

    private Object extract(Object pValue, String[] extraArgs, boolean pJsonify) throws AttributeNotFoundException {
        Deque<String> args = new LinkedList<>(Arrays.asList(extraArgs));

        return objectAccessor.extractObject(converter, pValue, args, pJsonify);
    }

    abstract org.jolokia.converter.json.ObjectAccessor createExtractor();

}
