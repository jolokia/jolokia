package org.jolokia.converter.json;

import java.io.OutputStream;
import java.io.Writer;
import java.lang.reflect.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

import javax.management.AttributeNotFoundException;

import org.jolokia.converter.object.StringToObjectConverter;
import org.json.simple.JSONAware;
import org.json.simple.JSONObject;

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

    private static final Set<Class> FINAL_CLASSES = new HashSet<Class>(Arrays.asList(
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

    private static final Set<String> IGNORE_METHODS = new HashSet<String>(Arrays.asList(
            "getClass",
            // Ommit internal stuff
            "getStackTrace",
            "getClassLoader"
    ));

    private static final Class[] IGNORED_RETURN_TYPES = new Class[]{
            OutputStream.class,
            Writer.class
    };

    private static final String[] GETTER_PREFIX = new String[]{"get", "is", "has"};

    /** {@inheritDoc} */
    public Class getType() {
        return Object.class;
    }

    /** {@inheritDoc} */
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    public Object extractObject(ObjectToJsonConverter pConverter, Object pValue,
                                Stack<String> pExtraArgs, boolean jsonify)
            throws AttributeNotFoundException {
        ValueFaultHandler faultHandler = pConverter.getValueFaultHandler();
        if (!pExtraArgs.isEmpty()) {
            // Still some path elements available, so dive deeper
            String attribute = pExtraArgs.pop();
            Object attributeValue = extractBeanPropertyValue(pValue, attribute, faultHandler);
            return pConverter.extractObject(attributeValue, pExtraArgs, jsonify);
        } else {
            if (jsonify) {
                // We need the jsonfied value from here on.
                return exctractJsonifiedValue(pValue, pExtraArgs, pConverter, faultHandler);
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
        String rest = new StringBuffer(pAttribute.substring(0,1).toUpperCase())
                .append(pAttribute.substring(1)).toString();
        String setter = new StringBuffer("set").append(rest).toString();
        String getter = new StringBuffer("get").append(rest).toString();

        Class clazz = pInner.getClass();
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
        Class params[] = found.getParameterTypes();
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
        found.invoke(pInner,pConverter.prepareValue(params[0].getName(), pValue));
        return oldValue;
    }

    /** {@inheritDoc} */
    public boolean canSetValue() {
        return true;
    }

    // =====================================================================================================

    private Object exctractJsonifiedValue(Object pValue, Stack<String> pExtraArgs,
                                          ObjectToJsonConverter pConverter, ValueFaultHandler pFaultHandler)
            throws AttributeNotFoundException {
        if (pValue.getClass().isPrimitive() || FINAL_CLASSES.contains(pValue.getClass()) || pValue instanceof JSONAware) {
            // No further diving, use these directly
            return pValue;
        } else {
            // For the rest we build up a JSON map with the attributes as keys and the value are
            List<String> attributes = extractBeanAttributes(pValue);
            if (attributes != null && attributes.size() > 0) {
                Map ret = new JSONObject();
                for (String attribute : attributes) {
                    ret.put(attribute, extractJsonifiedPropertyValue(pValue, attribute, pExtraArgs, pConverter, pFaultHandler));
                }
                return ret;
            } else {
                // No further attributes, return string representation
                return pValue.toString();
            }
        }
    }

    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private Object extractJsonifiedPropertyValue(Object pValue, String pAttribute, Stack<String> pExtraArgs,
                                                  ObjectToJsonConverter pConverter, ValueFaultHandler pFaultHandler)
            throws AttributeNotFoundException {
        Object value = extractBeanPropertyValue(pValue, pAttribute, pFaultHandler);
        if (value == null) {
            return null;
        } else if (value == pValue) {
            // Break Cycle
            return "[this]";
        } else {
            // Call into the converted recursively for any object known.
            return pConverter.extractObject(value, pExtraArgs, true /* jsonify */);
        }
    }

    // Extract all attributes from a given bean
    private List<String> extractBeanAttributes(Object pValue) {
        List<String> attrs = new ArrayList<String>();
        for (Method method : pValue.getClass().getMethods()) {
            if (!Modifier.isStatic(method.getModifiers()) &&
                !IGNORE_METHODS.contains(method.getName()) &&
                !isIgnoredType(method.getReturnType())) {
                addAttributes(attrs, method);
            }
        }
        return attrs;
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
        Class clazz = pValue.getClass();

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
        } catch (IllegalAccessException e) {
            return pFaultHandler.handleException(new IllegalStateException("Error while extracting " + pAttribute
                    + " from " + pValue,e));
        } catch (InvocationTargetException e) {
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
