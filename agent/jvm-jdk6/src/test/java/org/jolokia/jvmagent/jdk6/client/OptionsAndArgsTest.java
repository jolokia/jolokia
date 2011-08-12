package org.jolokia.jvmagent.jdk6.client;

/*
 * Copyright 2009-2011 Roland Huss
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

import javax.swing.text.html.Option;

import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 12.08.11
 */
@Test
public class OptionsAndArgsTest {

    @Test
    public void help() {
        OptionsAndArgs o = new OptionsAndArgs(new String[] { "--help","--verbose"});
        assertEquals(o.getCommand(), "help");
        assertFalse(o.isQuiet());
        assertTrue(o.isVerbose());
    }

    @Test
    public void simple() {
        OptionsAndArgs o = new OptionsAndArgs(new String[] { "--host","localhost","start","12","--password=bla","-u","roland","--quiet"});
        assertEquals(o.getCommand(), "start");
        assertTrue(o.isQuiet());
        assertFalse(o.isVerbose());
        assertEquals(o.getPid(),"12");
        assertEquals(o.toAgentArg(),"host=localhost,user=roland,password=bla");
    }

    @Test
    public void lookupJar() {
        OptionsAndArgs o = new OptionsAndArgs(new String[0]);
        assertEquals(o.getJarFileName(),"classes");
        assertNotNull(o.getJarFilePath(),"");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*Unknown option.*")
    public void unknownOption() {
        new OptionsAndArgs(new String[] { "--blubber","bla"});
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*short option.*")
    public void unknownShortOption() {
        new OptionsAndArgs(new String[] { "-x","bla"});
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*host.*requires.*")
    public void noOptionArg() {
        new OptionsAndArgs(new String[] { "--host","--user","roland"});
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*user.*requires.*")
    public void noOptionArg2() {
        new OptionsAndArgs(new String[] { "--host","localhost","--user"});
    }

    @Test
    public void defaultCommands() {
        OptionsAndArgs o = new OptionsAndArgs(new String[0]);
        assertEquals(o.getCommand(),"list");
        o = new OptionsAndArgs(new String[] { "12" });
        assertEquals(o.getCommand(),"toggle");
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*process id.*")
    public void invalidDefaultCommands() {
       new OptionsAndArgs(new String[] { "bla" });
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*numeric.*")
    public void invalidDefaultCommands2() {
       new OptionsAndArgs(new String[] { "bla" , "blub"});
    }
}
