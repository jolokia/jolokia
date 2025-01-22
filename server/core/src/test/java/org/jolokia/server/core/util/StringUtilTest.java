/*
 * Copyright 2009-2025 Roland Huss
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
package org.jolokia.server.core.util;

import java.util.Properties;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

public class StringUtilTest {

    @Test
    public void oneLevelSystemProperties() {
        System.setProperty("jolokia.what", "world");
        assertNull(StringUtil.resolvePlaceholders(null));
        assertEquals(StringUtil.resolvePlaceholders("Hello"), "Hello");
        assertEquals(StringUtil.resolvePlaceholders("Hello ${jolokia.what}!"), "Hello world!");
        assertEquals(StringUtil.resolvePlaceholders("${jolokia.what}"), "world");
        assertEquals(StringUtil.resolvePlaceholders("${jolokia.what}, hello ${jolokia.what}!"), "world, hello world!");
    }

    @Test
    public void multibyteSystemProperties() {
        System.setProperty("jolokia.what", "世界");
        System.setProperty("こんにちは", "Hello");
        assertNull(StringUtil.resolvePlaceholders(null));
        assertEquals(StringUtil.resolvePlaceholders("こんにちは、${jolokia.what}！"), "こんにちは、世界！");
        assertEquals(StringUtil.resolvePlaceholders("${こんにちは} world!"), "Hello world!");
    }

    @Test
    public void valuesWithPlaceholdersAreNotResolved() {
        System.setProperty("jolokia.what", "${world}");
        assertEquals(StringUtil.resolvePlaceholders("Hello ${jolokia.what}!"), "Hello ${world}!");
        assertEquals(StringUtil.resolvePlaceholders("Hello ${world}!"), "Hello ${world}!");
    }

    @Test
    public void mismatchedPlaceholders() {
        System.setProperty("nestedProperty", "what");
        System.setProperty("jolokia.what", "world");
        assertEquals(StringUtil.resolvePlaceholders("Hello ${jolokia.what!"), "Hello ${jolokia.what!");
        assertEquals(StringUtil.resolvePlaceholders("Hello ${jolokia.${nestedProperty}!"), "Hello ${jolokia.what!");
    }

    @Test
    public void nestedSystemProperties() {
        System.setProperty("doubleProperty", "jolokia.what");
        System.setProperty("nestedProperty", "what");
        System.setProperty("jolokia.what", "world");
        assertEquals(StringUtil.resolvePlaceholders("Hello ${jolokia.${nestedProperty}}!"), "Hello world!");
        assertEquals(StringUtil.resolvePlaceholders("${jolokia.${nestedProperty}}"), "world");
        assertEquals(StringUtil.resolvePlaceholders("${jolokia.${nestedProperty}}, hello ${jolokia.${nestedProperty}}!"), "world, hello world!");
        assertEquals(StringUtil.resolvePlaceholders("${doubleProperty}"), "jolokia.what");
        assertEquals(StringUtil.resolvePlaceholders("${${doubleProperty}}"), "world");
        assertEquals(StringUtil.resolvePlaceholders("${${${doubleProperty}}}"), "${world}");
    }

    @Test
    public void recursiveSystemProperties() {
        System.setProperty("nestedProperty", "what");
        System.setProperty("jolokia.what", "jolokia.${nestedProperty}");
        assertEquals(StringUtil.resolvePlaceholders("Hello ${jolokia.${nestedProperty}}!"), "Hello jolokia.${nestedProperty}!");
        // no risk, because we don't resolve properties in system property values at all
//            assertThrows(IllegalArgumentException.class,
//                    () -> StringUtil.resolvePlaceholders("Hello ${jolokia.${nestedProperty}}!"));
    }

    @Test
    public void customProperties() {
        System.setProperty("jolokia.what", "world");
        Properties props = new Properties();
        props.setProperty("jolokia.what", "universe");
        assertEquals(StringUtil.resolvePlaceholders("Hello ${jolokia.what}!"), "Hello world!");
        assertEquals(StringUtil.resolvePlaceholders("Hello ${jolokia.what}!", props, System.getenv()), "Hello universe!");
    }

}
