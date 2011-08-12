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

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import com.sun.tools.attach.VirtualMachine;
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
        OptionsAndArgs o = new OptionsAndArgs(new String[0]);
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
        List<VirtualMachineHandler.ProcessDesc> procs = vmHandler.listProcesses();
        assertTrue(procs.size() > 0);
        boolean foundAtLeastOne = false;
        for (VirtualMachineHandler.ProcessDesc p : procs) {
            OptionsAndArgs o = new OptionsAndArgs(new String[] { p.getId() });
            VirtualMachineHandler h = new VirtualMachineHandler(o);
            Object vm = null;
            try {
                vm = h.attachVirtualMachine();
                foundAtLeastOne = true;
            } finally {
                if (vm != null) {
                    h.detachAgent(vm);
                }
            }
        }
        assertTrue(foundAtLeastOne);
    }


}
