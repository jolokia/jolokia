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

import java.lang.reflect.InvocationTargetException;

import org.jolokia.jvmagent.client.util.OptionsAndArgs;
import org.jolokia.jvmagent.client.util.VirtualMachineHandlerOperations;

/**
 * Check the status of an agent on the target process.  Prints out the information
 * to standard out, except if the '--quiet' is given.
 *
 * @author roland
 * @since 06.10.11
 */
public class StatusCommand extends AbstractBaseCommand {

    /** {@inheritDoc} */
    @Override
    String getName() {
        return "status";
    }

    /**
     * Checkt the status and print it out (except for <code>--quiet</code>
     * @param pVm the virtual machine
     * @param pHandler platform-specific way to invoke operations on VM
     * @return the exit code (0: agent is attached, 1: agent is not attached.)
     */
    @Override
    @SuppressWarnings("PMD.SystemPrintln")
    int execute(OptionsAndArgs pOptions, Object pVm, VirtualMachineHandlerOperations pHandler)
            throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String agentUrl = checkAgentUrl(pVm, pHandler);
        boolean quiet = pOptions.isQuiet();
        if (agentUrl != null) {
            if (!quiet) {
                System.out.println("Jolokia started for " + getProcessDescription(pHandler, pOptions));
                System.out.println(agentUrl);
            }
            return 0;
        } else {
            if (!quiet) {
                System.out.println("No Jolokia agent attached to " + getProcessDescription(pHandler, pOptions));
            }
            return 1;
        }
    }
}
