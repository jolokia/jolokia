package org.jolokia.restrictor;

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

import java.io.InputStream;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.util.HttpMethod;
import org.jolokia.util.RequestType;
import org.testng.annotations.Test;

import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author roland
 * @since Jul 29, 2009
 */
public class PolicyBasedRestrictorTest {

    @Test
    public void basics() throws MalformedObjectNameException {
        InputStream is = getClass().getResourceAsStream("/access-sample1.xml");
        PolicyRestrictor restrictor = new PolicyRestrictor(is);
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"Verbose"));
        assertFalse(restrictor.isAttributeWriteAllowed(new ObjectName("java.lang:type=Memory"),"Verbose"));
        assertFalse(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"NonHeapMemoryUsage"));
        assertTrue(restrictor.isOperationAllowed(new ObjectName("java.lang:type=Memory"),"gc"));
        assertFalse(restrictor.isOperationAllowed(new ObjectName("java.lang:type=Threading"),"gc"));
        assertTrue(restrictor.isHttpMethodAllowed(HttpMethod.POST));
        assertFalse(restrictor.isHttpMethodAllowed(HttpMethod.GET));
    }

    @Test
    public void restrictIp() {
        InputStream is = getClass().getResourceAsStream("/access-sample1.xml");
        PolicyRestrictor restrictor = new PolicyRestrictor(is);

        String ips[][] = {
                { "11.0.18.32", "true" },
                { "planck", "true" },
                { "heisenberg", "false" },
                { "10.0.11.125", "true" },
                { "10.0.11.126", "false" },
                { "11.1.18.32", "false" },
                { "192.168.15.3", "true" },
                { "192.168.15.8", "true" },
                { "192.168.16.3", "false" }
        };

        for (String check[] : ips) {
            String res = restrictor.isRemoteAccessAllowed(check[0]) ? "true" : "false";
            assertEquals("Ip " + check[0] + " is " +
                         (check[1].equals("false") ? "not " : "") +
                         "allowed",check[1],res);
        }
    }

    @Test
    public void patterns() throws MalformedObjectNameException {
        InputStream is = getClass().getResourceAsStream("/access-sample2.xml");
        PolicyRestrictor restrictor = new PolicyRestrictor(is);
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"HeapMemoryUsage"));
        assertFalse(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"NonHeapMemoryUsage"));
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("jolokia:type=Config,name=Bla"),"Debug"));
        assertFalse(restrictor.isOperationAllowed(new ObjectName("jolokia:type=Threading"),"gc"));

        // No hosts set.
        assertTrue(restrictor.isRemoteAccessAllowed("10.0.1.125"));

    }

    @Test
    public void noRestrictions() throws MalformedObjectNameException {
        InputStream is = getClass().getResourceAsStream("/access-sample3.xml");
        PolicyRestrictor restrictor = new PolicyRestrictor(is);
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"HeapMemoryUsage"));
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"NonHeapMemoryUsage"));
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("jolokia:type=Config,name=Bla"),"Debug"));
        assertTrue(restrictor.isOperationAllowed(new ObjectName("jolokia:type=Threading"),"gc"));
        assertTrue(restrictor.isTypeAllowed(RequestType.READ));
        assertTrue(restrictor.isHttpMethodAllowed(HttpMethod.GET));
        assertTrue(restrictor.isHttpMethodAllowed(HttpMethod.POST));
    }


    @Test
    public void deny() throws MalformedObjectNameException {
        InputStream is = getClass().getResourceAsStream("/access-sample4.xml");
        PolicyRestrictor restrictor = new PolicyRestrictor(is);
        assertFalse(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"HeapMemoryUsage"));
        assertFalse(restrictor.isAttributeWriteAllowed(new ObjectName("java.lang:type=Memory"),"HeapMemoryUsage"));
        assertFalse(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"NonHeapMemoryUsage"));
        assertTrue(restrictor.isAttributeWriteAllowed(new ObjectName("java.lang:type=Memory"),"NonHeapMemoryUsage"));
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"BlaUsage"));

        assertFalse(restrictor.isAttributeReadAllowed(new ObjectName("jolokia:type=Config"),"Debug"));

        assertFalse(restrictor.isOperationAllowed(new ObjectName("java.lang:type=Blubber,name=x"),"gc"));
        assertTrue(restrictor.isOperationAllowed(new ObjectName("java.lang:type=Blubber,name=x"),"xavier"));
    }

    @Test
    public void allow() throws MalformedObjectNameException {
        InputStream is = getClass().getResourceAsStream("/access-sample5.xml");
        PolicyRestrictor restrictor = new PolicyRestrictor(is);
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"), "HeapMemoryUsage"));
        assertTrue(restrictor.isAttributeWriteAllowed(new ObjectName("java.lang:type=Memory"), "HeapMemoryUsage"));
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"), "NonHeapMemoryUsage"));
        assertFalse(restrictor.isAttributeWriteAllowed(new ObjectName("java.lang:type=Memory"), "NonHeapMemoryUsage"));
        assertFalse(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"), "BlaUsage"));

        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("jolokia:type=Config"), "Debug"));

        assertTrue(restrictor.isOperationAllowed(new ObjectName("java.lang:type=Blubber,name=x"), "gc"));
        assertFalse(restrictor.isOperationAllowed(new ObjectName("java.lang:type=Blubber,name=x"), "xavier"));
    }

    @Test
    public void illegalXml() {
        InputStream is = getClass().getResourceAsStream("/illegal1.xml");
        try {
            PolicyRestrictor restrictor = new PolicyRestrictor(is);
            fail("Could parse illegal file");
        } catch (SecurityException exp) {
            //ok
        }

        try {
            new PolicyRestrictor(null);
            fail("No file given");
        } catch (SecurityException exp) {
            // ok
        }
    }

    @Test
    public void noName() {
        InputStream is = getClass().getResourceAsStream("/illegal2.xml");
        try {
            PolicyRestrictor restrictor = new PolicyRestrictor(is);
            fail("Could parse illegal file");
        } catch (SecurityException exp) {
            assertTrue(exp.getMessage().contains("name"));
        }
    }

    @Test
    public void invalidTag() {
        InputStream is = getClass().getResourceAsStream("/illegal3.xml");
        try {
            PolicyRestrictor restrictor = new PolicyRestrictor(is);
            fail("Could parse illegal file");
        } catch (SecurityException exp) {
            assertTrue(exp.getMessage().contains("name"));
            assertTrue(exp.getMessage().contains("attribute"));
            assertTrue(exp.getMessage().contains("operation"));
            assertTrue(exp.getMessage().contains("bla"));
        }
    }

    @Test
    public void doubleName() {
        InputStream is = getClass().getResourceAsStream("/illegal4.xml");
        try {
            PolicyRestrictor restrictor = new PolicyRestrictor(is);
            fail("Could parse illegal file");
        } catch (SecurityException exp) {
            assertTrue(exp.getMessage().contains("name"));
        }

    }


    @Test
    public void httpMethod() {
        InputStream is = getClass().getResourceAsStream("/method.xml");
        PolicyRestrictor res = new PolicyRestrictor(is);
        assertTrue(res.isHttpMethodAllowed(HttpMethod.GET));
        assertTrue(res.isHttpMethodAllowed(HttpMethod.POST));
    }

    @Test
    public void illegalHttpMethod() {
        InputStream is = getClass().getResourceAsStream("/illegal5.xml");
        try {
            new PolicyRestrictor(is);
            fail();
        } catch (SecurityException exp) {
            assertTrue(exp.getMessage().contains("BLA"));
        }
    }

    @Test
    public void illegalHttpMethodTag() {
        InputStream is = getClass().getResourceAsStream("/illegal6.xml");
        try {
            new PolicyRestrictor(is);
            fail();
        } catch (SecurityException exp) {
            assertTrue(exp.getMessage().contains("method"));
            assertTrue(exp.getMessage().contains("blubber"));
        }
    }

    @Test
    public void cors() {
        InputStream is = getClass().getResourceAsStream("/allow-origin4.xml");
        PolicyRestrictor restrictor = new PolicyRestrictor(is);

        for (boolean strict : new boolean[] {true, false}) {
            assertFalse(restrictor.isOriginAllowed(null, strict));
            assertTrue(restrictor.isOriginAllowed("http://bla.com", strict));
            assertFalse(restrictor.isOriginAllowed("http://www.jolokia.org", strict));
            assertTrue(restrictor.isOriginAllowed("https://www.consol.de", strict));
        }
    }

    @Test
    public void corsStrictCheckingOff() {
        InputStream is = getClass().getResourceAsStream("/allow-origin1.xml");
        PolicyRestrictor restrictor = new PolicyRestrictor(is);

        // Allways true since we want a strict check but strict checking is off.
        assertTrue(restrictor.isOriginAllowed(null, true));
        assertTrue(restrictor.isOriginAllowed("http://bla.com", true));
        assertTrue(restrictor.isOriginAllowed("http://www.jolokia.org", true));
        assertTrue(restrictor.isOriginAllowed("https://www.consol.de", true));
    }

    @Test
    public void corsWildCard() {
        InputStream is = getClass().getResourceAsStream("/allow-origin2.xml");
        PolicyRestrictor restrictor = new PolicyRestrictor(is);

        assertTrue(restrictor.isOriginAllowed(null, false));
        assertTrue(restrictor.isOriginAllowed("http://bla.com", false));
        assertTrue(restrictor.isOriginAllowed("http://www.jolokia.org", false));
        assertTrue(restrictor.isOriginAllowed("http://www.consol.de", false));
    }

    @Test
    public void corsEmpty() {
        InputStream is = getClass().getResourceAsStream("/allow-origin3.xml");
        PolicyRestrictor restrictor = new PolicyRestrictor(is);

        assertTrue(restrictor.isOriginAllowed(null, false));
        assertTrue(restrictor.isOriginAllowed("http://bla.com", false));
        assertTrue(restrictor.isOriginAllowed("http://www.jolokia.org", false));
        assertTrue(restrictor.isOriginAllowed("http://www.consol.de", false));
    }

    @Test
    public void corsNoTags() {
        InputStream is = getClass().getResourceAsStream("/access-sample1.xml");
        PolicyRestrictor restrictor = new PolicyRestrictor(is);

        assertTrue(restrictor.isOriginAllowed("http://bla.com", false));
        assertTrue(restrictor.isOriginAllowed("http://www.jolokia.org", false));
        assertTrue(restrictor.isOriginAllowed("https://www.consol.de", false));
    }


}
