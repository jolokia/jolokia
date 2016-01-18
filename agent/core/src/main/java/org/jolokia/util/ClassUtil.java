package org.jolokia.util;

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

import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;

/**
 * Utility for class lookup.
 *
 * @author roland
 * @since 19.04.11
 */
public final class ClassUtil {

    private ClassUtil() {}

    /**
     * Lookup a class. See {@link ClassUtil#classForName(String, boolean,ClassLoader[])} for details. The class
     * gets initialized during lookup.
     *
     * @param pClassName name to lookup.
     * @return the class found or null if no class could be found.
     */
    public static <T> Class<T> classForName(String pClassName, ClassLoader ... pClassLoaders) {
        return classForName(pClassName, true, pClassLoaders);
    }

    /**
     * Load a certain class. Several class loader are tried: Fires the current thread's context
     * class loader, then its parents. If this doesn't work, the class loader which
     * loaded this class is used (and its parents)
     *
     * @param pClassName class name to load
     * @param pInitialize whether the class must be initialized
     * @param pClassLoaders optional class loaders which are tried as well
     * @return the class class found or null if no class could be loaded
     */
    public static <T> Class<T> classForName(String pClassName,boolean pInitialize,ClassLoader ... pClassLoaders) {
        Set<ClassLoader> tried = new HashSet<ClassLoader>();
        for (ClassLoader loader : findClassLoaders(pClassLoaders)) {
            // Go up the classloader stack to eventually find the server class. Sometimes the WebAppClassLoader
            // hide the server classes loaded by the parent class loader.
            while (loader != null) {
                try {
                    if (!tried.contains(loader)) {
                        return (Class<T>) Class.forName(pClassName, pInitialize, loader);
                    }
                } catch (ClassNotFoundException ignored) {}
                tried.add(loader);
                loader = loader.getParent();
            }
        }
        return null;
    }

    private static List<ClassLoader> findClassLoaders(ClassLoader... pClassLoaders) {
        List<ClassLoader> classLoadersToTry = new ArrayList<ClassLoader>(Arrays.asList(pClassLoaders));
        classLoadersToTry.add(Thread.currentThread().getContextClassLoader());
        classLoadersToTry.add(ClassUtil.class.getClassLoader());

        List<ClassLoader> ret = new ArrayList<ClassLoader>();
        Set<ClassLoader> visited = new HashSet<ClassLoader>();
        for (ClassLoader cll : classLoadersToTry) {
            if (cll != null && !visited.contains(cll)) {
                ret.add(cll);
                visited.add(cll);
            }
        }
        return ret;
    }

    /**
     * Get the given path as an input stream or return <code>null</code> if not found
     *
     * @param pPath path to lookup
     * @return input stream or null if not found.
     */
    public static InputStream getResourceAsStream(String pPath) {
        for (ClassLoader loader : new ClassLoader[] {
                Thread.currentThread().getContextClassLoader(),
                ClassUtil.class.getClassLoader()
        } ) {
            if (loader != null) {
                InputStream is = loader.getResourceAsStream(pPath);
                if (is != null) {
                    return is;
                }
            }
        }
        return null;
    }

    /**
     * Check for the existence of a given class
     *
     * @param pClassName class name to check
     * @return true if the class could be loaded by the thread's conext class loader, false otherwise
     */
    public static boolean checkForClass(String pClassName) {
        return ClassUtil.classForName(pClassName,false) != null;
    }

    /**
     * Instantiate an instance of the given class with its default constructor. The context class loader is used
     * to lookup the class
     *
     * @param pClassName name of class to instantiate
     * @param pArguments optional constructor arguments. Works only for objects with the same class as declared in
     *                   the constructor types (no subclasses)
     * @param <T> type object type
     * @return instantiated object
     * @throws IllegalArgumentException if the class could not be found or instantiated
     */
    public static <T> T newInstance(String pClassName, Object ... pArguments) {
        Class<T> clazz = classForName(pClassName);
        if (clazz == null) {
            throw new IllegalArgumentException("Cannot find " + pClassName);
        }
        return newInstance(clazz, pArguments);
    }

    /**
     * Instantiate an instance of the given class with its default constructor
     *
     * @param pClass class to instantiate
     * @param pArguments optional constructor arguments. Works only for objects with the same class as declared in
     *                   the constructor types (no subclasses)
     * @param <T> type object type
     * @return instantiated object
     * @throws IllegalArgumentException if the class could not be found or instantiated
     */
    public static <T> T newInstance(Class<T> pClass, Object ... pArguments) {
        try {
            if (pClass != null) {
                if (pArguments.length == 0) {
                    return pClass.newInstance();
                } else {
                    Constructor<T> ctr = lookupConstructor(pClass, pArguments);
                    return ctr.newInstance(pArguments);
                }
            } else {
                throw new IllegalArgumentException("Given class must not be null");
            }
        } catch (InstantiationException e) {
            throw new IllegalArgumentException("Cannot instantiate " + pClass + ": " + e,e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot instantiate " + pClass + ": " + e,e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot instantiate " + pClass + ": " + e,e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Cannot instantiate " + pClass + ": " + e,e);
        }
    }

    /**
     * Apply a method to a given object with optional arguments. The method is looked up the whole class
     * hierarchy.
     *
     * @param pObject object on which to apply the method
     * @param pMethod the method name
     * @param pArgs optional arguments
     * @return return value (if any)
     */
    public static Object applyMethod(Object pObject, String pMethod, Object ... pArgs) {
        Class<?> clazz = pObject.getClass();
        try {
            Method method = extractMethod(pMethod, clazz, pArgs);
            return method.invoke(pObject,pArgs);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot call method " + pMethod + " on " + pObject + ": " + e,e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException("Cannot call method " + pMethod + " on " + pObject + ": " + e,e);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot call method " + pMethod + " on " + pObject + ": " + e,e);
        }
    }

    /**
     * Get all resources from the classpath which are specified by the given path.
     *
     * @param pResource resource specification to use for lookup
     * @return the list or URLs to loookup
     */
    public static Set<String> getResources(String pResource) throws IOException {
        List<ClassLoader> clls = findClassLoaders();
        if (clls.size() != 0) {
            Set<String> ret = new HashSet<String>();
            for (ClassLoader cll : clls) {
                Enumeration<URL> urlEnum = cll.getResources(pResource);
                ret.addAll(extractUrlAsStringsFromEnumeration(urlEnum));
            }
            return ret;
        } else {
            return extractUrlAsStringsFromEnumeration(ClassLoader.getSystemResources(pResource));
        }
    }

    private static Set<String> extractUrlAsStringsFromEnumeration(Enumeration<URL> urlEnum) {
        Set<String> ret = new HashSet<String>();
        while (urlEnum.hasMoreElements()) {
            ret.add(urlEnum.nextElement().toExternalForm());
        }
        return ret;
    }

    // Lookup appropriate constructor
    private static <T> Constructor<T> lookupConstructor(Class<T> clazz, Object[] pArguments) throws NoSuchMethodException {
        Class[] argTypes = extractArgumentTypes(pArguments);
        return clazz.getConstructor(argTypes);
    }

    private static Method extractMethod(String pMethod, Class<?> clazz, Object[] pArgs) throws NoSuchMethodException {
        for (Method method : clazz.getMethods()) {
            if (!method.getName().equals(pMethod)) {
                continue;
            }
            Class[] parameters = method.getParameterTypes();
            if (parametersMatch(parameters, pArgs)) {
                return method;
            }
        }
        throw new NoSuchMethodException("No " + pMethod + " on " + clazz + " with " + pArgs.length + " arguments found ");
    }

    private static Class[] extractArgumentTypes(Object[] pArguments) {
        Class[] argTypes = new Class[pArguments.length];
        int i = 0;
        for (Object arg : pArguments) {
            argTypes[i++] = arg.getClass();
        }
        return argTypes;
    }

    private static boolean parametersMatch(Class[] parameters, Object[] pArgs) {
        if (parameters.length != pArgs.length) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (pArgs[i] == null) {
                continue;
            }
            Class argClass = pArgs[i].getClass();
            Class paramClass = parameters[i];
            if (!paramClass.isAssignableFrom(argClass)) {
                if (checkForPrimitive(argClass, paramClass)) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    private static boolean checkForPrimitive(Class argClass, Class paramClass) {
        return paramClass.isPrimitive() && PRIMITIVE_TO_OBJECT_MAP.get(paramClass.getName()) != null;
    }

    private static final Map<String,Class> PRIMITIVE_TO_OBJECT_MAP = new HashMap<String, Class>();

    static {
        PRIMITIVE_TO_OBJECT_MAP.put("int", Integer.TYPE);
        PRIMITIVE_TO_OBJECT_MAP.put("long", Long.TYPE);
        PRIMITIVE_TO_OBJECT_MAP.put("double", Double.TYPE);
        PRIMITIVE_TO_OBJECT_MAP.put("float", Float.TYPE);
        PRIMITIVE_TO_OBJECT_MAP.put("bool", Boolean.TYPE);
        PRIMITIVE_TO_OBJECT_MAP.put("char", Character.TYPE);
        PRIMITIVE_TO_OBJECT_MAP.put("byte", Byte.TYPE);
        PRIMITIVE_TO_OBJECT_MAP.put("void", Void.TYPE);
        PRIMITIVE_TO_OBJECT_MAP.put("short", Short.TYPE);
    }
}
