package org.jolokia.server.core.restrictor;

import org.jolokia.server.core.util.IpChecker;
import org.testng.annotations.Test;

import static org.testng.Assert.fail;
import static org.testng.AssertJUnit.assertEquals;

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


/**
 * @author roland
 * @since Oct 8, 2009
 */
public class IpCheckerTest {

    @Test
    public void basics() {
        String [][] fixture = new String[][]{
                // IP-to check, expected net/ip, result
                { "10.0.15.16", "10.0.15.16", "true" },
                { "10.0.15.16", "10.0.0.1/16", "true"},
                { "10.0.15.16", "10.0.0.1/24", "false"},
                { "10.0.15.16", "10.0.0.1/255.255.0.0", "true"},
                { "10.0.15.16", "10.0.0.1/255.255.1.0", "false"},
        };
        for (String[] strings : fixture) {
            String result = IpChecker.matches(strings[1], strings[0]) ?
                    "true" : "false";
            assertEquals("Expected mask: " + strings[1] + ", IP to check: " + strings[0],
                         strings[2], result);
        }
    }

    @Test
    public void basicsIPv6() {
        String [][] fixture = new String[][]{
                // IP-to check, expected net/ip, result
                { "[2001:db8::1]", "2001:db8::1", "true" },
                { "2001:db8::1", "2001:db8::1", "true" },
                { "2001:db8::1", "2001:db8::0:1", "true" }, // yes we can
                { "2001:db8::1", "2001:db8::3:1", "false" },
                { "2001:db8:1:2:3:4:5:6", "2001:db8:1:2:3:4:5:6", "true"},
                { "2001:db8:1:2:3:4:5:6", "2001:db8:1:2::/64", "true"},
                { "2001:db8:1:2:3:4:5:6", "[2001:db8:1:2::]/64", "true"},
                { "2001:db8:1:2:3:4:5:6", "2001:db8:2:2::/72", "false"},
                { "2001:db8:2:2:3:4:5:6", "2001:db8:2:2::/72", "true"},
                { "2001:db8:1:2:3:4:5:6", "[2001:db8:2:2::]/72", "false"},
        };
        for (String[] strings : fixture) {
            String result = IpChecker.matches(strings[1], strings[0]) ?
                    "true" : "false";
            assertEquals("Expected CIDR: " + strings[1] + ", IP to check: " + strings[0],
                         strings[2], result);
        }
    }

    @Test
    public void invalidFormat() {
        try {
            IpChecker.matches("10.0.16.27.8","10.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException ignored) {}
        try {
            IpChecker.matches("10.0.16.27","10.0.16.8.b");
            fail("Invalid IP");
        } catch (IllegalArgumentException ignored) {}
        try {
            IpChecker.matches("10.0.16.27/43434","10.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException ignored) {}
        try {
            IpChecker.matches("10.0.16.27.13/24","10.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException ignored) {}
        try {
            IpChecker.matches("A.0.16.27/24", "10.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException ignored) {}

        try {
            IpChecker.matches("10.0.16.27/24","A.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException ignored) {}
        try {
            IpChecker.matches("10.0.16.27/255.255.255.255.255","10.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException ignored) {}
        try {
            IpChecker.matches("10.0.16.27/35","10.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException ignored) {}
        try {
            IpChecker.matches("10.0.16.27/500.255.255.255","10.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException ignored) {}
    }

    @Test
    public void invalidFormatIPv6() {
        try {
            IpChecker.matches("2001:db8:0:0:0:0:0:1:8","2001:db8:0:0:0:0:0:1");
            fail("Invalid IP");
        } catch (IllegalArgumentException ignored) {}
        try {
            IpChecker.matches("2001:db8:0:0:0:0:0:1","2001:db8:0:0:0:0:0:1:b");
            fail("Invalid IP");
        } catch (IllegalArgumentException ignored) {}
        try {
            IpChecker.matches("2001:db8:0:0:0:0:0:1/129","2001:db8:0:0:0:0:0:1");
            fail("Invalid IP");
        } catch (IllegalArgumentException ignored) {}
        try {
            IpChecker.matches("2001:db8:0:0:0:0:0:1:3/24","2001:db8:0:0:0:0:0:1");
            fail("Invalid IP");
        } catch (IllegalArgumentException ignored) {}
    }

}
