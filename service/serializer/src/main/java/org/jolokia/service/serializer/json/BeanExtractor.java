package org.jolokia.service.serializer.json;

import java.io.OutputStream;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

import javax.management.AttributeNotFoundException;

import org.jolokia.service.serializer.object.StringToObjectConverter;
import org.jolokia.server.core.service.serializer.ValueFaultHandler;
import org.jolokia.server.core.util.EscapeUtil;

import org.jolokia.json.JSONObject;

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
 * Extractor for plain Java objects.
 *
 * @author roland
 * @since Apr 19, 2009
 */
public class BeanExtractor implements Extractor {

    private static final Set<Class<?>> FINAL_CLASSES = new HashSet<>(Arrays.asList(
            String.class,
            Number.class,
            Byte.class,
            Double.class,
            Float.class,
            Long.class,
            Short.class,
            Integer.class,
            Boolean.class
    ));

    private static final Set<String> IGNORE_METHODS = new HashSet<>(Arrays.asList(
            "getClass",
            // Ommit internal stuff
            "getStackTrace",
            "getClassLoader"
    ));

    @SuppressWarnings("rawtypes")
    private static final Class[] IGNORED_RETURN_TYPES = new Class[]{
            OutputStream.class,
            Writer.class
    };

    private static final String[] GETTER_PREFIX = new String[]{"get", "is", "has"};

    /** {@inheritDoc} */
    public Class<?> getType() {
        return Object.class;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue,
                                Deque<String> pPathParts, boolean jsonify)
            throws AttributeNotFoundException {
        // Wrap fault handler if a wildcard path pattern is present
        ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
        String pathPart = pPathParts.isEmpty() ? null : pPathParts.pop();
        if (pathPart != null) {
            // Still some path elements available, so dive deeper
            Object attributeValue = extractBeanPropertyValue(pValue, pathPart, faultHandler);
            return pConverter.extractObject(attributeValue, pPathParts, jsonify);
        } else {
            if (jsonify) {
                // We need the jsonfied value from here on.
                return exctractJsonifiedValue(pConverter, pValue, pPathParts);
            } else {
                // No jsonification requested, hence we are returning the object itself
                return pValue;
            }
        }
    }

    // Using standard set semantics
    /** {@inheritDoc} */
    public Object setObjectValue(StringToObjectConverter pConverter,Object pInner, String pAttribute, Object pValue)
            throws IllegalAccessException, InvocationTargetException {
        // Move this to plain object handler
        String rest = new StringBuilder(pAttribute.substring(0,1).toUpperCase())
                .append(pAttribute.substring(1)).toString();
        String setter = new StringBuilder("set").append(rest).toString();
        String getter = new StringBuilder("get").append(rest).toString();

        Class<?> clazz = pInner.getClass();
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
            AccessController.doPrivileged(new SetMethodAccessibleAction(getMethod));
            oldValue = getMethod.invoke(pInner);
        } catch (NoSuchMethodException exp) {
            // Ignored, we simply dont return an old value
            oldValue = null;
        }
        AccessController.doPrivileged(new SetMethodAccessibleAction(found));
        found.invoke(pInner,pConverter.deserialize(params[0].getName(), pValue));
        return oldValue;
    }

    /** {@inheritDoc} */
    public boolean canSetValue() {
        return true;
    }

    // =====================================================================================================

    private Object exctractJsonifiedValue(ObjectToJsonConverter pConverter, Object pValue, Deque<String> pPathParts)
            throws AttributeNotFoundException {
        Class<?> pClazz = pValue.getClass();
        if (pClazz.isPrimitive() || FINAL_CLASSES.contains(pClazz) || pClazz == BigDecimal.class || pClazz == BigInteger.class) {
            // No further diving, use these directly
            if (pClazz.equals(Long.class) && "string".equals(pConverter.getSerializeLong())) {
                // Long value can exceed max safe integer in JS, so convert it to
                // a string when the option is specified
                return pValue.toString();
            } else {
                return pValue;
            }
        } else {
            // For the rest we build up a JSON map with the attributes as keys and the value are
            List<String> attributes = extractBeanAttributes(pValue);
            if (!attributes.isEmpty()) {
                return extractBeanValues(pConverter, pValue, pPathParts, attributes);
            } else {
                // No further attributes, return string representation
                return pValue.toString();
            }
        }
    }

    private Object extractBeanValues(ObjectToJsonConverter pConverter, Object pValue, Deque<String> pPathParts, List<String> pAttributes) throws AttributeNotFoundException {
        Map<String, Object> ret = new JSONObject();
        for (String attribute : pAttributes) {
            Deque<String> path = new LinkedList<>(pPathParts);
            try {
                ret.put(attribute, extractJsonifiedPropertyValue(pConverter, pValue, attribute, path));
            } catch (ValueFaultHandler.AttributeFilteredException exp) {
                // Skip it since we are doing a path with wildcards, filtering out non-matchin attrs.
           }
        }
        if (ret.isEmpty() && !pAttributes.isEmpty()) {
            // Ok, everything was filtered. Bubbling upwards ...
            throw new ValueFaultHandler.AttributeFilteredException();
        }
        return ret;
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private Object extractJsonifiedPropertyValue(ObjectToJsonConverter pConverter, Object pValue, String pAttribute, Deque<String> pPathParts)
            throws AttributeNotFoundException {
        ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
        Object value = extractBeanPropertyValue(pValue, pAttribute, faultHandler);
        if (value == null) {
            if (!pPathParts.isEmpty()) {
                faultHandler.handleException(new AttributeNotFoundException(
                        "Cannot apply remaining path " + EscapeUtil.combineToPath(pPathParts) + " on value null"));
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
            return pConverter.extractObject(value, pPathParts, true /* jsonify */);
        }
    }

    // Extract all attributes from a given bean
    private List<String> extractBeanAttributes(Object pValue) {
        List<String> attrs = new ArrayList<>();
        for (Method method : pValue.getClass().getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) &&
                !IGNORE_METHODS.contains(method.getName()) &&
                !isIgnoredType(method.getReturnType()) &&
                !hasAnnotation(method, "java.beans.Transient")) {
                addAttributes(attrs, method);
            }
        }
        return attrs;
    }

    private boolean hasAnnotation(Method method, String annotation) {
        for (Annotation anno : method.getAnnotations()) {
            if (anno.annotationType().getName().equals(annotation)) {
                return true;
            }
        }
        return false;
    }

    // Add attributes, which are taken from get methods to the given list
    @SuppressWarnings("PMD.UnnecessaryCaseChange")
    private void addAttributes(List<String> pAttrs, Method pMethod) {
        String name = pMethod.getName();
        for (String pref : GETTER_PREFIX) {
            if (name.startsWith(pref) && name.length() > pref.length()
                    && pMethod.getParameterTypes().length == 0) {
                int len = pref.length();
                String firstLetter = name.substring(len,len+1);
                // Only for getter compliant to the beans conventions (first letter after prefix is upper case)
                if (firstLetter.toUpperCase().equals(firstLetter)) {
                    String attribute =
                            new StringBuffer(firstLetter.toLowerCase()).
                                    append(name.substring(len+1)).toString();
                    pAttrs.add(attribute);
                }
            }
        }
    }

    private Object extractBeanPropertyValue(Object pValue, String pAttribute, ValueFaultHandler pFaultHandler)
            throws AttributeNotFoundException {
        Class<?> clazz = pValue.getClass();

        Method method = null;

        String suffix = new StringBuilder(pAttribute.substring(0,1).toUpperCase()).append(pAttribute.substring(1)).toString();
        for (String pref : GETTER_PREFIX) {
            try {
                String methodName = new StringBuilder(pref).append(suffix).toString();
                method = clazz.getMethod(methodName);
            } catch (NoSuchMethodException e) {
                // Try next one
                continue;
            }
            // We found a valid method
            break;
        }
        // Finally, try the attribute name directly
        if (method == null) {
            try {
                method = clazz.getMethod(new StringBuilder(pAttribute.substring(0,1).toLowerCase())
                        .append(pAttribute.substring(1)).toString());
            } catch (NoSuchMethodException exp) {
                method = null;
            }
        }
        if (method == null) {
            return pFaultHandler.handleException(new AttributeNotFoundException(
                    "No getter known for attribute " + pAttribute + " for class " + pValue.getClass().getName()));
        }
        try {
            method.setAccessible(true);
            return method.invoke(pValue);
        } catch (IllegalAccessException | InvocationTargetException e) {
            return pFaultHandler.handleException(new IllegalStateException("Error while extracting " + pAttribute
                    + " from " + pValue,e));
        }
    }

    /**
     * Privileged action for setting the accesibility mode for a method to true
     */
    private static class SetMethodAccessibleAction implements PrivilegedAction<Void> {

        private final Method getMethod;
        /**
         * Which method to set accessible
         *
         * @param pMethod  method to set accessible
         */
        public SetMethodAccessibleAction(Method pMethod) {
            getMethod = pMethod;
        }

        /** {@inheritDoc} */
        public Void run() {
            getMethod.setAccessible(true);
            return null;
        }

    }
    // Ignore certain return types, since their getter tend to have bad
    // side effects like nuking files etc. See Jetty FileResource.getOutputStream() as a bad example
    // This method is not necessarily cheap (since it called quite often), however necessary
    // as safety net. I messed up my complete local Maven repository only be serializing a Jetty ServletContext
    private boolean isIgnoredType(Class<?> pReturnType) {
        for (Class<?> type : IGNORED_RETURN_TYPES) {
            if (type.isAssignableFrom(pReturnType)) {
                return true;
            }
        }
        return false;
    }
}
