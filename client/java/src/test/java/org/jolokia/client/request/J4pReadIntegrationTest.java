package org.jolokia.client.request;

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

import java.util.*;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.jolokia.client.exception.J4pException;
import org.jolokia.client.exception.J4pRemoteException;
import org.testng.annotations.Test;

import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertEquals;

/**
 * Integration test for reading attributes
 *
 * @author roland
 * @since Apr 27, 2010
 */
public class J4pReadIntegrationTest extends AbstractJ4pIntegrationTest {

    @Test
    public void nameTest() throws MalformedObjectNameException, J4pException {
        checkNames(HttpGet.METHOD_NAME,itSetup.getStrangeNames(),itSetup.getEscapedNames());
        checkNames(HttpPost.METHOD_NAME,itSetup.getStrangeNames(),itSetup.getEscapedNames());
    }

    @Test
    public void errorTest() throws MalformedObjectNameException, J4pException {
        J4pReadRequest req = new J4pReadRequest("no.domain:name=vacuum","oxygen");
        try {
            J4pReadResponse resp = j4pClient.execute(req);
            fail();
        } catch (J4pRemoteException exp) {
            assertEquals(404,exp.getStatus());
            assertTrue(exp.getMessage().contains("InstanceNotFoundException"));
            assertTrue(exp.getRemoteStackTrace().contains("InstanceNotFoundException"));
        }
    }
        
    @Test
    public void multipleAttributes() throws MalformedObjectNameException, J4pException {
        J4pReadRequest req = new J4pReadRequest(itSetup.getAttributeMBean(),"LongSeconds","SmallMinutes");
        J4pReadResponse resp = j4pClient.execute(req);
        assertFalse(req.hasSingleAttribute());
        assertEquals(2,req.getAttributes().size());
        Map respVal = resp.getValue();
        assertTrue(respVal.containsKey("LongSeconds"));
        assertTrue(respVal.containsKey("SmallMinutes"));

        Collection<String> attrs = resp.getAttributes(new ObjectName(itSetup.getAttributeMBean()));
        Set<String> attrSet = new HashSet<String>(attrs);
        assertTrue(attrSet.contains("LongSeconds"));
        assertTrue(attrSet.contains("SmallMinutes"));

        try {
            resp.getAttributes(new ObjectName("blub:type=bla"));
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains(itSetup.getAttributeMBean()));
        }

        Set<String> allAttrs = new HashSet<String>(resp.getAttributes());
        assertEquals(2,allAttrs.size());
        assertTrue(allAttrs.contains("LongSeconds"));
        assertTrue(allAttrs.contains("SmallMinutes"));

        String val = resp.getValue(new ObjectName(itSetup.getAttributeMBean()),"SmallMinutes");
        assertNotNull(val);

        try {
            resp.getValue(new ObjectName(itSetup.getAttributeMBean()),"Aufsteiger");
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("Aufsteiger"));
        }

        String longVal = resp.getValue("LongSeconds");
        assertNotNull(longVal);

        try {
            resp.getValue("Pinola bleibt");
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("Pinola"));
        }

        try {
            resp.getValue(null);
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("null"));
        }

        try {
            req.getAttribute();
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("than one"));
        }
    }

    @Test
    public void mbeanPattern() throws MalformedObjectNameException, J4pException {
        J4pReadRequest req = new J4pReadRequest("*:type=attribute","LongSeconds");
        J4pReadResponse resp = j4pClient.execute(req);
        assertEquals(1,resp.getObjectNames().size());
        Map respVal = resp.getValue();
        assertTrue(respVal.containsKey(itSetup.getAttributeMBean()));
        Map attrs = (Map) respVal.get(itSetup.getAttributeMBean());
        assertEquals(1,attrs.size());
        assertTrue(attrs.containsKey("LongSeconds"));

        Set<String> attrSet = new HashSet<String>(resp.getAttributes(new ObjectName(itSetup.getAttributeMBean())));
        assertEquals(1,attrSet.size());
        assertTrue(attrSet.contains("LongSeconds"));

        try {
            resp.getAttributes(new ObjectName("blub:type=bla"));
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("blub:type=bla"));
        }

        try {
            resp.getAttributes();
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("*:type=attribute"));
        }

        try {
            resp.getValue("LongSeconds");
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("non-pattern"));
        }
    }

    @Test
    public void mbeanPatternWithAttributes() throws MalformedObjectNameException, J4pException {
        J4pReadRequest req = new J4pReadRequest("*:type=attribute","LongSeconds","List");
        assertNull(req.getPath());
        J4pReadResponse resp = j4pClient.execute(req);
        assertEquals(1,resp.getObjectNames().size());
        Map respVal = resp.getValue();
        Map attrs = (Map) respVal.get(itSetup.getAttributeMBean());
        assertEquals(2,attrs.size());
        assertTrue(attrs.containsKey("LongSeconds"));
        assertTrue(attrs.containsKey("List"));

        String longVal = resp.getValue(new ObjectName(itSetup.getAttributeMBean()),"LongSeconds");
        assertNotNull(longVal);

        try {
            resp.getValue(new ObjectName(itSetup.getAttributeMBean()),"FCN");
            fail();
        } catch (IllegalArgumentException exp) {
            assertTrue(exp.getMessage().contains("FCN"));
        }
    }


    private void checkNames(String pMethod, List<String> ... pNames) throws MalformedObjectNameException, J4pException {
        for (int i = 0;i<pNames.length;i++) {
            for (String name : pNames[i]) {
                System.out.println(name);
                ObjectName oName =  new ObjectName(name);
                J4pReadRequest req = new J4pReadRequest(oName,"Ok");
                req.setPreferredHttpMethod(pMethod);
                J4pReadResponse resp = j4pClient.execute(req);
                Collection names = resp.getObjectNames();
                assertEquals(1,names.size());
                assertEquals(oName,names.iterator().next());
                assertEquals("OK",resp.getValue());
                Collection<String> attrs = resp.getAttributes();
                assertEquals(1,attrs.size());

                assertNotNull(resp.getValue("Ok"));
                try {
                    resp.getValue("Koepke");
                    fail();
                } catch (IllegalArgumentException exp) {
                    assertTrue(exp.getMessage().contains("Koepke"));
                }
            }
        }
    }

}
