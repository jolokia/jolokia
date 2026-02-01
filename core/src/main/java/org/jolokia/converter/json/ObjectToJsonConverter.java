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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.AttributeNotFoundException;

import org.jolokia.converter.json.simplifier.SimplifierAccessor;
import org.jolokia.converter.object.Converter;
import org.jolokia.converter.object.ObjectToObjectConverter;
import org.jolokia.converter.object.ObjectToOpenTypeConverter;
import org.jolokia.core.config.CoreConfiguration;
import org.jolokia.core.service.serializer.SerializeOptions;
import org.jolokia.core.service.serializer.ValueFaultHandler;
import org.jolokia.core.util.EscapeUtil;
import org.jolokia.core.util.LocalServiceFactory;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;

/**
 * <p>A converter/serializer that transforms Java objects (JMX attribute values, JMX operation parameters
 * and return values) into a JSON representation. It delegates handling of supported types to
 * {@link ObjectAccessor} implementations.</p>
 *
 * <p>Each {@link ObjectAccessor} gets a reference to this converter object so it can use it for a recursive
 * serialization of nested objects.</p>
 *
 * <p>Additionally (since the dawn of Jolokia) this converter is used to set <em>inner</em> values of any objects.
 * Also by delegating to extractors which can handle particular object types. For example {@link DateAccessor}
 * can call {@link Date#setTime(long)}.</p>
 *
 * @author roland
 * @since Apr 19, 2009
 */
public final class ObjectToJsonConverter {

    // Definition of simplifiers
    private static final String SIMPLIFIERS_DEFAULT_DEF = "META-INF/jolokia/simplifiers-default";
    private static final String SIMPLIFIERS_DEF         = "META-INF/jolokia/simplifiers";

    private static final Deque<String> EMPTY_DEQUE = new LinkedList<>();

    /**
     * Classes of the objects that can be returned directly as proper JSON types. All other objects <em>need to</em>
     * be converted in some way to proper JSON objects.
     */
    public static final Set<Class<?>> JSON_TYPES = Set.of(
        String.class,
        Boolean.TYPE,
        Boolean.class,
        Long.TYPE,
        Long.class,
        BigInteger.class,
        BigDecimal.class,
        JSONArray.class,
        JSONObject.class
    );

    /**
     * Classes of the objects that can be returned directly as proper JSON types and can't be used with
     * extra "path" do return part of the value.
     */
    public static final Set<Class<?>>JSON_BASIC_TYPES = Set.of(
        String.class,
        Boolean.TYPE,
        Boolean.class,
        Long.TYPE,
        Long.class,
        BigInteger.class,
        BigDecimal.class
    );

    /**
     * Simple types which are not proper JSON types (even if they seem to) but can be easily converted.
     */
    public static final Map<Class<?>, String> JSON_CONVERSIONS = Map.ofEntries(
        Map.entry(Character.class, String.class.getName()),
        Map.entry(Character.TYPE, String.class.getName()),
        Map.entry(Float.class, BigDecimal.class.getName()),
        Map.entry(Float.TYPE, BigDecimal.class.getName()),
        Map.entry(Double.class, BigDecimal.class.getName()),
        Map.entry(Double.TYPE, BigDecimal.class.getName()),
        Map.entry(Byte.class, Long.class.getName()),
        Map.entry(Byte.TYPE, Long.class.getName()),
        Map.entry(Short.class, Long.class.getName()),
        Map.entry(Short.TYPE, Long.class.getName()),
        Map.entry(Integer.class, Long.class.getName()),
        Map.entry(Integer.TYPE, Long.class.getName())
    );

    // Thread-Local set in order to prevent infinite recursions
    private final ThreadLocal<ObjectSerializationContext> stackContextLocal = new ThreadLocal<>();

    // List of dedicated extractors used for inner values
    private final List<ObjectAccessor> objectAccessors = new ArrayList<>();

    // Separate ArrayExtractor to handle arrays
    private final ArrayAccessor arrayAccessor;

    // Separate SimpleAccessor to handle objects that should go through straight conversion instead of
    // "extraction". No access to internal attributes
    private final SimpleAccessor simpleAccessor;

    // Used for converting strings to objects - only when setting attributes
    // see: https://github.com/jolokia/jolokia/issues/888#issuecomment-3278472424
    private final Converter<String> objectToObjectConverter;

    // Cache for actual accessors. Because we do class.isAssignableFrom(), it's better to keep the resolved
    // accessors for quick access
    private final Map<Class<?>, ObjectAccessor> ACCESSORS_CACHE = Collections.synchronizedMap(new HashMap<>());

    /**
     * Create a converter that can serialize objects into JSON representation
     *
     * @param pObjectToObjectConverter   used when setting values
     * @param pObjectToOpenTypeConverter used when converting {@link javax.management.openmbean.TabularData}
     * @param coreConfig                   {@link CoreConfiguration} used to load {@link ObjectAccessor} services
     */
    public ObjectToJsonConverter(ObjectToObjectConverter pObjectToObjectConverter, ObjectToOpenTypeConverter pObjectToOpenTypeConverter,
                                 CoreConfiguration coreConfig) {
        // TabularDataExtractor must be before MapExtractor
        // since javax.management.openmbean.TabularDataSupport implements java.util.Map interface
        // (while javax.management.openmbean.TabularData doesn't)
        objectAccessors.add(new TabularDataAccessor(pObjectToOpenTypeConverter));
        objectAccessors.add(new CompositeDataAccessor());

        objectAccessors.add(new MapAccessor());

        // Collection accessors
        // ListExtractor before CollectionExtractor, because we can set values only for List, not Set
        objectAccessors.add(new ListAccessor());
        objectAccessors.add(new org.jolokia.converter.json.CollectionAccessor());

        // discoverable simplifiers
        List<SimplifierAccessor<?>> simplifiers = new ArrayList<>();
        loadSimplifiers(simplifiers, coreConfig);
        objectAccessors.addAll(simplifiers);

        // if the discovered simplifier supports toString conversion, be ready to pass that to other converters
        // List of ObjectAccessors, which supports explicit to String conversion
        // but we need to add the discovered ones later, to override the built-in ones
        Map<Class<?>, ObjectAccessor> stringConverters = new HashMap<>();

        // Enum handling
        EnumObjectAccessor enumAccessor = new EnumObjectAccessor();
        objectAccessors.add(enumAccessor);
        stringConverters.put(enumAccessor.getType(), enumAccessor);

        // Special date/calendar/temporal handling with configurable patterns
        DateFormatConfiguration dfc = new DateFormatConfiguration(coreConfig);
        DateAccessor dateAccessor = new DateAccessor(dfc);
        CalendarAccessor calendarAccessor = new CalendarAccessor(dfc);
        JavaTimeTemporalAccessor temporalAccessor = new JavaTimeTemporalAccessor(dfc);
        objectAccessors.add(dateAccessor);
        objectAccessors.add(calendarAccessor);
        objectAccessors.add(temporalAccessor);
        // these are know to support toString conversion
        stringConverters.put(dateAccessor.getType(), dateAccessor);
        stringConverters.put(calendarAccessor.getType(), calendarAccessor);
        stringConverters.put(temporalAccessor.getType(), temporalAccessor);

        // override the built-in accessors that are also String converters with the custom ones
        for (SimplifierAccessor<?> simplifier : simplifiers) {
            if (simplifier.supportsStringConversion()) {
                stringConverters.put(simplifier.getType(), simplifier);
            }
        }

        // to prevent bean/reflection access to types like Long, BigDecimal or String - these should not go to
        // BeanAccessor
        simpleAccessor = new SimpleAccessor();

        // Must be treated as the fallback accessor, which uses default algorithm to access "bean" properties
        // with various protection rules (for security reasons)
        objectAccessors.add(new BeanAccessor());

        arrayAccessor = new ArrayAccessor();

        // this is a two-way dependency, we should be careful, it's not perfect, but we can avoid
        // duplication and leverage discoverability
        if (pObjectToObjectConverter != null) {
            pObjectToObjectConverter.setStringConverters(stringConverters);
        }
        objectToObjectConverter = pObjectToObjectConverter;
    }

    /**
     * Convert the return value to a JSON object.
     *
     * @param pValue     the value to convert
     * @param pPathParts path parts to use for extraction
     * @param pOptions   options used for parsing
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
     * @param pNewValue    the value to set
     * @param pPathParts   the path within the outer object. This object will be modified and must be a modifiable list.
     * @return the old value
     * @throws AttributeNotFoundException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    public Object setInnerValue(Object pOuterObject, Object pNewValue, List<String> pPathParts)
            throws AttributeNotFoundException, IllegalAccessException, InvocationTargetException {
        String lastPathElement = pPathParts.remove(pPathParts.size()-1);
        Deque<String> extraStack = EscapeUtil.reversePath(pPathParts);

        // Get the object pointed to do with path parts excluding the last path element
        // We are using no limits here, since a path must have been given (see above), and hence we should
        // be safe anyway as there's no conversion to JSON
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
     * @param pValue     value to extract from
     * @param pPathParts stack for diving into the object
     * @param pJsonify   whether a JSON representation {@link org.jolokia.json.JSONObject}
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
                        new AttributeNotFoundException("Cannot apply a path to a null value"));
            }

            stackContext.push(pValue);
            Class<?> cls = pValue.getClass();
            if (cls.isArray()) {
                // Special handling for arrays
                return arrayAccessor.extractObject(this, pValue, pathStack, pJsonify);
            }
            if (cls.isPrimitive() || JSON_BASIC_TYPES.contains(cls) || JSON_CONVERSIONS.containsKey(cls)) {
                // Special "don't drill into" classes that should be handled by PrimitiveAccessor
                if (!pathStack.isEmpty()) {
                    // skip any attribute which we never get from these values
                    throw new ValueFaultHandler.AttributeFilteredException();
                }
                return simpleAccessor.extractObject(this, pValue, null, pJsonify);
            }
            return invokeAccessor(pValue, pathStack, pJsonify);
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
     * @param pValue     value to extract from
     * @param pExtraArgs stack used for diving in to the value
     * @param pOpts      options from which various processing
     *                   parameters (like maxDepth, maxCollectionSize and maxObjects) are taken and put
     *                   into context in order to influence the object traversal.
     * @param pJsonify   whether the result should be returned as an JSON object
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
     * @param pInner     the inner object
     * @param pAttribute the attribute to set
     * @param pValue     the value to set
     * @return the old value
     * @throws IllegalAccessException    if the reflection code fails during setting of the value
     * @throws InvocationTargetException reflection error
     */
    private Object setObjectValue(Object pInner, String pAttribute, Object pValue)
            throws IllegalAccessException, InvocationTargetException {

        // Call various accessors depending on the type of the inner object, as is extract Object

        Class<?> clazz = pInner.getClass();
        if (clazz.isArray()) {
            return arrayAccessor.setObjectValue(objectToObjectConverter, pInner, pAttribute, pValue);
        }
        // primitive values and objects like java.lang.Integer will still be handled by BeanAccessor
        // but that's destined to fail. setObjectValue is called MUCH less often than serialize, so
        // we skip cache and optimization
        ObjectAccessor objectAccessor = getWriteAccessor(clazz);

        if (objectAccessor != null) {
            return objectAccessor.setObjectValue(objectToObjectConverter, pInner, pAttribute, pValue);
        } else {
            throw new IllegalStateException(
                "Internal error: No ObjectAccessor found for class " + clazz + " for setting object value." +
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
     * {@link ObjectAccessor} may need to access the converter (like {@link MapAccessor} for key conversion)
     *
     * @return
     */
    Converter<String> getConverter() {
        return this.objectToObjectConverter;
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

    /**
     * Get {@link ObjectAccessor} for specific class which supports writing
     *
     * @param pClazz
     * @return
     */
    private ObjectAccessor getWriteAccessor(Class<?> pClazz) {
        ObjectAccessor accessor = cachedAccessor(pClazz);
        if (accessor != null && accessor.canSetValue()) {
            return accessor;
        }
        return null;
    }

    /**
     * Find and call {@link ObjectAccessor} for specific class. If not found, {@link IllegalStateException}
     * is thrown.
     *
     * @param pValue
     * @param pPathParts
     * @param pJsonify
     * @return
     * @throws AttributeNotFoundException
     */
    private Object invokeAccessor(Object pValue, Deque<String> pPathParts, boolean pJsonify)
        throws AttributeNotFoundException {
        Class<?> cls = pValue.getClass();
        ObjectAccessor accessor = cachedAccessor(cls);
        if (accessor != null) {
            return accessor.extractObject(this, pValue, pPathParts, pJsonify);
        }
        throw new IllegalStateException("Internal error: No ObjectAccessor found for class " + cls);
    }

    /**
     * Get {@link ObjectAccessor} from cache for given class.
     *
     * @param pClazz
     * @return
     */
    private ObjectAccessor cachedAccessor(Class<?> pClazz) {
        ObjectAccessor found = ACCESSORS_CACHE.get(pClazz);
        if (found != null) {
            return found;
        }

        for (ObjectAccessor accessor : objectAccessors) {
            if (accessor.getType() != null && accessor.getType().isAssignableFrom(pClazz)) {
                ACCESSORS_CACHE.put(pClazz, accessor);
                return accessor;
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

    // Used for testing only. Hence final and package local
    ThreadLocal<ObjectSerializationContext> getStackContextLocal() {
        return stackContextLocal;
    }

    private void loadSimplifiers(List<SimplifierAccessor<?>> pSimplifiers, CoreConfiguration coreConfig) {
        List<SimplifierAccessor<?>> services = LocalServiceFactory.createServices(this.getClass().getClassLoader(),
            SIMPLIFIERS_DEFAULT_DEF, SIMPLIFIERS_DEF);

        // no need to validate SimplifierAccessor "services", because these are not implementing JolokiaService
        pSimplifiers.addAll(services);
    }

}
