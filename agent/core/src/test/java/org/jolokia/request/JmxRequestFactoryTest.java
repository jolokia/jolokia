package org.jolokia.request;

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

import org.jolokia.config.*;
import org.jolokia.util.HttpMethod;
import org.jolokia.util.RequestType;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.jolokia.request.JmxRequestBuilder.createMap;
import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 16.03.11
 */
public class JmxRequestFactoryTest {

    private ProcessingParameters procParams;

    @BeforeTest
    public void setup() {
        procParams = new Configuration().getProcessingParameters(new HashMap<String, String>());
    }

    @Test
    public void simpleGet() {
        JmxReadRequest req = JmxRequestFactory.createGetRequest("read/java.lang:type=Memory/HeapMemoryUsage", procParams);
        assert req.getType() == RequestType.READ : "Type is read";
        assert req.getObjectName().getCanonicalName().equals("java.lang:type=Memory") : "Name properly parsed";
        assertEquals(req.getAttributeName(),"HeapMemoryUsage","Attribute parsed properly");
        assert req.getPathParts() == null : "PathParts are null";
        assert req.getPath() == null : "Path is null";
    }

    @Test
    public void simplePost() {
        Map<String,Object> reqMap = createMap(
                "type","read",
                "mbean","java.lang:type=Memory",
                "attribute","HeapMemoryUsage");
        JmxReadRequest req = JmxRequestFactory.createPostRequest(reqMap, procParams);
        assert req.getType() == RequestType.READ : "Type is read";
        assert req.getObjectName().getCanonicalName().equals("java.lang:type=Memory") : "Name properly parsed";
        assertEquals(req.getAttributeName(),"HeapMemoryUsage","Attribute parsed properly");
        assert req.getPathParts() == null : "PathParts are null";
        assert req.getPath() == null : "Path is null";
    }

    @Test
    public void simplePostWithPath() {
        Map<String,Object> reqMap = createMap(
                "type","read",
                "mbean","java.lang:type=Memory",
                "attribute","HeapMemoryUsage",
                "path","blub!/bla/hello");
        JmxReadRequest req = JmxRequestFactory.createPostRequest(reqMap, procParams);
        List<String> path = req.getPathParts();
        assertEquals(path.size(),2);
        assertEquals(path.get(0),"blub/bla");
        assertEquals(path.get(1),"hello");
        assertEquals(req.getPath(),"blub!/bla/hello");
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void simplePostWithMalformedObjectName() {
        JmxRequestFactory.createPostRequest(createMap("type", "read", "mbean", "bal::blub", "attribute", "HeapMemoryUsage"), procParams);
    }

    @Test
    public void simplePostWithMergedMaps() {
        Map config = new HashMap();
        config.put("maxDepth","10");
        Map<String,Object> reqMap = createMap(
                "type","read",
                "mbean","java.lang:type=Memory",
                "attribute","HeapMemoryUsage",
                "config",config);
        Map param = new HashMap();;
        param.put("maxObjects","100");
        JmxReadRequest req = (JmxReadRequest) JmxRequestFactory.createPostRequest(reqMap,new Configuration().getProcessingParameters(param));
        assertEquals(req.getAttributeName(),"HeapMemoryUsage");
        assertEquals(req.getParameter(ConfigKey.MAX_DEPTH),"10");
        assertEquals(req.getParameterAsInt(ConfigKey.MAX_OBJECTS), 100);
    }

    @Test
    public void multiPostRequests() {
        Map<String,Object> req1Map = createMap(
                "type","read",
                "mbean","java.lang:type=Memory",
                "attribute","HeapMemoryUsage");
        Map<String,Object> req2Map = createMap(
                "type","list");
        List<JmxRequest> req = JmxRequestFactory.createPostRequests(Arrays.asList(req1Map, req2Map),procParams);
        assertEquals(req.get(0).getType(), RequestType.READ);
        assertEquals(req.get(1).getType(), RequestType.LIST);
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void multiPostRequestsWithWrongArg() {
        Map<String,Object> reqMap = createMap(
                "type", "list");
        JmxRequestFactory.createPostRequests(Arrays.asList(reqMap, "Wrong"),procParams);
    }

    @Test
    public void simpleGetWithPath() {
        JmxWriteRequest req = JmxRequestFactory.createGetRequest("write/java.lang:type=Runtime/SystemProperties/7788/[com.sun.management.jmxremote.port]/value", procParams);
        assert req.getType() == RequestType.WRITE : "Type is write";
        assert req.getObjectName().getCanonicalName().equals("java.lang:type=Runtime") : "Name properly parsed";
        List<String> parts = req.getPathParts();
        assert parts.get(0).equals("[com.sun.management.jmxremote.port]") : "Path part 0:" + parts.get(0);
        assert parts.get(1).equals("value") : "Path part 1: " + parts.get(1) ;
        assert req.getPath().equals("[com.sun.management.jmxremote.port]/value");
    }

    @Test
    public void simpleGetWithEscapedAttribute() {
        JmxReadRequest req = JmxRequestFactory.createGetRequest("read/java.lang:type=Memory/!/Heap!/Memory!/Usage!/",procParams);
        assertEquals(req.getAttributeName(),"/Heap/Memory/Usage/","Attribute properly parsed");
    }

    @Test
    public void simpleGetWithEscapedPath() {
        JmxReadRequest req = JmxRequestFactory.createGetRequest("read/java.lang:type=Memory/HeapMemoryUsage/used!/bla!/blub/bloe",procParams);
        assertEquals(req.getPathParts().size(),2,"Size of path");
        assertEquals(req.getPath(),"used!/bla!/blub/bloe","Path properly parsed");
    }

    @Test(expectedExceptionsMessageRegExp = ".*pathinfo.*",expectedExceptions = {IllegalArgumentException.class})
    public void illegalPath() {
        JmxRequestFactory.createGetRequest("read", procParams);
    }

    @Test(expectedExceptionsMessageRegExp = ".*Invalid object name.*",expectedExceptions = {IllegalArgumentException.class})
    public void invalidObjectName() {
        JmxRequestFactory.createGetRequest("read/bla::blub",procParams);
    }

    @Test(expectedExceptions = {UnsupportedOperationException.class})
    public void unsupportedType() {
        JmxRequestFactory.createGetRequest("regnotif",procParams);
    }

    @Test
    public void emptyRequest() {
        JmxVersionRequest req = JmxRequestFactory.createGetRequest("",procParams);
        req = JmxRequestFactory.createGetRequest(null,procParams);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void nullProcParams() {
        JmxRequestFactory.createGetRequest("",null);
    }

    @Test
    public void simpleGetWithQueryPath() {
        Map<String,String> params = new HashMap<String, String>();
        params.put("p","list/java.lang/type=Memory");
        JmxListRequest req = JmxRequestFactory.createGetRequest(null,new Configuration().getProcessingParameters(params));
        assert req.getHttpMethod() == HttpMethod.GET : "GET by default";
        assert req.getPath().equals("java.lang/type=Memory") : "Path extracted";
    }


    @Test
    public void readWithPattern() {
        JmxReadRequest req = JmxRequestFactory.createGetRequest("read/java.lang:type=*",procParams);
        assert req.getType() == RequestType.READ : "Type is read";
        assert req.getObjectName().getCanonicalName().equals("java.lang:type=*") : "Name properly parsed";
        assert req.getObjectName().isPattern() : "Name is pattern";
        assert req.getAttributeNames() == null : "No attributes names";
        assert req.getAttributeName() == null : "No attributes names";
        assert req.getPath() == null : "Path is null";
        req = JmxRequestFactory.createGetRequest("read/java.lang:type=*/HeapMemoryUsage",procParams);
        assert req.getAttributeName().equals("HeapMemoryUsage") : "No attributes names";

    }

    @Test
    public void readWithPatternAndAttribute() {
        JmxReadRequest req = JmxRequestFactory.createGetRequest("read/java.lang:type=*/",procParams);
        assert req.getType() == RequestType.READ : "Type is read";
        assert req.getObjectName().getCanonicalName().equals("java.lang:type=*") : "Name properly parsed";
        assert req.getObjectName().isPattern() : "Name is pattern";
        assert req.getAttributeNames() == null : "No attributes names";
        assert req.getAttributeName() == null : "No attributes names";
        assert req.getPath() == null : "Path is null";
    }

    @Test(expectedExceptions = { ClassCastException.class } )
    public void castException() {
        JmxReadRequest req = JmxRequestFactory.createGetRequest("exec/java.lang:type=Memory/gc", procParams);
    }

}
