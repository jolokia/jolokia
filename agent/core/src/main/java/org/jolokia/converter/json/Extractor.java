package org.jolokia.converter.json;

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

import java.lang.reflect.InvocationTargetException;
import java.util.Stack;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.StringToObjectConverter;

/**
 * Interface for extractor serializing an object to a JSON representation.
 * Each extractor is responsible for a single type.
 *
 * @author roland
 * @since Jul 2, 2010
*/
public interface Extractor {

    /**
     * Type for which this extractor can objects of this type
     *
     * @return type which can be handled
     */
    Class getType();

    /**
     * Extract an object from pValue. In the simplest case, this is the value itself.
     * For more complex data types, it is converted into a JSON structure if possible
     * (and if 'jsonify' is true). pExtraArgs is not null, this returns only a substructure,
     * specified by the path represented by this stack
     *
     * @param pConverter the global converter in order to be able do dispatch for
     *        serializing inner data types
     * @param pValue the value to convert
     * @param pExtraArgs extra arguments which contain e.g. a path
     * @param jsonify whether to convert to a JSON object/list or whether the plain object
     *        should be returned. The later is required for writing an inner value
     * @return the extracted object
     * @throws AttributeNotFoundException if the inner path does not exist.
     */
    Object extractObject(ObjectToJsonConverter pConverter,Object pValue, Stack<String> pExtraArgs,boolean jsonify)
            throws AttributeNotFoundException;

    /**
     * If this extractor is able to set a value (see {@link #canSetValue()}), this method sets the value
     * even on an inner object
     *
     * @param pConverter the global converter in order to be able do dispatch for
     *        serializing inner data types
     * @param pInner object on which to set the value
     * @param pAttribute attribute of the object to set
     * @param pValue the new value to set

     * @return the old value
     * @throws IllegalAccessException if the attribute to set to is not accessible
     * @throws InvocationTargetException reflection error
     */
    Object setObjectValue(StringToObjectConverter pConverter,Object pInner, String pAttribute, Object pValue)
            throws IllegalAccessException, InvocationTargetException;

    /**
     * Whether this extractor is able to set a value.
     *
     * @return true if this extractor can set a value, false otherwise.
     */
    boolean canSetValue();
}
