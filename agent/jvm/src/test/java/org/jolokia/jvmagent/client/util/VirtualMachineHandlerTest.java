package org.jolokia.jvmagent.client.util;

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

import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.jolokia.jvmagent.client.command.CommandDispatcher;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * @author roland
 * @since 12.08.11
 */
@Test(groups = "java6")
public class VirtualMachineHandlerTest {

    VirtualMachineHandler vmHandler;

    @BeforeTest
    public void setup() {
        OptionsAndArgs o = new OptionsAndArgs(CommandDispatcher.getAvailableCommands(),new String[0]);
        vmHandler = new VirtualMachineHandler(o);
    }


    @Test
    public void simple() {
        Class clazz = vmHandler.lookupVirtualMachineClass();
        assertEquals(clazz.getName(),"com.sun.tools.attach.VirtualMachine");
    }

    @Test
    public void emptyPid() {
        assertNull(vmHandler.attachVirtualMachine());
    }

    @Test
    public void listAndAttach() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        List<ProcessDescription> procs = vmHandler.listProcesses();
        assertTrue(procs.size() > 0);
        boolean foundAtLeastOne = false;
        for (ProcessDescription p : procs) {
            foundAtLeastOne |= tryAttach(p.getId());
        }
        assertTrue(foundAtLeastOne);
    }


    @Test
    public void findProcess() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        List<ProcessDescription> procs = filterOwnProcess(vmHandler.listProcesses());
        if (procs.size() > 0) {
            Pattern singleHitPattern = Pattern.compile("^" + Pattern.quote(procs.get(0).getDisplay()) + "$");
            assertTrue(tryAttach(singleHitPattern.pattern()));
        }

        assertFalse(tryAttach("RobertMakClaudiPizarro","No process"));
        if (procs.size() >= 2) {
            assertFalse(tryAttach(".",procs.get(0).getId()));
        }
    }

    private List<ProcessDescription> filterOwnProcess(List<ProcessDescription> pProcessDescs) {
        List<ProcessDescription> ret = new ArrayList<ProcessDescription>();
        String ownId = getOwnProcessId();
        for (ProcessDescription desc : pProcessDescs) {
            if (!desc.getId().equals(ownId)) {
                ret.add(desc);
                break;            }
        }
        return ret;
    }

    private String getOwnProcessId() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        int endIdx = name.indexOf('@');
        return endIdx != -1 ? name.substring(0,endIdx) : name;
    }

    private boolean tryAttach(String pId,String ... expMsg) {
        OptionsAndArgs o = new OptionsAndArgs(CommandDispatcher.getAvailableCommands(),"start", pId);
        VirtualMachineHandler h = new VirtualMachineHandler(o);
        Object vm = null;
        try {
            vm = h.attachVirtualMachine();
            return true;
        } catch (Exception exp) {
            if (expMsg.length > 0) {
                assertTrue(exp.getMessage().contains(expMsg[0]) || exp.getCause().getMessage().contains(expMsg[0]));
            }
        } finally {
            if (vm != null) {
                h.detachAgent(vm);
            }
        }
        return false;
    }

}
