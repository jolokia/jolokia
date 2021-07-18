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

import java.lang.management.ManagementFactory;
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

    VirtualMachineHandlerOperations vmHandler;

    @BeforeTest
    public void setup() {
        OptionsAndArgs o = new OptionsAndArgs(CommandDispatcher.getAvailableCommands());
        vmHandler = PlatformUtils.createVMAccess(o);
    }


    @Test
    public void simple() throws ClassNotFoundException {
        Class clazz = ToolsClassFinder.lookupClass("com.sun.tools.attach.VirtualMachine");
        assertEquals(clazz.getName(),"com.sun.tools.attach.VirtualMachine");
    }

    @Test
    public void emptyPid() {
        assertNull(vmHandler.attachVirtualMachine());
    }

    @Test
    public void listAndAttach() throws Exception, NoSuchMethodException, IllegalAccessException {
        List<ProcessDescription> procs = vmHandler.listProcesses();
        assertTrue(procs.size() > 0);
        boolean foundAtLeastOne = false;
        for (ProcessDescription p : procs) {
            try {
                foundAtLeastOne |= tryAttach(p.getId());
            } catch (Exception exp) {
                System.err.println("ERROR: " + p.getId() + " " + p.getDisplay() + ": " + exp);
            }
        }
        assertTrue(foundAtLeastOne);
    }


    @Test
    public void findProcess() throws Exception, NoSuchMethodException, IllegalAccessException {
        List<ProcessDescription> procs = filterOwnProcess(vmHandler.listProcesses());
        for (ProcessDescription desc : procs) {
            try {
                if (desc.getDisplay() != null && desc.getDisplay().length() > 0) {
                    Pattern singleHitPattern = Pattern.compile("^" + Pattern.quote(desc.getDisplay()) + "$");
                    assertTrue(tryAttach(singleHitPattern.pattern()));
                    break;
                }
            } catch (Exception exp) {
                // We ignore error which happen spuriously
                System.out.println("Cannot attach to " + desc.getDisplay() + " (" + desc.getId() + "): " + exp);
                exp.printStackTrace();
            }
        }

        assertFalse(tryAttach("RobertMakClaudioPizarro",".*No.*process.*"));
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

    private boolean tryAttach(String pId,String ... expMsg) throws Exception {
        OptionsAndArgs o = new OptionsAndArgs(CommandDispatcher.getAvailableCommands(),"start", pId);
        VirtualMachineHandlerOperations h = PlatformUtils.createVMAccess(o);
        Object vm = null;
        try {
            vm = h.attachVirtualMachine();
            return true;
        } catch (Exception exp) {
            if (expMsg.length > 0) {
                assertTrue(exp.getMessage().matches(expMsg[0]) || exp.getCause().getMessage().matches(expMsg[0]));
            } else {
                throw exp;
            }
        } finally {
            if (vm != null) {
                h.detachAgent(vm);
            }
        }
        return false;
    }

}
