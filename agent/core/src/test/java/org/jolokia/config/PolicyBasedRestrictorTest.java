package org.jolokia.config;

import org.jolokia.config.PolicyBasedRestrictor;
import org.jolokia.JmxRequest;
import org.testng.annotations.Test;

import java.io.InputStream;

import javax.management.ObjectName;
import javax.management.MalformedObjectNameException;

import static org.testng.Assert.*;
import static org.testng.AssertJUnit.assertEquals;

/*
 * jolokia - WAR Agent for exporting JMX via JSON
 *
 * Copyright (C) 2009 Roland Huß, roland@cpan.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * A commercial license is available as well. Please contact roland@cpan.org for
 * further details.
 */

/**
 * @author roland
 * @since Jul 29, 2009
 */
public class PolicyBasedRestrictorTest {

    @Test
    public void basics() throws MalformedObjectNameException {
        InputStream is = getClass().getResourceAsStream("/access-sample1.xml");
        PolicyBasedRestrictor restrictor = new PolicyBasedRestrictor(is);
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"Verbose"));
        assertFalse(restrictor.isAttributeWriteAllowed(new ObjectName("java.lang:type=Memory"),"Verbose"));
        assertFalse(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"NonHeapMemoryUsage"));
        assertTrue(restrictor.isOperationAllowed(new ObjectName("java.lang:type=Memory"),"gc"));
        assertFalse(restrictor.isOperationAllowed(new ObjectName("java.lang:type=Threading"),"gc"));
    }

    @Test
    public void restrictIp() {
        InputStream is = getClass().getResourceAsStream("/access-sample1.xml");
        PolicyBasedRestrictor restrictor = new PolicyBasedRestrictor(is);

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
        PolicyBasedRestrictor restrictor = new PolicyBasedRestrictor(is);
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
        PolicyBasedRestrictor restrictor = new PolicyBasedRestrictor(is);
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"HeapMemoryUsage"));
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"NonHeapMemoryUsage"));
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("jolokia:type=Config,name=Bla"),"Debug"));
        assertTrue(restrictor.isOperationAllowed(new ObjectName("jolokia:type=Threading"),"gc"));
        assertTrue(restrictor.isTypeAllowed(JmxRequest.Type.READ));
    }

    @Test
    public void deny() throws MalformedObjectNameException {
        InputStream is = getClass().getResourceAsStream("/access-sample4.xml");
        PolicyBasedRestrictor restrictor = new PolicyBasedRestrictor(is);
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
        PolicyBasedRestrictor restrictor = new PolicyBasedRestrictor(is);
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"HeapMemoryUsage"));
        assertTrue(restrictor.isAttributeWriteAllowed(new ObjectName("java.lang:type=Memory"),"HeapMemoryUsage"));
        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"NonHeapMemoryUsage"));
        assertFalse(restrictor.isAttributeWriteAllowed(new ObjectName("java.lang:type=Memory"),"NonHeapMemoryUsage"));
        assertFalse(restrictor.isAttributeReadAllowed(new ObjectName("java.lang:type=Memory"),"BlaUsage"));

        assertTrue(restrictor.isAttributeReadAllowed(new ObjectName("jolokia:type=Config"),"Debug"));

        assertTrue(restrictor.isOperationAllowed(new ObjectName("java.lang:type=Blubber,name=x"),"gc"));
        assertFalse(restrictor.isOperationAllowed(new ObjectName("java.lang:type=Blubber,name=x"),"xavier"));
    }

    @Test
    public void illegalXml() {
        InputStream is = getClass().getResourceAsStream("/illegal1.xml");
        try {
            PolicyBasedRestrictor restrictor = new PolicyBasedRestrictor(is);
            fail("Could parse illegal file");
        } catch (SecurityException exp) {
            //ok
        }

        try {
            new PolicyBasedRestrictor(null);
            fail("No file given");
        } catch (SecurityException exp) {
            // ok
        }
    }

    @Test
    public void noName() {
        InputStream is = getClass().getResourceAsStream("/illegal2.xml");
        try {
            PolicyBasedRestrictor restrictor = new PolicyBasedRestrictor(is);
            fail("Could parse illegal file");
        } catch (SecurityException exp) {
            assertTrue(exp.getMessage().contains("name"));
        }
    }

    @Test
    public void invalidTag() {
        InputStream is = getClass().getResourceAsStream("/illegal3.xml");
        try {
            PolicyBasedRestrictor restrictor = new PolicyBasedRestrictor(is);
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
            PolicyBasedRestrictor restrictor = new PolicyBasedRestrictor(is);
            fail("Could parse illegal file");
        } catch (SecurityException exp) {
            assertTrue(exp.getMessage().contains("name"));
        }

    }
}
