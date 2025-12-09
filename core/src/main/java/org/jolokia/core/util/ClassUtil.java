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
package org.jolokia.core.util;

import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;

import org.jolokia.core.api.LogHandler;

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
    @SuppressWarnings("unchecked")
    public static <T> Class<T> classForName(String pClassName,boolean pInitialize,ClassLoader ... pClassLoaders) {
        if (pClassName == null) {
            return null;
        }
        Set<ClassLoader> tried = new HashSet<>();
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
        List<ClassLoader> classLoadersToTry = new ArrayList<>(Arrays.asList(pClassLoaders));
        classLoadersToTry.add(Thread.currentThread().getContextClassLoader());
        classLoadersToTry.add(ClassUtil.class.getClassLoader());

        List<ClassLoader> ret = new ArrayList<>();
        Set<ClassLoader> visited = new HashSet<>();
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
                    return pClass.getConstructor().newInstance();
                } else {
                    Constructor<T> ctr = lookupConstructor(pClass, pArguments);
                    return ctr.newInstance(pArguments);
                }
            } else {
                throw new IllegalArgumentException("Given class must not be null");
            }
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new IllegalArgumentException("Cannot instantiate " + pClass + ": " + e,e);
        }
    }

    public static LogHandler newLogHandlerInstance(String pLogHandlerClass, String pLogHandlerName, boolean pIsDebug) {
        Class<LogHandler> cl = ClassUtil.classForName(pLogHandlerClass);
        if (cl != null) {
            Constructor<? extends LogHandler> bestMatch = findBestLogHandlerConstructor(cl);
            if (bestMatch != null) {
                try {
                    switch (bestMatch.getParameterCount()) {
                        case 2: {
                            Object[] args = new Object[2];
                            if (bestMatch.getParameters()[0].getType() == String.class) {
                                args[0] = pLogHandlerName;
                                args[1] = pIsDebug;
                            } else {
                                args[0] = pIsDebug;
                                args[1] = pLogHandlerName;
                            }
                            return bestMatch.newInstance(args);
                        }
                        case 1: {
                            if (bestMatch.getParameters()[0].getType() == String.class) {
                                return bestMatch.newInstance(pLogHandlerName);
                            } else {
                                return bestMatch.newInstance(pIsDebug);
                            }
                        }
                        case 0:
                        default:
                            return bestMatch.newInstance();
                    }
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalArgumentException(e.getMessage(), e);
                }
            } else {
                throw new IllegalArgumentException("Can't create log handler for class " + pLogHandlerClass);
            }
        }
        return null;
    }

    private static Constructor<? extends LogHandler> findBestLogHandlerConstructor(Class<? extends LogHandler> cl) {
        // check constructors, prefer:
        // 1a. String, boolean
        // 1b. boolean, String
        // 2. String
        // 3. boolean
        // 4. no-arg
        // x. boolean is preferred over Boolean
        @SuppressWarnings("unchecked")
        Constructor<? extends LogHandler>[] ctors = (Constructor<? extends LogHandler>[]) cl.getConstructors();
        return Arrays.stream(ctors).filter(c -> {
            int pc = c.getParameterCount();
            if (pc == 0) {
                return true;
            }
            if (pc == 1) {
                return c.getParameters()[0].getType() == String.class
                        || c.getParameters()[0].getType() == Boolean.class
                        || c.getParameters()[0].getType() == Boolean.TYPE;
            }
            if (pc == 2) {
                boolean hasString = c.getParameters()[0].getType() == String.class
                        || c.getParameters()[1].getType() == String.class;
                boolean hasBoolean = c.getParameters()[0].getType() == Boolean.class
                        || c.getParameters()[0].getType() == Boolean.TYPE
                        || c.getParameters()[1].getType() == Boolean.class
                        || c.getParameters()[1].getType() == Boolean.TYPE;
                return hasString && hasBoolean;
            }
            return false;
        }).min((c1, c2) -> {
            if (c1.getParameterCount() != c2.getParameterCount()) {
                return c1.getParameterCount() > c2.getParameterCount() ? -1 : 1;
            }
            if (c1.getParameterCount() == 2) {
                // prefer (String, [Bb]oolean) over ([Bb]oolean, String)
                // prefer (String, boolean) over (String, Boolean)
                Class<?> t01 = c1.getParameters()[0].getType();
                Class<?> t02 = c2.getParameters()[0].getType();
                Class<?> t11 = c1.getParameters()[1].getType();
                Class<?> t12 = c2.getParameters()[1].getType();
                if (t01 == String.class && t02 != String.class) {
                    return -1;
                }
                if (t01 != String.class && t02 == String.class) {
                    return 1;
                }
                if (t01 == Boolean.TYPE && t02 == Boolean.class) {
                    return -1;
                }
                return t11 == Boolean.TYPE && t12 == Boolean.class ? -1 : 1;
            }
            if (c1.getParameterCount() == 1) {
                // prefer (String) over (boolean)
                Class<?> t01 = c1.getParameters()[0].getType();
                Class<?> t02 = c2.getParameters()[0].getType();
                if (t01 == String.class) {
                    return -1;
                }
                if (t02 == String.class) {
                    return 1;
                }
                return t01 == Boolean.TYPE ? -1 : 1;
            }

            // weird, but we can't really determine...
            return 0;
        }).orElse(null);
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
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot call method " + pMethod + " on " + pObject + ": " + e,e);
        }
    }

    /**
     * Get all resources from the classpath which are specified by the given path.
     *
     * @param pResource resource specification to use for lookup
     * @param pClassLoaders
     * @return the list or URLs to loookup
     */
    public static Set<String> getResources(String pResource, ClassLoader... pClassLoaders) throws IOException {
        List<ClassLoader> clls = findClassLoaders(pClassLoaders);
        if (!clls.isEmpty()) {
            Set<String> ret = new HashSet<>();
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
        Set<String> ret = new HashSet<>();
        while (urlEnum.hasMoreElements()) {
            ret.add(urlEnum.nextElement().toExternalForm());
        }
        return ret;
    }

    // Lookup appropriate constructor
    private static <T> Constructor<T> lookupConstructor(Class<T> clazz, Object[] pArguments) throws NoSuchMethodException {
        Class<?>[] argTypes = extractArgumentTypes(pArguments);
        return clazz.getConstructor(argTypes);
    }

    private static Method extractMethod(String pMethod, Class<?> clazz, Object[] pArgs) throws NoSuchMethodException {
        for (Method method : clazz.getMethods()) {
            if (!method.getName().equals(pMethod)) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (parametersMatch(parameters, pArgs)) {
                return method;
            }
        }
        throw new NoSuchMethodException("No " + pMethod + " on " + clazz + " with " + pArgs.length + " arguments found ");
    }

    private static Class<?>[] extractArgumentTypes(Object[] pArguments) {
        Class<?>[] argTypes = new Class[pArguments.length];
        int i = 0;
        for (Object arg : pArguments) {
            argTypes[i++] = arg.getClass();
        }
        return argTypes;
    }

    private static boolean parametersMatch(Class<?>[] parameters, Object[] pArgs) {
        if (parameters.length != pArgs.length) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            if (pArgs[i] == null) {
                continue;
            }
            Class<?> argClass = pArgs[i].getClass();
            Class<?> paramClass = parameters[i];
            if (!paramClass.isAssignableFrom(argClass)) {
                if (checkForPrimitive(argClass, paramClass)) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    private static boolean checkForPrimitive(Class<?> argClass, Class<?> paramClass) {
        return paramClass.isPrimitive() && PRIMITIVE_TO_OBJECT_MAP.get(paramClass.getName()) != null;
    }

    private static final Map<String,Class<?>> PRIMITIVE_TO_OBJECT_MAP = new HashMap<>();

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
