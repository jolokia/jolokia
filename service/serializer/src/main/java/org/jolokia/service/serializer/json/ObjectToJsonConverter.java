package org.jolokia.service.serializer.json;


import java.lang.reflect.InvocationTargetException;
import java.util.*;

import javax.management.AttributeNotFoundException;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.service.api.JolokiaContext;
import org.jolokia.service.serializer.object.Converter;
import org.jolokia.service.serializer.object.ObjectToObjectConverter;
import org.jolokia.server.core.service.serializer.SerializeOptions;
import org.jolokia.server.core.service.serializer.ValueFaultHandler;
import org.jolokia.server.core.util.LocalServiceFactory;
import org.jolokia.server.core.util.EscapeUtil;

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
 * A converter which converts attribute and return values
 * into a JSON representation. It uses certain handlers for this which
 * are registered in the constructor.
 * <p>
 * Each handler gets a reference to this converter object so that it
 * can use it for a recursive solution of nested objects.
 *
 * @author roland
 * @since Apr 19, 2009
 */
public final class ObjectToJsonConverter {

    // List of dedicated handlers used for delegation in serialization/deserialization
    private final List<Extractor> extractors;

    private final ArrayExtractor arrayExtractor;

    // Thread-Local set in order to prevent infinite recursions
    private final ThreadLocal<ObjectSerializationContext> stackContextLocal = new ThreadLocal<>();

    // Used for converting string to objects when setting attributes
    private final Converter<String> stringToObjectConverter;

    // Definition of simplifiers
    private static final String SIMPLIFIERS_DEFAULT_DEF = "META-INF/jolokia/simplifiers-default";
    private static final String SIMPLIFIERS_DEF         = "META-INF/jolokia/simplifiers";

    private static final Deque<String> EMPTY_DEQUE = new LinkedList<>();

    private final JolokiaContext context;

    public ObjectToJsonConverter(ObjectToObjectConverter pObjectToObjectConverter) {
        this(pObjectToObjectConverter, null);
    }

    /**
     * New object-to-json converter
     *
     * @param pStringToObjectConverter used when setting values
     */
    public ObjectToJsonConverter(Converter<String> pStringToObjectConverter, JolokiaContext context) {

        extractors = new ArrayList<>();

        // TabularDataExtractor must be before MapExtractor
        // since TabularDataSupport isa Map
        extractors.add(new TabularDataExtractor());
        extractors.add(new CompositeDataExtractor());

        // Collection handlers
        extractors.add(new ListExtractor());
        extractors.add(new MapExtractor());
        extractors.add(new CollectionExtractor());

        // Special, well known objects
        addSimplifiers(extractors);

        // Enum handling
        extractors.add(new EnumExtractor());

        // Special date handling
        String dateFormat = context == null
            ? ConfigKey.DATE_FORMAT.getDefaultValue() : context.getConfig(ConfigKey.DATE_FORMAT);
        TimeZone dateFormatZone = context == null
            ? TimeZone.getDefault() : TimeZone.getTimeZone(context.getConfig(ConfigKey.DATE_FORMAT_ZONE));
        extractors.add(new DateExtractor(dateFormat, dateFormatZone));
        extractors.add(new CalendarExtractor(dateFormat, dateFormatZone));
        extractors.add(new TemporalExtractor(dateFormat, dateFormatZone));

        // Must be last in handlers, used default algorithm
        extractors.add(new BeanExtractor());

        arrayExtractor = new ArrayExtractor();

        stringToObjectConverter = pStringToObjectConverter;

        this.context = context;
    }


    /**
     * Convert the return value to a JSON object.
     *
     * @param pValue the value to convert
     * @param pPathParts path parts to use for extraction
     * @param pOptions options used for parsing
     * @return the converter object. This either a subclass of {@link org.jolokia.json.JSONStructure} or a basic data type like String or Long.
     * @throws AttributeNotFoundException if within a path an attribute could not be found
     */
    public Object serialize(Object pValue, List<String> pPathParts, SerializeOptions pOptions)
            throws AttributeNotFoundException {
        Deque<String> extraStack = pPathParts != null ? EscapeUtil.reversePath(pPathParts) : new LinkedList<>();
        return extractObjectWithContext(pValue, extraStack, pOptions, true);
    }

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
    public Object setInnerValue(Object pOuterObject, Object pNewValue, List<String> pPathParts)
            throws AttributeNotFoundException, IllegalAccessException, InvocationTargetException {
        String lastPathElement = pPathParts.remove(pPathParts.size()-1);
        Deque<String> extraStack = EscapeUtil.reversePath(pPathParts);

        // Get the object pointed to do with path-1
        // We are using no limits here, since a path must have been given (see above), and hence we should
        // be safe anyway.
        Object inner = extractObjectWithContext(pOuterObject, extraStack, SerializeOptions.DEFAULT, false);

        // Set the attribute pointed to by the path elements
        // (depending on the parent object's type)
        return setObjectValue(inner, lastPathElement, pNewValue);
    }


    /**
     * Related to {@link #extractObjectWithContext} except that
     * it does not set up a context. This method is called back from the
     * various extractors to recursively continue the extraction, hence it is public.
     * <p>
     * This method must not be used as entry point for serialization.
     * Use {@link #serialize(Object, List, SerializeOptions)} or
     * {@link #setInnerValue(Object, Object, List)} instead.
     *
     * @param pValue value to extract from
     * @param pPathParts stack for diving into the object
     * @param pJsonify whether a JSON representation {@link org.jolokia.json.JSONObject}
     * @return extracted object either in native format or as {@link org.jolokia.json.JSONObject}
     * @throws AttributeNotFoundException if an attribute is not found during traversal
     */
    public Object extractObject(Object pValue, Deque<String> pPathParts, boolean pJsonify)
            throws AttributeNotFoundException {
        ObjectSerializationContext stackContext = stackContextLocal.get();
        String limitReached = checkForLimits(pValue, stackContext);
        Deque<String> pathStack = pPathParts != null ? pPathParts : EMPTY_DEQUE;
        if (limitReached != null) {
            return limitReached;
        }
        try {
            if (pValue == null) {
                return pathStack.isEmpty() ?
                        null :
                        stackContext.getValueFaultHandler().handleException(
                                new AttributeNotFoundException("Cannot apply a path to an null value"));
            }

            stackContext.push(pValue);
            if (pValue.getClass().isArray()) {
                // Special handling for arrays
                return arrayExtractor.extractObject(this,pValue,pathStack,pJsonify);
            }
            return callHandler(pValue, pathStack, pJsonify);
        } finally {
            stackContext.pop(pValue);
        }
    }

    // =================================================================================================

    /**
     * Handle a value which means to dive into the internal of a complex object
     * (if <code>pExtraArgs</code> is not null) and/or to convert
     * it to JSON (if <code>pJsonify</code> is true).
     *
     *
     * @param pValue value to extract from
     * @param pExtraArgs stack used for diving in to the value
     * @param pOpts options from which various processing
     *        parameters (like maxDepth, maxCollectionSize and maxObjects) are taken and put
     *        into context in order to influence the object traversal.
     * @param pJsonify whether the result should be returned as an JSON object
     * @return extracted value, either natively or as JSON
     * @throws AttributeNotFoundException if during traversal an attribute is not found as specified in the stack
     */
    private Object extractObjectWithContext(Object pValue, Deque<String> pExtraArgs, SerializeOptions pOpts, boolean pJsonify)
            throws AttributeNotFoundException {
        Object jsonResult;
        setupContext(pOpts);
        try {
            jsonResult = extractObject(pValue, pExtraArgs, pJsonify);
        } catch (ValueFaultHandler.AttributeFilteredException exp) {
            jsonResult = null;
        } finally {
            clearContext();
        }
        return jsonResult;
    }

    /**
     * Set an value of an inner object
     *
     * @param pInner the inner object
     * @param pAttribute the attribute to set
     * @param pValue the value to set
     * @return the old value
     * @throws IllegalAccessException if the reflection code fails during setting of the value
     * @throws InvocationTargetException reflection error
     */
    private Object setObjectValue(Object pInner, String pAttribute, Object pValue)
            throws IllegalAccessException, InvocationTargetException {

        // Call various handlers depending on the type of the inner object, as is extract Object

        Class<?> clazz = pInner.getClass();
        if (clazz.isArray()) {
            return arrayExtractor.setObjectValue(stringToObjectConverter,pInner,pAttribute,pValue);
        }
        Extractor extractor = getExtractor(clazz);

        if (extractor != null) {
            return extractor.setObjectValue(stringToObjectConverter,pInner,pAttribute,pValue);
        } else {
            throw new IllegalStateException(
                    "Internal error: No handler found for class " + clazz + " for setting object value." +
                    " (object: " + pInner + ", attribute: " + pAttribute + ", value: " + pValue + ")");
        }
    }

    // =================================================================================

    /**
     * Get the length of an extracted collection, but not larger than the configured limit.
     *
     * @param originalLength the original length
     * @return the original length if is smaller than then the configured maximum length. Otherwise, the
     *         maximum length is returned.
     */
    int getCollectionLength(int originalLength) {
        ObjectSerializationContext ctx = stackContextLocal.get();
        return ctx.getCollectionSizeTruncated(originalLength);
    }

    /**
     * Get the option for serializing long values.
     *
     * @return the option for serializing long values
     */
    String getSerializeLong() {
        ObjectSerializationContext ctx = stackContextLocal.get();
        return ctx.getSerializeLong();
    }

    /**
     * Get the fault handler used for dealing with exceptions during value extraction.
     *
     * @return the fault handler
     */
    public ValueFaultHandler getValueFaultHandler() {
        ObjectSerializationContext ctx = stackContextLocal.get();
        return ctx.getValueFaultHandler();
    }

    /**
     * Clear the context used for counting objects and limits
     */
    void clearContext() {
        stackContextLocal.remove();
    }

    /**
     * Set up the context with hard limits and defaults
     */
    void setupContext() {
        setupContext(new SerializeOptions.Builder().build());
    }

    /**
     * Set up the context with the limits given in the request or with the default limits if not. In all cases,
     * hard limits as defined in the servlet configuration are never exceeded.
     *
     * @param pOpts options used for parsing.
     */
    void setupContext(SerializeOptions pOpts) {
        ObjectSerializationContext stackContext = new ObjectSerializationContext(pOpts);
        stackContextLocal.set(stackContext);
    }

    // =================================================================================

    // Get the extractor for a certain class
    private Extractor getExtractor(Class<?> pClazz) {
        for (Extractor handler : extractors) {
            if (handler.canSetValue() && handler.getType() != null && handler.getType().isAssignableFrom(pClazz)) {
                return handler;
            }
        }
        return null;
    }

    private String checkForLimits(Object pValue, ObjectSerializationContext pStackContext) {
        if (pValue != null) {
            if (pStackContext.maxDepthReached()) {
                // We use its string representation.
                return pValue.toString();
            }
            if (pStackContext.alreadyVisited(pValue)) {
                return "[Reference " + pValue.getClass().getName() + "@" + Integer.toHexString(pValue.hashCode()) + "]";
            }
        }
        if (pStackContext.maxObjectsExceeded()) {
            return "[Object limit exceeded]";
        }
        return null;
    }



    private Object callHandler(Object pValue, Deque<String> pPathParts, boolean pJsonify)
            throws AttributeNotFoundException {
        Class<?> pClazz = pValue.getClass();
        for (Extractor handler : extractors) {
            if (handler.getType() != null && handler.getType().isAssignableFrom(pClazz)) {
                return handler.extractObject(this,pValue,pPathParts,pJsonify);
            }
        }
        throw new IllegalStateException(
                "Internal error: No handler found for class " + pClazz +
                    " (object: " + pValue + ", extraArgs: " + pPathParts + ")");
    }


    // Used for testing only. Hence final and package local
    ThreadLocal<ObjectSerializationContext> getStackContextLocal() {
        return stackContextLocal;
    }

    // Simplifiers are added either explicitly or by reflection from a subpackage
    private void addSimplifiers(List<Extractor> pHandlers) {
        // Add all
        List<Extractor> services = LocalServiceFactory.createServices(this.getClass().getClassLoader(),
            SIMPLIFIERS_DEFAULT_DEF, SIMPLIFIERS_DEF);

        if (LocalServiceFactory.validateServices(services, context)) {
            pHandlers.addAll(services);
        }
    }
}
