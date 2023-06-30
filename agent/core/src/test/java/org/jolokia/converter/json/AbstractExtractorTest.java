package org.jolokia.converter.json;

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

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.StringToObjectConverter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

/**
 * @author roland
 * @since 13.08.11
 */
abstract public class AbstractExtractorTest {

    protected Extractor extractor;
    protected ObjectToJsonConverter converter;
    protected StringToObjectConverter stringToObjectConverter;
    @BeforeMethod
    public void setup() {
        extractor = createExtractor();
        stringToObjectConverter = new StringToObjectConverter();
        converter = new ObjectToJsonConverter(stringToObjectConverter);
        converter.setupContext(new JsonConvertOptions.Builder().useAttributeFilter(true).build());
    }

    @AfterMethod
    public void teardown() {
        converter.clearContext();
    }

    protected Object extractJson(Object pValue,String ... extraArgs) throws AttributeNotFoundException {
        return extract(pValue, extraArgs, true);
    }

    protected Object extractObject(Object pValue,String ... extraArgs) throws AttributeNotFoundException {
        return extract(pValue, extraArgs, false);
    }

    protected Object setObject(Object pInner,String pAttribute,Object pValue) throws InvocationTargetException, IllegalAccessException {
        return extractor.setObjectValue(stringToObjectConverter,pInner,pAttribute,pValue);
    }

    private Object extract(Object pValue, String[] extraArgs, boolean pJsonify) throws AttributeNotFoundException {
        Stack<String> args = new Stack<String>();
        args.addAll(Arrays.asList(extraArgs));
        Collections.reverse(args);

        return extractor.extractObject(converter,pValue,args, pJsonify);
    }


    abstract Extractor createExtractor();
}
