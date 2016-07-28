package org.jolokia.jvmagent.client.command;

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

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.regex.Pattern;

import com.sun.tools.attach.*;
import org.easymock.EasyMock;
import org.easymock.IArgumentMatcher;
import org.jolokia.Version;
import org.jolokia.jvmagent.JvmAgent;
import org.jolokia.jvmagent.client.util.*;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.*;

/**
 * @author roland
 * @since 12.08.11
 */
@Test(groups = "java6")
public class CommandDispatcherTest {

    private PrintStream outBack, errBack;
    private ByteArrayOutputStream outStream,errStream;

    @Test
    public void start() throws IOException, AgentInitializationException, AgentLoadException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        testCommand("start", false, 0);
        testCommand("start", true, 1);
    }

    @Test
    public void stop() throws AgentInitializationException, IOException, AgentLoadException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        testCommand("stop", false, 1);
        testCommand("stop", true, 0);
    }

    @Test
    public void toggle() throws AgentInitializationException, IOException, AgentLoadException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        testCommand("toggle", false, 0);
        testCommand("toggle", true, 0);
    }

    @Test
    public void help() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        CommandDispatcher d = new CommandDispatcher(opts("--help"));
        assertEquals(d.dispatchCommand(null, null), 0);
        CommandDispatcher.printHelp();
        assertTrue(outStream.toString().contains("Jolokia Agent Launcher"));
    }

    @Test
    public void version() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        CommandDispatcher d = new CommandDispatcher(opts("--version"));
        assertEquals(d.dispatchCommand(null, null), 0);
        assertTrue(outStream.toString().contains(Version.getAgentVersion()));
        assertFalse(outStream.toString().contains(Version.getProtocolVersion()));
    }

    @Test
    public void versionVerbose() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        CommandDispatcher d = new CommandDispatcher(opts("--version","--verbose"));
        assertEquals(d.dispatchCommand(null, null), 0);
        assertTrue(outStream.toString().contains(Version.getAgentVersion()));
        assertTrue(outStream.toString().contains(Version.getProtocolVersion()));
    }

    @Test
    public void list() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        CommandDispatcher d = new CommandDispatcher(opts("list"));

        VirtualMachineHandler vmh = createMock(VirtualMachineHandler.class);
        List<ProcessDescription> ret = new ArrayList<ProcessDescription>();
        ret.add(new ProcessDescription("12","TestProcess"));
        expect(vmh.listProcesses()).andReturn(ret);
        replay(vmh);

        assertEquals(d.dispatchCommand(null, vmh), 0);

        verify(vmh);
    }

    @Test
    public void descriptionWithPattern() throws AgentInitializationException, InvocationTargetException, IOException, NoSuchMethodException, AgentLoadException, IllegalAccessException {
        CommandDispatcher d = new CommandDispatcher(opts("start","blub"));

        VirtualMachineHandler vmh = createMock(VirtualMachineHandler.class);
        VirtualMachine vm = createMock(VirtualMachine.class);
        expect(vm.getSystemProperties()).andReturn(getProperties(false));
        expect(vm.getSystemProperties()).andReturn(getProperties(true));
        // Agent should be loaded for successful switch
        vm.loadAgent(EasyMock.<String>anyObject(), EasyMock.<String>anyObject());

        expect(vmh.findProcess(patternMatcher("blub"))).andReturn(new ProcessDescription("18", "bla blub blie"));
        replay(vm, vmh);

        int rc = d.dispatchCommand(vm, vmh);

        assertEquals(rc, 0);
        verify(vm, vmh);
    }

    private Pattern patternMatcher(final String pPattern) {
        EasyMock.reportMatcher(new IArgumentMatcher() {
            public boolean matches(Object argument) {
                Pattern p = (Pattern) argument;
                return p.pattern().equals(pPattern);
            }

            public void appendTo(StringBuffer buffer) {
            }
        });
        return null;
    }

    @Test
    public void status() throws IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        testStatus(true,0);
        testStatus(false,1);
    }

    @Test(expectedExceptions = InvocationTargetException.class)
    public void throwException() throws IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        CommandDispatcher d = new CommandDispatcher(opts("start", "42"));

        VirtualMachine vm = createMock(VirtualMachine.class);
        expect(vm.getSystemProperties()).andThrow(new IOException());
        replay(vm);

        d.dispatchCommand(vm,null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*Unknown.*")
    public void unknownCommand() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        CommandDispatcher d = new CommandDispatcher(opts("blub", "42"));

        d.dispatchCommand(null,null);
    }

    // ======================================================================================================

    private void testCommand(String pCommand, boolean pActive, int pRc, String... pProcess) throws IOException, AgentLoadException, AgentInitializationException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String p = pProcess.length > 0 ? pProcess[0] : "42";
        CommandDispatcher d = new CommandDispatcher(opts(pCommand,p));

        VirtualMachineHandler vmh = createMock(VirtualMachineHandler.class);
        VirtualMachine vm = createMock(VirtualMachine.class);
        expect(vm.getSystemProperties()).andReturn(getProperties(pActive)).times(pCommand.equals("toggle") ? 2 : 1);
        if (!pActive && !pCommand.equals("stop")) {
            expect(vm.getSystemProperties()).andReturn(getProperties(true));
        }
        if (pRc == 0) {
            // Agent should be loaded for successful switch
            vm.loadAgent(EasyMock.<String>anyObject(), EasyMock.<String>anyObject());
        }
        replay(vm,vmh);

        int rc = d.dispatchCommand(vm,vmh);

        assertEquals(rc, pRc);
        verify(vm,vmh);
    }

    private void testStatus(boolean pActive,int pRc) throws IOException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        CommandDispatcher d = new CommandDispatcher(opts("status", "18"));

        VirtualMachine vm = createMock(VirtualMachine.class);
        expect(vm.getSystemProperties()).andReturn(getProperties(pActive)).anyTimes();
        replay(vm);

        assertEquals(d.dispatchCommand(vm,null), pRc);

        verify(vm);
    }

    private Properties getProperties(boolean pActive) {
        Properties props = new Properties();
        if (pActive) {
            props.put(JvmAgent.JOLOKIA_AGENT_URL,"blub");
        }
        return props;
    }


    @BeforeMethod
    void prepareOutputStreams() {
        outBack = System.out;
        errBack = System.err;

        outStream = new ByteArrayOutputStream();
        errStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outStream));
        System.setErr(new PrintStream(errStream));
    }

    @AfterMethod
    void restoreOutputStreams() {
        System.setOut(outBack);
        System.setErr(errBack);
    }


    String getError() {
        return errStream.toString();
    }

    String getOut() {
        return outStream.toString();
    }


    private OptionsAndArgs opts(String... args) {
        return new OptionsAndArgs(CommandDispatcher.getAvailableCommands(),args);
    }



}
