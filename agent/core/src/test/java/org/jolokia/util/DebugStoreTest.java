package org.jolokia.util;

/*
 * Copyright 2009-2011 Roland Huss
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 02.09.11
 */
public class DebugStoreTest {

    @Test
    public void simple() {
        DebugStore store = new DebugStore(2,true);
        store.log("Test");
        assertTrue(store.debugInfo().contains("Test"));
        store.resetDebugInfo();
        assertEquals(store.debugInfo().length(), 0);
    }

    @Test
    public void simpleWithException() {
        DebugStore store = new DebugStore(2,true);
        Exception exp = new Exception();
        store.log("Test",exp);
        assertTrue(store.debugInfo().contains("Test"));
        assertTrue(store.debugInfo().contains(this.getClass().getName()));
    }

    @Test
    public void switchDebugAndTrim() {
        DebugStore store = new DebugStore(2,true);
        store.log("Test");
        assertTrue(store.debugInfo().contains("Test"));
        store.setDebug(false);
        assertFalse(store.isDebug());
        assertEquals(store.debugInfo().length(),0);
        store.setDebug(true);
        assertTrue(store.isDebug());
        store.log("Test1");
        assertTrue(store.debugInfo().contains("Test1"));
        store.log("Test2");
        store.log("Test3");
        // Test1 should be removed
        assertTrue(!store.debugInfo().contains("Test1"));
        assertTrue(store.debugInfo().contains("Test2"));
        assertTrue(store.debugInfo().contains("Test3"));

        // And now Test2, too
        assertEquals(store.getMaxDebugEntries(),2);
        store.setMaxDebugEntries(1);
        assertEquals(store.getMaxDebugEntries(),1);
        assertTrue(!store.debugInfo().contains("Test1"));
        assertTrue(!store.debugInfo().contains("Test2"));
        assertTrue(store.debugInfo().contains("Test3"));
    }

    @Test
    public void noDebug() {
        DebugStore store = new DebugStore(2,false);
        store.log("Test");
        assertEquals(store.debugInfo().length(), 0);
    }
}
