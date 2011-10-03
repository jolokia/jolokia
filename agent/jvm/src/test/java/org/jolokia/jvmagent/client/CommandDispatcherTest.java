package org.jolokia.jvmagent.client;

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

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import com.sun.tools.attach.*;
import org.easymock.EasyMock;
import org.jolokia.jvmagent.JvmAgent;
import org.testng.annotations.*;

import static org.easymock.EasyMock.*;
import static org.testng.Assert.assertEquals;

/**
 * @author roland
 * @since 12.08.11
 */
@Test(groups = "java6")
public class CommandDispatcherTest {

    private PrintStream outBack, errBack;
    private ByteArrayOutputStream outStream,errStream;

    @Test
    public void start() throws IOException, AgentInitializationException, AgentLoadException {
        testStartStop("start",false,0);
        testStartStop("start",true,1);
    }

    @Test
    public void stop() throws AgentInitializationException, IOException, AgentLoadException {
        testStartStop("stop",false,1);
        testStartStop("stop",true,0);
    }

    @Test
    public void toggle() throws AgentInitializationException, IOException, AgentLoadException {
        testStartStop("toggle",false,0);
        testStartStop("toggle",true,0);
    }

    @Test
    public void help() {
        CommandDispatcher d = new CommandDispatcher(new OptionsAndArgs("--help"));
        assertEquals(d.dispatchCommand(null,null),0);
    }

    @Test
    public void list() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        CommandDispatcher d = new CommandDispatcher(new OptionsAndArgs("list"));

        VirtualMachineHandler vmh = createMock(VirtualMachineHandler.class);
        List<VirtualMachineHandler.ProcessDesc> ret = new ArrayList<VirtualMachineHandler.ProcessDesc>();
        ret.add(new VirtualMachineHandler.ProcessDesc("12","TestProcess"));
        expect(vmh.listProcesses()).andReturn(ret);
        replay(vmh);

        assertEquals(d.dispatchCommand(null, vmh), 0);

        verify(vmh);
    }

    @Test
    public void status() throws IOException {
        testStatus(true,0);
        testStatus(false,1);
    }

    @Test(expectedExceptions = ProcessingException.class)
    public void throwException() throws IOException {
        CommandDispatcher d = new CommandDispatcher(new OptionsAndArgs("start","42"));

        VirtualMachine vm = createMock(VirtualMachine.class);
        expect(vm.getSystemProperties()).andThrow(new IOException());
        replay(vm);

        d.dispatchCommand(vm,null);
    }

    @Test(expectedExceptions = IllegalArgumentException.class,expectedExceptionsMessageRegExp = ".*Unknown.*")
    public void unknownCommand() {
        CommandDispatcher d = new CommandDispatcher(new OptionsAndArgs("blub","42"));

        d.dispatchCommand(null,null);
    }

    // ======================================================================================================

    private void testStartStop(String pCommand,boolean pActive,int pRc) throws IOException, AgentLoadException, AgentInitializationException {
        CommandDispatcher d = new CommandDispatcher(new OptionsAndArgs(pCommand,"42"));

        VirtualMachineHandler vmh = createMock(VirtualMachineHandler.class);
        VirtualMachine vm = createMock(VirtualMachine.class);
        expect(vm.getSystemProperties()).andReturn(getProperties(pActive)).anyTimes();
        if (pRc == 0) {
            // Agent should be loaded for successful switch
            vm.loadAgent(EasyMock.<String>anyObject(), EasyMock.<String>anyObject());
        }
        replay(vm,vmh);

        int rc = d.dispatchCommand(vm,vmh);

        assertEquals(rc, pRc);
        verify(vm,vmh);
    }

    private void testStatus(boolean pActive,int pRc) throws IOException {
        CommandDispatcher d = new CommandDispatcher(new OptionsAndArgs("status","18"));

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




}
