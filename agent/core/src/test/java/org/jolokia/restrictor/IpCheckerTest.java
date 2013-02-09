package org.jolokia.restrictor;

import org.jolokia.util.IpChecker;
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
                // IP-tocheck, expected net/ip, result
                { "10.0.15.16", "10.0.15.16", "true" },
                { "10.0.15.16", "10.0.0.1/16", "true"},
                { "10.0.15.16", "10.0.0.1/24", "false"},
                { "10.0.15.16", "10.0.0.1/255.255.0.0", "true"},
                { "10.0.15.16", "10.0.0.1/255.255.1.0", "false"},
        };
        for (int i = 0; i < fixture.length; i ++) {
            String result = IpChecker.matches(fixture[i][1],fixture[i][0]) ?
                    "true" : "false";
            assertEquals("Expected mask: " + fixture[i][1] + ", IP to check: " + fixture[i][0],
                         fixture[i][2],result);
        }
    }

    @Test
    public void invalidFormat() {
        try {
            IpChecker.matches("10.0.16.27.8","10.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException exp) {}
        try {
            IpChecker.matches("10.0.16.27","10.0.16.8.b");
            fail("Invalid IP");
        } catch (IllegalArgumentException exp) {}
        try {
            IpChecker.matches("10.0.16.27/43434","10.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException exp) {}
        try {
            IpChecker.matches("10.0.16.27.13/24","10.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException exp) {}
        try {
            IpChecker.matches("A.0.16.27/24", "10.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException exp) {}

        try {
            IpChecker.matches("10.0.16.27/24","A.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException exp) {}
        try {
            IpChecker.matches("10.0.16.27/255.255.255.255.255","10.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException exp) {}
        try {
            IpChecker.matches("10.0.16.27/35","10.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException exp) {}
        try {
            IpChecker.matches("10.0.16.27/500.255.255.255","10.0.16.8");
            fail("Invalid IP");
        } catch (IllegalArgumentException exp) {}

    }

}

