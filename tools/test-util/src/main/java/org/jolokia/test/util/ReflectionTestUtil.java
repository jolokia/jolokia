package org.jolokia.test.util;

import java.lang.reflect.Field;

/**
 * @author roland
 * @since 25.02.14
 */
public class ReflectionTestUtil {

    private ReflectionTestUtil() {}

    public static void setField(Object pObject,String pField, Object pValue) throws NoSuchFieldException, IllegalAccessException {
        Class<?> cl = pObject.getClass();
        Field field = cl.getDeclaredField(pField);
        field.setAccessible(true);
        field.set(pObject,pValue);
    }

    public static Object getField(Object pObject,String pField) throws NoSuchFieldException, IllegalAccessException {
        Class<?> cl = pObject.getClass();
        Field field = cl.getDeclaredField(pField);
        field.setAccessible(true);
        return field.get(pObject);
    }

}
