package org.jolokia.config;

import org.testng.annotations.Test;

import static org.testng.Assert.fail;
import static org.testng.AssertJUnit.assertEquals;

/*
 * jmx4perl - WAR Agent for exporting JMX via JSON
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
            IpChecker.matches("A.0.16.27/24","10.0.16.8");
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

