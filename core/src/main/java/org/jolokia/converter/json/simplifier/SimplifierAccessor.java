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
package org.jolokia.converter.json.simplifier;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.json.ObjectAccessor;
import org.jolokia.converter.json.ObjectToJsonConverter;
import org.jolokia.converter.object.Converter;
import org.jolokia.core.service.serializer.ValueFaultHandler;
import org.jolokia.json.JSONObject;

/**
 * <p>Base class for all <em>simplifiers</em>. A simplifier is a special {@link ObjectAccessor} which
 * condenses full blown Java beans (like {@link java.io.File}) to a more compact representation.
 * Simplifiers cannot be used to set values and are only used for downstream serialization.</p>
 *
 * <p>Simplifiers work similarly to {@link org.jolokia.converter.json.BeanAccessor}, but instead
 * if extracting all properties for all public <em>getters</em>, they delegate to type-specific
 * {@link AttributeExtractor attribute extractors}, which are used to create a {@link JSONObject} with
 * explicitly configured <em>attributes</em> (not necessarily related to getters).</p>
 *
 * <p>Simplifier are registered by listing the classes in a {@code META-INF/simplifiers} plain text file and
 * then picked up during initialization. The default prepackaged simplifiers are taken from
 * {@code META-INF/simplifiers-default}.</p>
 *
 * @author roland
 * @since Jul 27, 2009
 */
public abstract class SimplifierAccessor<T> implements ObjectAccessor {

    /**
     * Map of attribute names to {@link AttributeExtractor an extractor} registered by particular
     * {@link SimplifierAccessor}.
     */
    private final Map<String, AttributeExtractor<T>> extractors = new HashMap<>();

    private final Class<T> type;

    /**
     * Super constructor taking the value type as argument
     *
     * @param pType the type handled by this {@link SimplifierAccessor}
     */
    protected SimplifierAccessor(Class<T> pType) {
        type = pType;
    }

    @Override
    public Class<?> getType() {
        return type;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Deque<String> pPathParts, boolean pJsonify)
            throws AttributeNotFoundException {
        String path = pPathParts.isEmpty() ? null : pPathParts.pop();
        ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
        if (path != null) {
            return extractAttribute(pConverter, pValue, pPathParts, pJsonify, path, faultHandler);
        } else {
            return pJsonify ? extractAllAttributes(pConverter, (T) pValue, pPathParts) : pValue;
        }
    }

    @Override
    public final boolean supportsStringConversion() {
        return true;
    }

    /**
     * No setting for simplifying accessors
     * @return always {@code false}
     */
    @Override
    public final boolean canSetValue() {
        return false;
    }

    /**
     * Always throws {@link IllegalArgumentException} since a simplifier cannot be used to write values to objects.
     */
    @Override
    public final Object setObjectValue(Converter<String> pConverter, Object pObject, String pAttribute, Object pValue) {
        throw new UnsupportedOperationException("A simplifier can't be used to set a value");
    }

    /**
     * <em>Simplify</em> an object by using {@link AttributeExtractor attribute extractors} to return a key-value
     * representation of supported object.
     *
     * @param pConverter
     * @param pValue
     * @param pPath
     * @return
     * @throws AttributeNotFoundException
     */
    private Object extractAllAttributes(ObjectToJsonConverter pConverter, T pValue, Deque<String> pPath)
            throws AttributeNotFoundException {
        JSONObject ret = new JSONObject();
        for (Map.Entry<String, AttributeExtractor<T>> entry : extractors.entrySet()) {
            Deque<String> paths = new LinkedList<>(pPath);
            try {
                // extract single attribute using registered attribute extractor
                Object value = entry.getValue().extract(pValue);
                // but still apply full "paths" stack - for example if the extracted attribute was another
                // complex object with available ObjectAccessor
                ret.put(entry.getKey(), pConverter.extractObject(value, paths, true));
            } catch (AttributeExtractor.SkipAttributeException | ValueFaultHandler.AttributeFilteredException e) {
                // Skip this one
            }
        }
        if (ret.isEmpty()) {
            // Everything filtered, bubble up ...
            throw new ValueFaultHandler.AttributeFilteredException();
        }
        return ret;
    }

    /**
     * Extract single attribute using registered {@link AttributeExtractor}. The value returned is subject
     * to further extraction (drilling into the object) if path stack is not empty.
     *
     * @param pConverter
     * @param pValue
     * @param pPath
     * @param pJsonify
     * @param pAttribute
     * @param pFaultHandler
     * @return
     * @throws AttributeNotFoundException
     */
    private Object extractAttribute(ObjectToJsonConverter pConverter, Object pValue, Deque<String> pPath, boolean pJsonify, String pAttribute, ValueFaultHandler pFaultHandler)
            throws AttributeNotFoundException {
        AttributeExtractor<T> extractor = extractors.get(pAttribute);
        if (extractor == null) {
            return pFaultHandler.handleException(new AttributeNotFoundException("Illegal path element " + pAttribute + " for object " + pValue));
        }

        try {
            @SuppressWarnings("unchecked")
            Object attributeValue = extractor.extract((T) pValue);
            return pConverter.extractObject(attributeValue, pPath, pJsonify);
        } catch (AttributeExtractor.SkipAttributeException e) {
            return pFaultHandler.handleException(new AttributeNotFoundException("Illegal path element " + pAttribute + " for object " + pValue));
        }
    }

    /**
     * Add a single extractor - to be called by implementing class in the constructor
     *
     * @param pName      name of the extractor - it'll match the attribute name of the constructed {@link JSONObject}
     *                   for the class being <em>simplified</em>.
     * @param pExtractor the extractor itself
     */
    protected final void addExtractor(String pName, AttributeExtractor<T> pExtractor) {
        extractors.put(pName, pExtractor);
    }

}
