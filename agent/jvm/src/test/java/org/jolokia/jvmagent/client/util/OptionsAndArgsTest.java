package org.jolokia.jvmagent.client.util;

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
import java.util.regex.Pattern;

import org.jolokia.jvmagent.client.command.CommandDispatcher;
import org.jolokia.util.EscapeUtil;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 12.08.11
 */
@Test
public class OptionsAndArgsTest {

    private OptionsAndArgs opts(String ... args) {
        return  new OptionsAndArgs(CommandDispatcher.getAvailableCommands(),args);
    }

    @Test
    public void help() {
        OptionsAndArgs o = opts("--help","--verbose");
        assertEquals(o.getCommand(), "help");
        assertFalse(o.isQuiet());
        assertTrue(o.isVerbose());
    }

    @Test
    public void simple() {
        OptionsAndArgs o = opts("--host","localhost","start","12","--password=bla","-u","roland","--quiet");
        assertEquals(o.getCommand(), "start");
        assertTrue(o.isQuiet());
        assertFalse(o.isVerbose());
        assertEquals(o.getPid(),"12");
        assertNull(o.getProcessPattern());
        String args = o.toAgentArg();
        assertTrue(args.matches(".*host=localhost.*"));
        assertTrue(args.matches(".*user=roland.*"));
        assertTrue(args.matches(".*password=bla.*"));
        Map<String,String> opts = new HashMap<String, String>();
        for (String s : args.split(",")) {
            String[] p = s.split("=");
            assertEquals(p.length,2);
            opts.put(p[0],p[1]);
        }
        assertEquals(opts.size(),3);
        assertEquals(opts.get("host"),"localhost");
        assertEquals(opts.get("user"),"roland");
        assertEquals(opts.get("password"),"bla");
    }

    @Test
    public void lookupJar() {
        OptionsAndArgs o = opts();
        assertEquals(o.getJarFileName(),"classes");
        assertNotNull(o.getJarFilePath(),"");
    }

    @Test
    public void listArgs() {
        String DN1 = "CN=adminuser, C=XX, O=Default Company Ltd";
        String DN2 = "CN=Max Mustermann, C=DE, O=Volkswagen";
        OptionsAndArgs o = opts("--clientPrincipal",DN1,"--clientPrincipal",DN2);
        assertTrue(o.toAgentArg().contains(EscapeUtil.escape(DN1,EscapeUtil.CSV_ESCAPE,",")));
        assertTrue(o.toAgentArg().contains(EscapeUtil.escape(DN2,EscapeUtil.CSV_ESCAPE,",")));
        assertTrue(o.toAgentArg().contains("clientPrincipal"));
        assertTrue(o.toAgentArg().contains("clientPrincipal.1"));
        assertFalse(o.toAgentArg().contains("clientPrincipal.0"));
        assertFalse(o.toAgentArg().contains("clientPrincipal.2"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*Unknown option.*")
    public void unknownOption() {
        opts("--blubber", "bla");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*short option.*")
    public void unknownShortOption() {
        opts("-x","bla");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*host.*requires.*")
    public void noOptionArg() {
        opts("--host","--user","roland");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*user.*requires.*")
    public void noOptionArg2() {
        opts("--host","localhost","--user");
    }

    @Test
    public void defaultCommands() {
        OptionsAndArgs o = opts();
        assertEquals(o.getCommand(),"list");
        o = opts("12");
        assertEquals(o.getCommand(), "toggle");
    }

    @Test
    public void toggleDefaultWithPattern() {
        OptionsAndArgs o = opts("bla");
        assertNull(o.getPid());
        Pattern pat = o.getProcessPattern();
        assertEquals(pat.pattern(),"bla");
        assertEquals(o.getCommand(), "toggle");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*Invalid pattern.*")
    public void invalidPattern() {
        OptionsAndArgs o = opts("start", "i+*");
        o.getProcessPattern();
    }

    @Test
    public void encrypt() {
        OptionsAndArgs o = opts("encrypt", "passwd");
        assertEquals(o.getCommand(), "encrypt");
        assertEquals(o.getExtraArgs(), Arrays.asList("passwd"));

    }
}
