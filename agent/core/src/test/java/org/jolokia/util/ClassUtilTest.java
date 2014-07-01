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

import java.io.IOException;
import java.net.URL;
import java.util.Set;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 19.04.11
 */
public class ClassUtilTest {

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
        Set<URL> urls = ClassUtil.getResources("META-INF/detectors");
        assertNotNull(urls);
        assertEquals(urls.size(),1);
    }

    @Test
    public void testNewInstance() {
        ClassUtilTest test = ClassUtil.newInstance(getClass().getCanonicalName());
        assertEquals(test.getClass(),getClass());
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
