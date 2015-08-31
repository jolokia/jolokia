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

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 19.04.11
 */
public class ClassUtilTest {

    public ClassUtilTest() {
    }

    public ClassUtilTest(String stringProp, Integer intProp) {
        this.stringProp = stringProp;
        this.intProp = intProp;
    }

    public void setStringProp(String stringProp) {
        this.stringProp = stringProp;
    }

    public void setIntProp(int intProp) {
        this.intProp = intProp;
    }

    private String stringProp;
    private int intProp;


    @Test
    public void classForName() {
        assertTrue(ClassUtil.checkForClass("java.lang.String"));
        assertEquals(ClassUtil.classForName(ClassUtilTest.class.getName()),ClassUtilTest.class);
        assertNull(ClassUtil.classForName("blablub"));
    }

    @Test
    public void classForNameFoundInParent() throws ClassNotFoundException {
        ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
        ClassLoader cl = new MyCl(oldCl);
        Thread.currentThread().setContextClassLoader(cl);

        Class clazz = ClassUtil.classForName("org.jolokia.util.RequestType");
        assertEquals(clazz.getName(),"org.jolokia.util.RequestType");
        assertEquals(oldCl.loadClass("org.jolokia.util.RequestType"),clazz);

        Thread.currentThread().setContextClassLoader(oldCl);
    }

    @Test
    public void classForNameWithoutContextClassLoader() {
        Thread current = Thread.currentThread();
        ClassLoader origLoader = current.getContextClassLoader();
        current.setContextClassLoader(null);
        try {
            classForName();
        } finally {
            current.setContextClassLoader(origLoader);
        }
    }

    @Test
    public void resourceAsStream() {
        checkResources();
        Thread current = Thread.currentThread();
        ClassLoader origLoader = current.getContextClassLoader();
        current.setContextClassLoader(null);
        try {
            checkResources();
        } finally {
            current.setContextClassLoader(origLoader);
        }
    }

    @Test
    public void testGetResources() throws IOException {
        Set<String> urls = ClassUtil.getResources("META-INF/detectors");
        assertNotNull(urls);
        assertEquals(urls.size(),1);
    }

    @Test
    public void testNewInstance() {
        ClassUtilTest test = ClassUtil.newInstance(getClass().getCanonicalName());
        assertEquals(test.getClass(),getClass());
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*find.*")
    public void testNewInstanceFail1() {
        ClassUtil.newInstance("blubber.bla");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*NoSuchMethodException.*")
    public void testNewInstanceFail2() {
        ClassUtil.newInstance("java.lang.String",Boolean.TRUE);
    }

    @Test
    public void testApply() {
        File testFile = new File("/cannot/possibly/exist/at/all");
        Boolean result = (Boolean) ClassUtil.applyMethod(testFile,"exists");
        assertFalse(result);
    }

    @Test
    public void testApplyWithPrimitive() {
        ClassUtilTest test = new ClassUtilTest("bla",1);
        assertEquals(test.intProp,1);
        ClassUtil.applyMethod(test,"setIntProp",new Integer(2));
        assertEquals(test.intProp,2);
    }
    @Test
    public void testApplyNoArgs() {
        String fs = System.getProperty("path.separator");
        String pathname = fs + "tmp";
        File testFile = new File(pathname);
        String path = (String) ClassUtil.applyMethod(testFile,"getPath");
        assertEquals(path, pathname);
    }
    @Test
    public void testApplyWithArgs() {
        Map<String,String> map = new HashMap<String,String>();
        ClassUtil.applyMethod(map,"put","hello","world");
        assertEquals(map.get("hello"),"world");
    }

    @Test
    public void testApplyWithNullArg() {
        ClassUtilTest test = new ClassUtilTest("set",0);
        assertEquals(test.stringProp,"set");
        ClassUtil.applyMethod(test,"setStringProp",new Object[] { null });
        assertEquals(test.stringProp,null);
    }
    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*NoSuchMethod.*")
    public void testApplyWithArgsFail1() {
        Map<String,String> map = new HashMap<String,String>();
        ClassUtil.applyMethod(map, "putBlubber", "hello", "world");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*NoSuchMethod.*")
    public void testApplyWithFail2() {
        ClassUtilTest test = new ClassUtilTest();
        ClassUtil.applyMethod(test,"setStringProp",Boolean.TRUE);
    }


    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*NoSuchMethodException.*")
    public void testApplyFail1() {
        ClassUtil.applyMethod(new Object(),"bullablu");
    }


    @Test
    public void testNewInstanceWithConstructor() {
        ClassUtilTest test = ClassUtil.newInstance(getClass().getCanonicalName(),"eins",2);
        assertEquals(test.getClass(),getClass());
        assertEquals(test.stringProp,"eins");
        assertEquals(test.intProp,2);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*Blub.*")
    public void testNewInstanceNotFound() {
        ClassUtil.newInstance(getClass().getCanonicalName() + "$Blub");
    }

    private void checkResources() {
        assertNotNull(ClassUtil.getResourceAsStream("access-sample1.xml"));
        assertNull(ClassUtil.getResourceAsStream("plumperquatsch"));
    }

    public static class MyCl extends ClassLoader {
        protected MyCl(ClassLoader cl) {
            super(cl);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.equals("org.jolokia.util.RequestType")) {
                throw new ClassNotFoundException();
            }
            return super.loadClass(name);
        }
    }
}
