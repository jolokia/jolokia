/*
 * Copyright 2009-2024 Roland Huss
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
package org.jolokia.server.core.restrictor;

import java.io.IOException;
import java.io.InputStream;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jolokia.server.core.restrictor.policy.PolicyRestrictor;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class MBeanFilterTest {

    private static PolicyRestrictor restrictor;

    @BeforeClass
    public static void init() throws IOException {
        InputStream is = MBeanFilterTest.class.getResourceAsStream("/filter-1.xml");
        restrictor = new PolicyRestrictor(is);
    }

    @Test
    public void patternSplitting() {
        String[] prefixSuffix1 = "*suffix".split("\\*");
        String[] prefixSuffix2 = "prefix*".split("\\*");
        String[] prefixSuffix3 = "prefix*suffix".split("\\*");
        assertEquals(prefixSuffix1[0], "");
        assertEquals(prefixSuffix1[1], "suffix");
        assertEquals(prefixSuffix2[0], "prefix");
        assertEquals(prefixSuffix2.length, 1); // weird...
        assertEquals(prefixSuffix3[0], "prefix");
        assertEquals(prefixSuffix3[1], "suffix");
    }

    @Test
    public void objectNamePatterns() {
        try {
            assertEquals(new ObjectName("*:*").getDomain(), "*");
        } catch (MalformedObjectNameException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void filteringMBeanNames() throws Exception {
        assertFalse(restrictor.isObjectNameHidden(new ObjectName("domain0:type=any")));
        assertTrue(restrictor.isObjectNameHidden(new ObjectName("domain1:type=any")));
        assertTrue(restrictor.isObjectNameHidden(new ObjectName("domain2:type=any")));
        assertTrue(restrictor.isObjectNameHidden(new ObjectName("domain3:type=any")));
        assertTrue(restrictor.isObjectNameHidden(new ObjectName("domain3.org:type=any")));
        assertTrue(restrictor.isObjectNameHidden(new ObjectName("example.domain4:type=any")));
        assertFalse(restrictor.isObjectNameHidden(new ObjectName("domain.tld.com4:type=any")));
        assertTrue(restrictor.isObjectNameHidden(new ObjectName("domain6:type=address")));
        assertFalse(restrictor.isObjectNameHidden(new ObjectName("domain6:type=any")));
        assertTrue(restrictor.isObjectNameHidden(new ObjectName("domain6:type=address,name=Guybrush")));
        assertFalse(restrictor.isObjectNameHidden(new ObjectName("domain7:address=/dev/null")));
        assertFalse(restrictor.isObjectNameHidden(new ObjectName("domain7:address=/dev/null,destination=aVoid")));
        assertTrue(restrictor.isObjectNameHidden(new ObjectName("domain7:address=/dev/null,destination=Void")));
        assertFalse(restrictor.isObjectNameHidden(new ObjectName("domain7:destination=VoidSpace")));
    }

}
