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
package org.jolokia.service.serializer.json;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.channels.Channel;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.management.AttributeNotFoundException;

import org.jolokia.json.JSONObject;
import org.jolokia.server.core.service.serializer.ValueFaultHandler;
import org.jolokia.server.core.util.EscapeUtil;
import org.jolokia.service.serializer.object.Converter;

/**
 * {@link ObjectAccessor} for plain Java objects - "beans". We should be very careful here, because we don't
 * want to access "classLoader/ucp/path/0/host" attribute of {@link Class} for example...
 *
 * @author roland
 * @since Apr 19, 2009
 */
public class BeanAccessor implements ObjectAccessor {

    /** Getters we will never return */
    private static final Set<String> IGNORED_GETTERS = Set.of(
        "getClass",
        // Omit internal stuff
        "getStackTrace",
        "getClassLoader"
    );

    /** Base types we will never return */
    private static final Class<?>[] IGNORED_RETURN_TYPES = new Class[]{
        OutputStream.class,
        InputStream.class,
        Writer.class,
        Reader.class,
        Socket.class,
        ServerSocket.class,
        Channel.class,
        System.class,
        Runtime.class,
        Process.class,
    };

    /**
     * Some classes which could be expanded as map of attributes, but we want plain to String conversion without
     * using dedicated extractors/simplifiers
     */
    private static final Set<Class<?>> NO_ATTRIBUTE_CLASSES = Set.of(
        URI.class,
        UUID.class,
        Locale.class
    );

    /**
     * We never serialize objects of some packages.
     */
    private static final Set<Package> IGNORED_PACKAGES = Set.of(
        java.lang.module.ModuleFinder.class.getPackage(),
        java.lang.ref.Reference.class.getPackage(),
        java.lang.reflect.Field.class.getPackage()
    );

    private static final String[] GETTER_PREFIX = new String[]{"get", "is", "has"};

    private static final List<AttributeAndMethod> NO_ATTRIBUTES = Collections.emptyList();

    // Over time we serialize more and more objects, but the set of classes is limited (though may not be super small)
    // Serialization of a list of 10000 objects of the same class should not lead to 10000x collection of getters
    private final Map<Class<?>, Map<String, AttributeAndMethod>> ATTRIBUTE_CACHE = Collections.synchronizedMap(new HashMap<>());

    /**
     * Cache ignored types, to avoid repeating calls to {@link Class#isAssignableFrom}. We don't expect this
     * cache to grow indefinitely - Jolokia serializes some objects (or object graphs) millions times, but these
     * objects use the same classes.
     */
    private final Set<Class<?>> CACHE_IGNORED = new HashSet<>();

    @Override
    public Class<?> getType() {
        return Object.class;
    }

    @Override
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue, Deque<String> pPathParts, boolean pJsonify)
            throws AttributeNotFoundException {
        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathPart != null) {
            // Still some path elements available, so dive deeper
            // get all attributes - cache or fresh
            this.collectValidBeanAttributes(pValue);
            Map<String, AttributeAndMethod> attributes = ATTRIBUTE_CACHE.get(pValue.getClass());
            AttributeAndMethod attr = attributes.get(pathPart);
            if (attr == null) {
                // special attr+method that will be handled in extractBeanPropertyValue
                attr = new AttributeAndMethod(pathPart, null);
            }
            Object attributeValue = extractBeanPropertyValue(pValue, attr, pConverter.getValueFaultHandler());
            return pConverter.extractObject(attributeValue, pPathParts, pJsonify);
        } else {
            return pJsonify ? objectToJSON(pConverter, pValue, pPathParts) : pValue;
        }
    }

    @Override
    public boolean canSetValue() {
        return true;
    }

    /**
     * <p>Set a bean property with the passed value - subject to conversion into the class of the setter for given
     * attribute.</p>
     *
     * <p>This method, differently than {@link #extractObject}, doesn't use cache and is also used (without
     * success) for primitive types that should not be altered.</p>
     *
     * @param pConverter {@link Converter} used to convert the value being set to a class of the accessed attribute
     * @param pObject    object on which to set the value
     * @param pAttribute attribute of the object to set. (For arrays or lists it should be an index.)
     * @param pValue     the new value to set after {@link Converter#convert conversion}
     * @return
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     */
    @Override
    public Object setObjectValue(Converter<String> pConverter, Object pObject, String pAttribute, Object pValue)
            throws IllegalAccessException, InvocationTargetException {
        String property = Character.toUpperCase(pAttribute.charAt(0)) + pAttribute.substring(1);
        // setter is required
        String setter = "set" + property;
        // getter is not required for write-only properties (quite common with JMX)
        String getter = "get" + property;

        Class<?> clazz = pObject.getClass();
        Method found = null;
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(setter)) {
                found = method;
                break;
            }
        }
        if (found == null) {
            throw new IllegalArgumentException(
                    "No Method " + setter + " known for object of type " + clazz.getName());
        }
        Class<?>[] params = found.getParameterTypes();
        if (params.length != 1) {
            throw new IllegalArgumentException(
                    "Invalid parameter signature for " + setter + " known for object of type "
                            + clazz.getName() + ". Setter must take exactly one parameter.");
        }
        Object oldValue;
        try {
            final Method getMethod = clazz.getMethod(getter);
            oldValue = getMethod.invoke(pObject);
        } catch (NoSuchMethodException exp) {
            // Ignored, we simply dont return an old value
            oldValue = null;
        }
        // convert the argument to proper class
        found.invoke(pObject, pConverter.convert(params[0].getName(), pValue));

        return oldValue;
    }

    /**
     * Serialize given {@link Object} into a JSON recursively, delegating to other accessors along the way
     *
     * @param pConverter
     * @param pValue
     * @param pPathParts
     * @return
     * @throws AttributeNotFoundException
     */
    private Object objectToJSON(ObjectToJsonConverter pConverter, Object pValue, Deque<String> pPathParts)
            throws AttributeNotFoundException {
        Class<?> clazz = pValue.getClass();

        // PrimitiveAccessor handled some basic types for which we don't collect attributes (like java.lang.Long)
        // so we can proceed with reflection. We'll always return JSONObject if there are any attributes

        // For the rest we build up a JSON map with the attributes as keys and the value are
        Collection<AttributeAndMethod> attributes = collectValidBeanAttributes(pValue);
        if (!attributes.isEmpty()) {
            return extractBeanPropertyValues(pConverter, pValue, pPathParts, attributes);
        } else {
            // the bean we're trying to serialize as JSON doesn't have any valid properties/attributes.
            // We'll attempt supported conversion then. Never call .toString() here!
            return pConverter.getConverter().convert(String.class.getName(), pValue);
        }
    }

    /**
     * Prepare a list of attributes we'll return as JSON representation of the object. Up to Jolokia 2.3.0
     * we were allowing non-public methods, but it's no longer the case.
     *
     * @param pValue
     * @return
     */
    private Collection<AttributeAndMethod> collectValidBeanAttributes(Object pValue) {
        Class<?> cls = pValue.getClass();

        Map<String, AttributeAndMethod> attributes = ATTRIBUTE_CACHE.get(cls);
        if (attributes != null) {
            return attributes.values();
        }

        // some classes for which we don't want to return any attribute
        if (NO_ATTRIBUTE_CLASSES.contains(cls)) {
            return NO_ATTRIBUTES;
        }

        // heavy processing

        attributes = new HashMap<>();

        for (Method method : cls.getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())
                && !IGNORED_GETTERS.contains(method.getName()) && !isIgnoredType(method.getReturnType())
                && !hasAnnotation(method, "java.beans.Transient")) {

                // we can potentially treat this method as a getter
                String name = method.getName();
                for (String pref : GETTER_PREFIX) {
                    if (name.startsWith(pref) && name.length() > pref.length() && method.getParameterTypes().length == 0) {
                        // this is a getX/isX/hasX
                        int len = pref.length();
                        char firstLetter = name.charAt(len);
                        // Only for getter compliant to the beans conventions (first letter after prefix is upper case)
                        if (Character.isUpperCase(firstLetter)) {
                            String attributeName = Character.toLowerCase(firstLetter) + name.substring(len + 1);
                            attributes.put(attributeName, new AttributeAndMethod(attributeName, method));
                        }
                    }
                }
            }
        }
        ATTRIBUTE_CACHE.put(cls, Collections.unmodifiableMap(attributes));

        return attributes.values();
    }

    /**
     * Extract selected <em>attributes</em>> of the object. Always return a {@link JSONObject}, with fields
     * corresponding to the attributes of the values. Path parts are processed for each of the value.
     *
     * @param pConverter
     * @param pValue
     * @param pPathParts
     * @param pAttributes
     * @return
     * @throws AttributeNotFoundException
     */
    private Object extractBeanPropertyValues(ObjectToJsonConverter pConverter, Object pValue, Deque<String> pPathParts, Collection<AttributeAndMethod> pAttributes)
            throws AttributeNotFoundException {
        Map<String, Object> ret = new JSONObject();
        for (AttributeAndMethod attribute : pAttributes) {
            Deque<String> path = new LinkedList<>(pPathParts);
            try {
                ret.put(attribute.name, extractBeanPropertyValueAsJSON(pConverter, pValue, attribute, path));
            } catch (ValueFaultHandler.AttributeFilteredException exp) {
                // Skip it since we are doing a path with wildcards, filtering out non-matching attrs.
           }
        }
        if (ret.isEmpty() && !pAttributes.isEmpty()) {
            // Ok, everything was filtered. Bubbling upwards ...
            throw new ValueFaultHandler.AttributeFilteredException();
        }
        return ret;
    }

    /**
     * Extract single bean property and convert it to JSON
     *
     * @param pConverter
     * @param pValue
     * @param pAttribute
     * @param pPathParts
     * @return
     * @throws AttributeNotFoundException
     */
    private Object extractBeanPropertyValueAsJSON(ObjectToJsonConverter pConverter, Object pValue, AttributeAndMethod pAttribute, Deque<String> pPathParts)
            throws AttributeNotFoundException {
        ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
        Object value = extractBeanPropertyValue(pValue, pAttribute, faultHandler);
        if (value == null) {
            if (!pPathParts.isEmpty()) {
                faultHandler.handleException(new AttributeNotFoundException(
                        "Cannot apply remaining path " + EscapeUtil.combineToPath(pPathParts) + " on null value"));
            }
            return null;
        } else if (value == pValue) {
            if (!pPathParts.isEmpty()) {
                faultHandler.handleException(new AttributeNotFoundException(
                        "Cannot apply remaining path " + EscapeUtil.combineToPath(pPathParts) + " on a cycle"));
            }
            // Break Cycle
            return "[this]";
        } else {
            // Call into the converted recursively for any object known.
            return pConverter.extractObject(value, pPathParts, true);
        }
    }

    /**
     * Extract single property of the bean - to be converted into JSON by the caller
     *
     * @param pValue
     * @param pAttribute
     * @param pFaultHandler
     * @return
     * @throws AttributeNotFoundException
     */
    private Object extractBeanPropertyValue(Object pValue, AttributeAndMethod pAttribute, ValueFaultHandler pFaultHandler)
            throws AttributeNotFoundException {
        Class<?> clazz = pValue.getClass();

        // Up to 2.3.0 we were trying the attribute name directly as method - no longer the case!
        if (pAttribute.method == null) {
            return pFaultHandler.handleException(new AttributeNotFoundException(
                    "No getter known for attribute \"" + pAttribute.name + "\" for class " + pValue.getClass().getName()));
        }

        try {
            // no longer call setAccessible - we need public methods after Jolokia 2.3.0!
//            method.setAccessible(true);
            return pAttribute.method.invoke(pValue);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return pFaultHandler.handleException(new IllegalStateException("Error while extracting " + pAttribute
                    + " from " + pValue,e));
        }
    }

    /**
     * <p>When serializing a <em>bean</em>, we should ignore properties of selected types. Some getter tend to have bad
     * side effects like nuking files etc. See Jetty FileResource.getOutputStream() as a bad example.</p>
     *
     * <p>This method is not necessarily cheap (since it called quite often), however it's necessary as a safety net.
     * (I messed up my complete local Maven repository only be serializing a Jetty ServletContext).</p>
     *
     * @param pType
     * @return
     */
    private boolean isIgnoredType(Class<?> pType) {
        if (CACHE_IGNORED.contains(pType) || (!pType.isPrimitive() && !pType.isArray() && IGNORED_PACKAGES.contains(pType.getPackage()))) {
            return true;
        }
        for (Class<?> type : IGNORED_RETURN_TYPES) {
            if (type.isAssignableFrom(pType)) {
                CACHE_IGNORED.add(pType);
                return true;
            }
        }
        return false;
    }

    private boolean hasAnnotation(Method method, String annotation) {
        for (Annotation anno : method.getAnnotations()) {
            if (anno.annotationType().getName().equals(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Attribute name and discovered getter {@link Method} to prevent endless reflection
     */
    private static final class AttributeAndMethod {
        final String name;
        final Method method;

        private AttributeAndMethod(String name, Method method) {
            this.name = name;
            this.method = method;
        }

        @Override
        public String toString() {
            return name + "/" + method.getReturnType().getName() + " " + method.getName() + "()";
        }
    }

}
