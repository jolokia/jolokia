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

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.request.*;
import org.testng.annotations.Test;

import static org.jolokia.util.RequestType.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 06.03.12
 */
public class HistoryKeyTest {


    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*null.*")
    public void emptyAttributeName() throws MalformedObjectNameException {
        new HistoryKey((JmxWriteRequest) new JmxRequestBuilder(WRITE, "test:type=bla").build());

    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*null.*")
    public void emptyOperationName() throws MalformedObjectNameException {
        new HistoryKey((JmxExecRequest) new JmxRequestBuilder(EXEC, "test:type=bla").build());

    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*more than one.*")
    public void multipleAttributeNames() throws MalformedObjectNameException {
        new HistoryKey((JmxReadRequest) new JmxRequestBuilder(READ, "test:type=bla").attributes("eins","zwei").build());

    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*pattern.*")
    public void patternMBeanName() throws MalformedObjectNameException {
        new HistoryKey((JmxReadRequest) new JmxRequestBuilder(READ,"test:type=*").build());
    }



    @Test
    public void readRequest() throws MalformedObjectNameException {
        JmxReadRequest req = new JmxRequestBuilder(READ,"test:type=blub")
                .attribute("bla")
                .path("blub")
                .build();

        HistoryKey key = new HistoryKey(req);
        assertTrue(key.toString().contains("attribute"));
        assertTrue(key.toString().contains("bla"));
        assertTrue(key.toString().contains("blub"));
        assertTrue(key.toString().contains("test:type=blub"));

        HistoryKey key2 = new HistoryKey("test:type=blub","bla","blub","targetUrl");
        assertTrue(key2.toString().contains("targetUrl"));

        assertFalse(key.equals(key2));
        HistoryKey key3 = new HistoryKey("test:type=blub","bla","blub",null);
        assertTrue(key.equals(key3));
        assertEquals(key.hashCode(),key3.hashCode());
    }

    @Test
    public void readRequestForAll() throws MalformedObjectNameException {
        JmxReadRequest req = new JmxRequestBuilder(READ,"test:type=blub")
                .build();

        HistoryKey key = new HistoryKey(req);
        assertTrue(key.toString().contains("attribute"));
        assertTrue(key.toString().contains("(all)"));
        assertTrue(key.toString().contains("test:type=blub"));
    }

    @Test
    public void writeRequest() throws MalformedObjectNameException {
        JmxWriteRequest req = new JmxRequestBuilder(WRITE,"test:type=blub")
                .attribute("bla")
                .build();

        HistoryKey key = new HistoryKey(req);
        assertTrue(key.toString().contains("attribute"));
        assertTrue(key.toString().contains("bla"));
        assertTrue(key.toString().contains("test:type=blub"));
    }
    
    @Test
    public void execRequest() throws MalformedObjectNameException {
        JmxExecRequest req = new JmxRequestBuilder(EXEC,"test:type=blub")
                .operation("exec")
                .arguments("eins","zwei")
                .build();

        HistoryKey key = new HistoryKey(req);
        assertTrue(key.toString().contains("operation"));
        assertTrue(key.toString().contains("exec"));
        assertTrue(key.toString().contains("test:type=blub"));

        key = new HistoryKey("test:type=blub","exec","targetUrl");
        assertTrue(key.toString().contains("targetUrl"));
    }
    
    @Test
    public void matches() throws MalformedObjectNameException {
        HistoryKey key = new HistoryKey("test:type=*","bla",null,null);
        assertTrue(key.isMBeanPattern());
        
        JmxReadRequest req2 = new JmxRequestBuilder(READ,"test:type=hello")
                .attribute("bla")
                .build();

        JmxReadRequest req3 = new JmxRequestBuilder(READ,"test:name=hello")
                .attribute("bla")
                .build();

        HistoryKey key2 = new HistoryKey(req2);
        assertFalse(key2.isMBeanPattern());
        HistoryKey key3 = new HistoryKey(req3);
        assertFalse(key3.isMBeanPattern());

        assertTrue(key.matches(key2));
        assertFalse(key.matches(key3));
    }

    @Test
    public void emptyPath() throws MalformedObjectNameException {
        HistoryKey key1 = new HistoryKey("test:type=bla","bla","",null);
        HistoryKey key2 = new HistoryKey("test:type=bla","bla",null,null);
        assertEquals(key1,key2);
    }
}
