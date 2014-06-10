package org.jolokia.history;

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

import org.jolokia.request.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.jolokia.util.RequestType.*;
import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;


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
    public void invalidHistoryKey() throws MalformedObjectNameException {
        JmxReadRequest req = new JmxRequestBuilder(READ,"test:type=bla")
                .attributes("bla","bla2")
                .build();
        try {
            new HistoryKey(req);
            fail("No multiple attributes allowed");
        } catch (IllegalArgumentException exp) {}
    }

    @Test(groups = "java6")
    public void invalidHistoryKeyWithPattern() throws MalformedObjectNameException {
        JmxReadRequest req = new JmxRequestBuilder(READ,"test:type=*")
                .attribute("bla")
                .build();
        try {
            new HistoryKey(req);
            fail("No pattern allowed");
        } catch (IllegalArgumentException exp) {}
    }

    @Test
    public void configure() throws MalformedObjectNameException {
        JmxExecRequest req =
                new JmxRequestBuilder(EXEC,"test:type=exec")
                        .operation("op")
                        .build();
        store.configure(new HistoryKey(req), new HistoryLimit(2, 0L));
        assertEquals("2 history entries",2,updateNTimesAsList(req,3).size());
        store.configure(new HistoryKey(req), new HistoryLimit(4, 0L));
        assertEquals("4 history entries",4,updateNTimesAsList(req,5).size());
        store.configure(new HistoryKey(req), new HistoryLimit(12, 0L));
        assertEquals("10 history entries (max. for store)",10,updateNTimesAsList(req,10).size());
        store.setGlobalMaxEntries(20);
        assertEquals("Read max entries",20,store.getGlobalMaxEntries());
        assertEquals("20 history entries (max. for store)",20,updateNTimesAsList(req,30).size());
        store.reset();
        store.configure(new HistoryKey(req), new HistoryLimit(20, 0L));
        /** 5 fresh updates yield 4 history entries returned (and 5 stored) */
        assertEquals("4 history entries after reset",4,updateNTimesAsList(req,5).size());
        store.configure(new HistoryKey(req), null);
        assertEquals("History disabled",null,updateNTimesAsList(req,12));
    }

    @Test
    public void reconfigure() throws MalformedObjectNameException {
        JmxExecRequest req =
                new JmxRequestBuilder(EXEC,"test:type=exec")
                        .operation("op")
                        .build();

        store.configure(new HistoryKey(req), new HistoryLimit(2, 100L));
        store.configure(new HistoryKey(req), new HistoryLimit(2, 100L));
    }
    
    @Test
    public void durationBasedEvicting() throws MalformedObjectNameException {
        JmxReadRequest req =
                new JmxRequestBuilder(READ,"test:type=read")
                        .attribute("attr")
                        .build();
        HistoryKey key = new HistoryKey(req);
        store.configure(key,new HistoryLimit(0,1));
        JSONArray hist = updateNTimesAsListWithSleep(req, 3, 2000);
        assertEquals("1 History Entry", 1, hist.size());
    }

    
    @Test
    public void singleAttributeRead() throws Exception {
        JmxReadRequest req =
                new JmxRequestBuilder(READ,"test:type=read")
                        .attribute("attr")
                        .build();
        store.configure(new HistoryKey(req), new HistoryLimit(3, 0L));
        /** 3 fresh updates yield 2 history entries returned (and 3 stored) */
        assertEquals("2 history entries",2,updateNTimesAsList(req,3,"42").size());

    }

    @Test
    public void singleAttributeWrite() throws Exception {
        JmxWriteRequest req =
                new JmxRequestBuilder(WRITE,"test:type=write")
                        .attribute("attr")
                        .value("val1")
                        .build();
        store.configure(new HistoryKey(req), new HistoryLimit(5, 0L));
        assertEquals("4 history entries",3,updateNTimesAsList(req,4).size());
    }
    @Test
    public void singleAttributeAsListRead() throws Exception {
        JmxReadRequest req =
                new JmxRequestBuilder(READ,"test:type=read")
                        .attributes("attr")
                        .build();
        store.configure(new HistoryKey(req), new HistoryLimit(5, 0L));
        Map value = new HashMap();
        value.put("attr","42");
        JSONObject res = updateNTimesAsMap(req,4,value);
        assertEquals("4 history entries",3,((List) res.get("attr")).size());
    }

    @Test
    public void noAttributesRead() throws Exception {
        String mbean = "test:type=read";
        JmxReadRequest req =
                new JmxRequestBuilder(READ,mbean)
                        .build();
        store.configure(new HistoryKey(mbean,"attr1",null,null), new HistoryLimit(4, 0L));
        store.configure(new HistoryKey(mbean,"attr2",null,null), new HistoryLimit(5, 0L));
        Map value = new HashMap();
        value.put("attr1","val1");
        value.put("attr2","val2");
        JSONObject history = updateNTimesAsMap(req,5,value);
        assertEquals("Attr1 has 3 entries",4,((List) history.get("attr1")).size());
        assertEquals("Attr2 has 4 entries",4,((List) history.get("attr2")).size());
    }

    @Test
    public void multipleAttributeRead() throws Exception {
        String mbean = "test:type=read";
        JmxReadRequest req =
                new JmxRequestBuilder(READ,mbean)
                        .attributes("attr1","attr2")
                        .build();
        store.configure(new HistoryKey(mbean,"attr1",null,null), new HistoryLimit(3, 0L));
        store.configure(new HistoryKey(mbean,"attr2",null,null), new HistoryLimit(5, 0L));
        /** 5 fresh updates yield 2 history entries returned (and 3 stored) */
        Map value = new HashMap();
        value.put("attr1","val1");
        value.put("attr2","val2");
        JSONObject history = updateNTimesAsMap(req,5,value);
        assertEquals("Attr1 has 3 entries",3,((List) history.get("attr1")).size());
        assertEquals("Attr2 has 4 entries",4,((List) history.get("attr2")).size());
    }


    @Test
    public void patternConfigure() throws MalformedObjectNameException {
        store.configure(new HistoryKey("java.lang:type=Memory","HeapMemoryUsage",null,null), new HistoryLimit(10, 0L));
        store.configure(new HistoryKey("java.lang:*", "HeapMemoryUsage", null, null), new HistoryLimit(3, 0L));
        JmxReadRequest req =
                new JmxRequestBuilder(READ,"java.lang:type=Memory")
                        .attribute("HeapMemoryUsage")
                        .build();

        JSONArray history = updateNTimesAsList(req, 5, 4711);
        assertEquals(history.size(), 3);
    }

    @Test
    public void patternRemoveEntries() throws MalformedObjectNameException {
        store.configure(new HistoryKey("java.lang:*", "HeapMemoryUsage", null, null), new HistoryLimit(3, 0L));
        store.configure(new HistoryKey("java.lang:type=Memory","HeapMemoryUsage",null,null), new HistoryLimit(10, 0L));
        store.configure(new HistoryKey("java.lang:*", "HeapMemoryUsage", null, null), null);

        JmxReadRequest req =
                new JmxRequestBuilder(READ,"java.lang:type=Memory")
                        .attribute("HeapMemoryUsage")
                        .build();

        JSONArray history = updateNTimesAsList(req, 5, 4711);
        assertNull(history);
    }

    @Test
    public void patternGetEntries() throws MalformedObjectNameException {
        store.configure(new HistoryKey("java.lang:*", "HeapMemoryUsage", null, null), new HistoryLimit(3, 0L));

        JmxReadRequest req =
                new JmxRequestBuilder(READ,"java.lang:type=Memory")
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

    @Test(groups = "java6")
    public void patternAttributeRead() throws Exception {
        JmxReadRequest req =
                new JmxRequestBuilder(READ,"test:type=*")
                        .build();
        store.configure(new HistoryKey("test:type=read","attr1",null,null), new HistoryLimit(3, 0L));
        store.configure(new HistoryKey("test:type=write","attr2",null,null), new HistoryLimit(5, 0L));
        /** 5 fresh updates yield 2 history entries returned (and 3 stored) */
        Map mBeanMap = new HashMap();
        Map attr1Map = new HashMap();
        mBeanMap.put("test:type=read",attr1Map);
        attr1Map.put("attr1","val1");
        Map attr2Map = new HashMap();
        mBeanMap.put("test:type=write",attr2Map);
        attr2Map.put("attr2","val2");
        JSONObject history = updateNTimesAsMap(req,4,mBeanMap);
        assertEquals("History has 2 entries",2,history.size());
        assertEquals("bean1 has 1 entry",1,((Map) history.get("test:type=read")).size());
        assertEquals("bean1 has 1 entry",1,((Map) history.get("test:type=write")).size());
        assertEquals("attr1 has 3 history entries",3,((List) ((Map) history.get("test:type=read")).get("attr1")).size());
        assertEquals("attr2 has 3 history entries",3,((List) ((Map) history.get("test:type=write")).get("attr2")).size());
    }

    private JSONArray updateNTimesAsListWithSleep(JmxReadRequest pReq, int pNr, long pSleep,Object ... pValue) {
        return (JSONArray) updateNTimes(pReq,pNr,pSleep,pValue);    
    }

    private JSONArray updateNTimesAsList(JmxRequest pReq, int pNr,Object ... pValue) {
        return (JSONArray) updateNTimes(pReq, pNr,0L,pValue);
    }

    private JSONObject updateNTimesAsMap(JmxRequest pReq, int pNr,Object ... pValue) {
        return (JSONObject) updateNTimes(pReq, pNr,0L,pValue);
    }
    
    private synchronized Object updateNTimes(JmxRequest pReq, int pNr,long pSleep, Object ... pValue) {
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
