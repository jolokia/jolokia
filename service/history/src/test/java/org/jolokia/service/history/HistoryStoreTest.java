package org.jolokia.service.history;

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

import java.util.*;

import javax.management.MalformedObjectNameException;

import org.jolokia.server.core.request.*;
import org.jolokia.json.JSONArray;
import org.jolokia.json.JSONObject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.jolokia.server.core.util.RequestType.*;
import static org.testng.Assert.*;

/**
 * Unit test for history functionality
 *
 * @author roland
 * @since Mar 9, 2010
 */
public class HistoryStoreTest {

    // History store to test against
    private HistoryStore store;


    @BeforeMethod
    public void initStore() {
        store = new HistoryStore(10);
    }

    @Test
    public void invalidHistoryKey() throws BadRequestException {
        JolokiaReadRequest req = new JolokiaRequestBuilder(READ,"test:type=bla")
                .attributes("bla","bla2")
                .build();
        try {
            new HistoryKey(req);
            fail("No multiple attributes allowed");
        } catch (IllegalArgumentException ignored) {}
    }

    public void invalidHistoryKeyWithPattern() throws BadRequestException {
        JolokiaReadRequest req = new JolokiaRequestBuilder(READ,"test:type=*")
                .attribute("bla")
                .build();
        try {
            new HistoryKey(req);
            fail("No pattern allowed");
        } catch (IllegalArgumentException ignored) {}
    }

    @Test
    public void configure() throws MalformedObjectNameException, BadRequestException {
        JolokiaExecRequest req =
                new JolokiaRequestBuilder(EXEC,"test:type=exec")
                        .operation("op")
                        .build();
        store.configure(new HistoryKey(req), new HistoryLimit(2, 0L));
        assertEquals(updateNTimesAsList(req,3).size(), 2,"2 history entries");
        store.configure(new HistoryKey(req), new HistoryLimit(4, 0L));
        assertEquals(updateNTimesAsList(req,5).size(), 4,"4 history entries");
        store.configure(new HistoryKey(req), new HistoryLimit(12, 0L));
        assertEquals(updateNTimesAsList(req,10).size(), 10,"10 history entries (max. for store)");
        store.setGlobalMaxEntries(20);
        assertEquals(store.getGlobalMaxEntries(), 20,"Read max entries");
        assertEquals(updateNTimesAsList(req,30).size(), 20,"20 history entries (max. for store)");
        store.reset();
        store.configure(new HistoryKey(req), new HistoryLimit(20, 0L));
        /* 5 fresh updates yield 4 history entries returned (and 5 stored) */
        assertEquals(updateNTimesAsList(req,5).size(), 4,"4 history entries after reset");
        store.configure(new HistoryKey(req), null);
        assertNull(updateNTimesAsList(req, 12), "History disabled");
    }

    @Test
    public void reconfigure() throws BadRequestException {
        JolokiaExecRequest req =
                new JolokiaRequestBuilder(EXEC,"test:type=exec")
                        .operation("op")
                        .build();

        store.configure(new HistoryKey(req), new HistoryLimit(2, 100L));
        store.configure(new HistoryKey(req), new HistoryLimit(2, 100L));
    }

    @Test
    public void durationBasedEvicting() throws BadRequestException {
        JolokiaReadRequest req =
                new JolokiaRequestBuilder(READ,"test:type=read")
                        .attribute("attr")
                        .build();
        HistoryKey key = new HistoryKey(req);
        store.configure(key,new HistoryLimit(0,1));
        JSONArray hist = updateNTimesAsListWithSleep(req, 3, 2000);
        assertEquals(hist.size(), 1,"1 History Entry");
    }


    @Test
    public void singleAttributeRead() throws Exception {
        JolokiaReadRequest req =
                new JolokiaRequestBuilder(READ,"test:type=read")
                        .attribute("attr")
                        .build();
        store.configure(new HistoryKey(req), new HistoryLimit(3, 0L));
        /* 3 fresh updates yield 2 history entries returned (and 3 stored) */
        assertEquals(updateNTimesAsList(req,3,"42").size(), 2,"2 history entries");

    }

    @Test
    public void singleAttributeWrite() throws Exception {
        JolokiaWriteRequest req =
                new JolokiaRequestBuilder(WRITE,"test:type=write")
                        .attribute("attr")
                        .value("val1")
                        .build();
        store.configure(new HistoryKey(req), new HistoryLimit(5, 0L));
        assertEquals(updateNTimesAsList(req,4).size(), 3,"4 history entries");
    }

    @Test
    public void singleAttributeAsListRead() throws Exception {
        JolokiaReadRequest req =
                new JolokiaRequestBuilder(READ,"test:type=read")
                        .attributes("attr")
                        .build();
        store.configure(new HistoryKey(req), new HistoryLimit(5, 0L));
        Map<String, String> value = new HashMap<>();
        value.put("attr","42");
        JSONObject res = updateNTimesAsMap(req,4,value);
        assertEquals(((List<?>) res.get("attr")).size(), 3,"4 history entries");
    }

    @Test
    public void noAttributesRead() throws Exception {
        String mbean = "test:type=read";
        JolokiaReadRequest req =
                new JolokiaRequestBuilder(READ,mbean)
                        .build();
        store.configure(new HistoryKey(mbean,"attr1",null,null), new HistoryLimit(4, 0L));
        store.configure(new HistoryKey(mbean,"attr2",null,null), new HistoryLimit(5, 0L));
        Map<String, String> value = new HashMap<>();
        value.put("attr1","val1");
        value.put("attr2","val2");
        JSONObject history = updateNTimesAsMap(req,5,value);
        assertEquals(((List<?>) history.get("attr1")).size(), 4, "Attr1 has 3 entries");
        assertEquals(((List<?>) history.get("attr2")).size(), 4, "Attr2 has 4 entries");
    }

    @Test
    public void multipleAttributeRead() throws Exception {
        String mbean = "test:type=read";
        JolokiaReadRequest req =
                new JolokiaRequestBuilder(READ,mbean)
                        .attributes("attr1","attr2")
                        .build();
        store.configure(new HistoryKey(mbean,"attr1",null,null), new HistoryLimit(3, 0L));
        store.configure(new HistoryKey(mbean,"attr2",null,null), new HistoryLimit(5, 0L));
        /* 5 fresh updates yield 2 history entries returned (and 3 stored) */
        Map<String, String> value = new HashMap<>();
        value.put("attr1","val1");
        value.put("attr2","val2");
        JSONObject history = updateNTimesAsMap(req,5,value);
        assertEquals(((List<?>) history.get("attr1")).size(), 3, "Attr1 has 3 entries");
        assertEquals(((List<?>) history.get("attr2")).size(), 4, "Attr2 has 4 entries");
    }


    @Test
    public void patternConfigure() throws MalformedObjectNameException, BadRequestException {
        store.configure(new HistoryKey("java.lang:type=Memory","HeapMemoryUsage",null,null), new HistoryLimit(10, 0L));
        store.configure(new HistoryKey("java.lang:*", "HeapMemoryUsage", null, null), new HistoryLimit(3, 0L));
        JolokiaReadRequest req =
                new JolokiaRequestBuilder(READ,"java.lang:type=Memory")
                        .attribute("HeapMemoryUsage")
                        .build();

        JSONArray history = updateNTimesAsList(req, 5, 4711);
        assertEquals(history.size(), 3);
    }

    @Test
    public void patternRemoveEntries() throws MalformedObjectNameException, BadRequestException {
        store.configure(new HistoryKey("java.lang:*", "HeapMemoryUsage", null, null), new HistoryLimit(3, 0L));
        store.configure(new HistoryKey("java.lang:type=Memory","HeapMemoryUsage",null,null), new HistoryLimit(10, 0L));
        store.configure(new HistoryKey("java.lang:*", "HeapMemoryUsage", null, null), null);

        JolokiaReadRequest req =
                new JolokiaRequestBuilder(READ,"java.lang:type=Memory")
                        .attribute("HeapMemoryUsage")
                        .build();

        JSONArray history = updateNTimesAsList(req, 5, 4711);
        assertNull(history);
    }

    @Test
    public void patternGetEntries() throws MalformedObjectNameException, BadRequestException {
        store.configure(new HistoryKey("java.lang:*", "HeapMemoryUsage", null, null), new HistoryLimit(3, 0L));

        JolokiaReadRequest req =
                new JolokiaRequestBuilder(READ,"java.lang:type=Memory")
                        .attribute("HeapMemoryUsage")
                        .build();

        JSONArray history = updateNTimesAsList(req, 5, 4711);
        assertEquals(history.size(), 3);
    }


    @Test
    public void size() throws Exception {
        assertTrue(store.getSize() < 100);
        singleAttributeRead();
        assertTrue(store.getSize() > 100);
    }

    public void patternAttributeRead() throws Exception {
        JolokiaReadRequest req =
                new JolokiaRequestBuilder(READ,"test:type=*")
                        .build();
        store.configure(new HistoryKey("test:type=read","attr1",null,null), new HistoryLimit(3, 0L));
        store.configure(new HistoryKey("test:type=write","attr2",null,null), new HistoryLimit(5, 0L));
        /* 5 fresh updates yield 2 history entries returned (and 3 stored) */
        Map<String, Object> mBeanMap = new HashMap<>();
        Map<String, String> attr1Map = new HashMap<>();
        mBeanMap.put("test:type=read",attr1Map);
        attr1Map.put("attr1","val1");
        Map<String, String> attr2Map = new HashMap<>();
        mBeanMap.put("test:type=write",attr2Map);
        attr2Map.put("attr2","val2");
        JSONObject history = updateNTimesAsMap(req,4,mBeanMap);
        assertEquals(history.size(), 2,"History has 2 entries");
        assertEquals(((Map<?, ?>) history.get("test:type=read")).size(), 1, "bean1 has 1 entry");
        assertEquals(((Map<?, ?>) history.get("test:type=write")).size(), 1, "bean1 has 1 entry");
        assertEquals(((List<?>) ((Map<?, ?>) history.get("test:type=read")).get("attr1")).size(), 3, "attr1 has 3 history entries");
        assertEquals(((List<?>) ((Map<?, ?>) history.get("test:type=write")).get("attr2")).size(), 3, "attr2 has 3 history entries");
    }

    private JSONArray updateNTimesAsListWithSleep(JolokiaReadRequest pReq, int pNr, long pSleep,Object ... pValue) {
        return (JSONArray) updateNTimes(pReq,pNr,pSleep,pValue);
    }

    private JSONArray updateNTimesAsList(JolokiaRequest pReq, int pNr,Object ... pValue) {
        return (JSONArray) updateNTimes(pReq, pNr,0L,pValue);
    }

    private JSONObject updateNTimesAsMap(JolokiaRequest pReq, int pNr,Object ... pValue) {
        return (JSONObject) updateNTimes(pReq, pNr,0L,pValue);
    }

    private synchronized Object updateNTimes(JolokiaRequest pReq, int pNr,long pSleep, Object ... pValue) {
        JSONObject res = new JSONObject();
        if (pValue != null && pValue.length > 0) {
            res.put("value",pValue[0]);
        }
        for (int i=0;i<pNr;i++) {
            store.updateAndAdd(pReq,res);
            if (pSleep != 0L) {
                try {
                    wait(pSleep);
                } catch (InterruptedException e) {
                    throw new IllegalArgumentException("Interrupted",e);
                }
            }
        }
        return res.get("history");
    }


    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*maxEntries.*maxDuration.*")
     public void invalidHistoryLimit() {
        new HistoryLimit(0,0L);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*maxEntries.*")
    public void invalidMaxEntriesHistoryLimit() {
        new HistoryLimit(-1,0L);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*maxDuration.*")
    public void invalidMaxDurationHistoryLimit() {
        new HistoryLimit(0,-1L);
    }

    @Test
    public void valueEntryTest() {
        ValueEntry entry = new ValueEntry("Test",42L);
        assertTrue(entry.toString().contains("42"));
        assertTrue(entry.toString().contains("Test"));
    }

    @Test
    public void historyEntryTest() {
        HistoryEntry entry = new HistoryEntry(new HistoryLimit(10,20L));
        entry.add("Blub",30L);
        assertTrue(entry.toString().contains("10"));
        assertTrue(entry.toString().contains("20"));
        assertTrue(entry.toString().contains("Blub"));
    }
}
