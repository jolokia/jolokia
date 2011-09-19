package org.jolokia.request;

/*
 *  Copyright 2009-2010 Roland Huss
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

import java.util.Arrays;
import java.util.List;

import javax.management.MalformedObjectNameException;

import org.jolokia.util.StringUtil;
import org.jolokia.util.RequestType;
import org.json.simple.JSONObject;
import org.testng.annotations.Test;

import static org.jolokia.request.JmxRequestBuilder.createMap;
import static org.testng.Assert.*;
import static org.testng.Assert.assertTrue;


/**
 * @author roland
 * @since Apr 15, 2010
 */
public class JmxRequestTest {

    @Test
    public void testPathSplitting() throws MalformedObjectNameException {
        List<String> paths = StringUtil.parsePath("hello/world");
        assertEquals(paths.size(),2);
        assertEquals(paths.get(0),"hello");
        assertEquals(paths.get(1),"world");

        paths = StringUtil.parsePath("hello!/world/second");
        assertEquals(paths.size(),2);
        assertEquals(paths.get(0),"hello/world");
        assertEquals(paths.get(1),"second");
    }

    @Test
    public void testPathGlueing() throws MalformedObjectNameException {
        String path = StringUtil.combineToPath(Arrays.asList("hello/world", "second"));
        assertEquals(path,"hello!/world/second");
    }

    // =================================================================================

    @Test
    public void readRequest() {
        for (JmxReadRequest req : new JmxReadRequest[] {
                (JmxReadRequest) JmxRequestFactory.createGetRequest("read/java.lang:type=Memory/HeapMemoryUsage/used", null),
                (JmxReadRequest) JmxRequestFactory.createPostRequest(
                        createMap("type", "read", "mbean", "java.lang:type=Memory",
                                  "attribute","HeapMemoryUsage",
                                  "path","used"),null)
        }) {
            assertEquals(req.getType(),RequestType.READ);
            assertEquals(req.getObjectNameAsString(),"java.lang:type=Memory");
            assertEquals(req.getAttributeName(),"HeapMemoryUsage");
            assertEquals(req.getPath(),"used");

            verify(req,"type","read");
            verify(req,"mbean","java.lang:type=Memory");
            verify(req,"attribute","HeapMemoryUsage");
            verify(req,"path","used");
        }
    }

    @Test
    public void readRequestMultiAttributes() {
        for (JmxReadRequest req : new JmxReadRequest[] {
                (JmxReadRequest) JmxRequestFactory.createGetRequest("read/java.lang:type=Memory/HeapMemoryUsage/used", null),
                (JmxReadRequest) JmxRequestFactory.createPostRequest(
                        createMap("type", "read", "mbean", "java.lang:type=Memory",
                                  "attribute","HeapMemoryUsage",
                                  "path","used"),null)
        }) {
            assertEquals(req.getType(),RequestType.READ);
            assertEquals(req.getObjectNameAsString(),"java.lang:type=Memory");
            assertEquals(req.getAttributeName(),"HeapMemoryUsage");
            assertEquals(req.getPath(),"used");

            verify(req,"type","read");
            verify(req,"mbean","java.lang:type=Memory");
            verify(req,"attribute","HeapMemoryUsage");
            verify(req,"path","used");
        }
    }

    @Test
    public void writeRequest() {
        for (JmxWriteRequest req : new JmxWriteRequest[] {
                (JmxWriteRequest) JmxRequestFactory.createGetRequest("write/java.lang:type=Memory/Verbose/true/bla", null),
                (JmxWriteRequest) JmxRequestFactory.createPostRequest(
                        createMap("type", "write", "mbean", "java.lang:type=Memory",
                                  "attribute","Verbose",
                                  "value", "true",
                                  "path","bla"),null)
        }) {
            assertEquals(req.getType(),RequestType.WRITE);
            assertEquals(req.getObjectNameAsString(),"java.lang:type=Memory");
            assertEquals(req.getAttributeName(),"Verbose");
            assertEquals(req.getValue(),"true");
            assertEquals(req.getPath(),"bla");

            verify(req,"type","write");
            verify(req,"mbean","java.lang:type=Memory");
            verify(req,"attribute","Verbose");
            verify(req,"value","true");
            verify(req,"path","bla");
        }
    }

    @Test
    public void listRequest() {
        for (JmxListRequest req : new JmxListRequest[] {
                (JmxListRequest) JmxRequestFactory.createGetRequest("list/java.lang:type=Memory", null),
                (JmxListRequest) JmxRequestFactory.createPostRequest(
                        createMap("type", "list", "path", "java.lang:type=Memory"),null)
        }) {
            assertEquals(req.getType(), RequestType.LIST);
            assertEquals(req.getPath(),"java.lang:type=Memory");

            verify(req,"type","list");
            verify(req,"path","java.lang:type=Memory");
        }
    }

    @Test
    public void versionRequest() {
        for (JmxVersionRequest req : new JmxVersionRequest[] {
                (JmxVersionRequest) JmxRequestFactory.createGetRequest("version/java.lang:type=Memory", null),
                (JmxVersionRequest) JmxRequestFactory.createPostRequest(
                        createMap("type", "version", "path", "java.lang:type=Memory"),null)
        }) {
            assertEquals(req.getType(),RequestType.VERSION);
            verify(req,"type","version");
        }
    }

    @Test
    public void execRequest() {
        List args = Arrays.asList(null,"","normal");
        for (JmxExecRequest req : new JmxExecRequest[] {
                (JmxExecRequest) JmxRequestFactory.createGetRequest("exec/java.lang:type=Runtime/operation/[null]/\"\"/normal", null),
                (JmxExecRequest) JmxRequestFactory.createPostRequest(
                        createMap("type", "exec", "mbean", "java.lang:type=Runtime",
                                  "operation","operation","arguments", args), null)
        }) {
            assertEquals(req.getType(),RequestType.EXEC);
            assertEquals(req.getOperation(),"operation");
            assertNull(req.getArguments().get(0));
            assertEquals(req.getArguments().get(1), "");
            assertEquals(req.getArguments().get(2), "normal");

            verify(req,"type","exec");
            verify(req,"operation","operation");
            verify(req, "mbean", "java.lang:type=Runtime");
            JSONObject json = req.toJSON();
            List args2 = (List) json.get("arguments");
            assertEquals(args2.get(0),null);
            assertEquals(args2.get(1),"");
            assertEquals(args2.get(2),"normal");
        }
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void invalidExecRequest() {
        JmxRequestFactory.createGetRequest("exec/java.lang:type=Runtime",null);
    }

    @Test
    public void searchRequest() {

        for (JmxSearchRequest req : new JmxSearchRequest[] {
                (JmxSearchRequest) JmxRequestFactory.createGetRequest("search/java.lang:*", null),
                (JmxSearchRequest) JmxRequestFactory.createPostRequest(
                        createMap("type", "search", "mbean", "java.lang:*"),null)
        }) {
            assertEquals(req.getType(),RequestType.SEARCH);
            assertEquals(req.getObjectName().getCanonicalName(),"java.lang:*");
            assertTrue(req.getObjectName().isPattern());

            verify(req,"type","search");
            verify(req,"mbean","java.lang:*");
        }
    }

    private void verify(JmxRequest pReq, String pKey, String pValue) {
        JSONObject json = pReq.toJSON();
        assertEquals(json.get(pKey),pValue);
        String info = pReq.toString();
        if (pKey.equals("type")) {
            String val = pValue.substring(0,1).toUpperCase() + pValue.substring(1);
            assertTrue(info.contains(val));
        } else {
            assertTrue(info.contains(pValue));
        }
    }


}
