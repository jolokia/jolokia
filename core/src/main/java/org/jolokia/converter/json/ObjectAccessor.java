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

package org.jolokia.converter.json;

import java.lang.reflect.InvocationTargetException;
import java.util.Deque;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.Converter;

/**
 * <p>Interface for extracting objects or their inner data (with optional conversion of the values into their JSON
 * representation) and for manipulating the internal state of the objects (not possible for some types of objects).</p>
 *
 * <p>Up to Jolokia 2.3.0, this interface was called {@code org.jolokia.converter.json.Extractor}, but it was too
 * confusing name for the two-way nature of this interface. New name is inspired by Spring's
 * {@code org.springframework.beans.PropertyAccessor} which can also get and set values of an object. Instead of
 * using <a href="https://docs.spring.io/spring-framework/reference/core/validation/beans-beans.html#beans-beans-conventions">
 * property paths as strings</a>, we use {@link java.util.Deque} of nested properties which are interpreted
 * according to given type (like array index or key of a map).</p>
 *
 * @author roland
 * @since Jul 2, 2010
 */
public interface ObjectAccessor {

    /**
     * Gets a class of supported objects.
     *
     * @return supported type of the objects handled by this accessor
     */
    Class<?> getType();

    /**
     * <p>Extract an object from given {@code pValue} of {@link #getType() the supported type}.</p>
     *
     * <p>In the simplest case without <em>jsonification</em> and without a <em>property path</em>,
     * we extract the value itself.</p>
     *
     * <p>{@code pPathParts} may be used as a {@link Deque} (a stack) of <em>keys</em> into the object being
     * accessed, where the first item ({@link Deque#pop()}) is the 1st level inner object and the remainder of the
     * stack is passed recursively when accessing composite objects. First key may be null, which means that the
     * remaining path parts are applied to each item of the object (like elements of the array).</p>
     *
     * <p>{@code pJsonify} flag indicates that the returned value should be converted into a JSON representation.</p>
     *
     * @param pConverter the top-level converter that knows about other {@link ObjectAccessor accessors} used
     *                   for <em>drilling</em> into inner objects.
     * @param pValue     the objects being accessed
     * @param pPathParts a path into inner object(s) to access internal structure of the object. Each accessor
     *                   should {@link Deque#pop()} <em>its</em> attribute and pass the remaining stack recursively.
     * @param pJsonify   whether to convert the extracted object into a JSON representation
     * @return the extracted object in JSON or original format
     * @throws AttributeNotFoundException if the inner path does not exist.
     */
    Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Deque<String> pPathParts, boolean pJsonify)
        throws AttributeNotFoundException;

    /**
     * Some accessors may support dedicated conversion to String. By default, generic <em>extraction</em> of the
     * value from given object should be used, but in some cases (like explicit conversion to String for {@link java.util.Map}
     * keys), some accessors can be used to get a String value.
     *
     * @since 2.4.0
     * @return
     */
    default boolean supportsStringConversion() {
        return false;
    }

    /**
     * Convert a value to String - only if {@link #supportsStringConversion() such conversion is supported}.
     *
     * @since 2.4.0
     * @param pValue
     * @return
     */
    default String extractString(Object pValue) {
        return null;
    }

    /**
     * Whether this accessor can be used to set a value inside (directly or nested) a value of
     * {@link #getType() given class}.
     *
     * @return {@code true} if this extractor can be used to set inner values.
     */
    boolean canSetValue();

    /**
     * If this accessor can be used to {@link #canSetValue() set inner values}, this method sets the value
     * inside the passed <em>outer object</em>.
     *
     * @param pConverter {@link Converter} used to convert the value being set to a class of the accessed attribute
     * @param pObject    object on which to set the value
     * @param pAttribute attribute of the object to set. (For arrays or lists it should be an index.)
     * @param pValue     the new value to set after {@link Converter#convert conversion}
     * @return the old value of the changed attribute
     * @throws IllegalAccessException if the reflection error occurs when setting the value (BeanAccessor only)
     * @throws InvocationTargetException if the reflection error occurs when setting the value (BeanAccessor only)
     * @throws IllegalArgumentException if the attribute can't be converted to desired kind (like array index number)
     * @throws UnsupportedOperationException if the object doesn't allow setting "inner" values (like {@link Enum})
     */
    Object setObjectValue(Converter<String> pConverter, Object pObject, String pAttribute, Object pValue)
        throws IllegalAccessException, InvocationTargetException, IllegalArgumentException;

}
