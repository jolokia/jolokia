/*
 * Copyright 2011 Roland Huss
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jolokia.request;

import java.util.List;

import org.testng.annotations.Test;

/**
 * @author roland
 * @since 16.03.11
 */
public class JmxRequestFactoryTest {


    @Test
    public void simpleGet() {
        JmxReadRequest req = JmxRequestFactory.createGetRequest("read/java.lang:type=Memory/HeapMemoryUsage",null);
        assert req.getType() == RequestType.READ : "Type is read";
        assert req.getObjectName().getCanonicalName().equals("java.lang:type=Memory") : "Name properly parsed";
        assert req.getPathParts() == null : "PathParts are null";
        assert req.getPath() == null : "Path is null";
    }

    @Test
    public void simpleGetWithPath() {
        JmxWriteRequest req = JmxRequestFactory.createGetRequest("write/java.lang:type=Runtime/SystemProperties/7788/[com.sun.management.jmxremote.port]/value", null);
        assert req.getType() == RequestType.WRITE : "Type is write";
        assert req.getObjectName().getCanonicalName().equals("java.lang:type=Runtime") : "Name properly parsed";
        List<String> parts = req.getPathParts();
        assert parts.get(0).equals("[com.sun.management.jmxremote.port]") : "Path part 0:" + parts.get(0);
        assert parts.get(1).equals("value") : "Path part 1: " + parts.get(1) ;
        assert req.getPath().equals("[com.sun.management.jmxremote.port]/value");
    }

    @Test
    public void readWithPattern() {
        JmxReadRequest req = JmxRequestFactory.createGetRequest("read/java.lang:type=*",null);
        assert req.getType() == RequestType.READ : "Type is read";
        assert req.getObjectName().getCanonicalName().equals("java.lang:type=*") : "Name properly parsed";
        assert req.getObjectName().isPattern() : "Name is pattern";
        assert req.getAttributeNames() == null : "No attributes names";
        assert req.getAttributeName() == null : "No attributes names";
        assert req.getPath() == null : "Path is null";
        req = JmxRequestFactory.createGetRequest("read/java.lang:type=*/HeapMemoryUsage",null);
        assert req.getAttributeName().equals("HeapMemoryUsage") : "No attributes names";

    }

    @Test
    public void readWithPatternAndAttribute() {
        JmxReadRequest req = JmxRequestFactory.createGetRequest("read/java.lang:type=*/",null);
        assert req.getType() == RequestType.READ : "Type is read";
        assert req.getObjectName().getCanonicalName().equals("java.lang:type=*") : "Name properly parsed";
        assert req.getObjectName().isPattern() : "Name is pattern";
        assert req.getAttributeNames() == null : "No attributes names";
        assert req.getAttributeName() == null : "No attributes names";
        assert req.getPath() == null : "Path is null";
    }

    @Test(expectedExceptions = { ClassCastException.class } )
    public void castException() {
        JmxReadRequest req = JmxRequestFactory.createGetRequest("exec/java.lang:type=Memory/gc",null);
    }
}
