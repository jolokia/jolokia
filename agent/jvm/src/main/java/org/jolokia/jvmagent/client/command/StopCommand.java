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
 * Stop a Jolokia Agent, but only if it is already running (started with 'start').
 * Whether an agent is started is decided by the existence of the
 * system property {@see JvmAgentJdk6#JOLOKIA_AGENT_URL}.
 *
 * @author roland
 * @since 06.10.11
 */
public class StopCommand extends AbstractBaseCommand {

    /** {@inheritDoc} */
    @Override
    String getName() {
        return "stop";
    }

    /** {@inheritDoc} */
    @Override
    @SuppressWarnings("PMD.SystemPrintln")
    int execute(OptionsAndArgs pOpts, Object pVm, VirtualMachineHandlerOperations pHandler) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String agentUrl = checkAgentUrl(pVm, pHandler);
        boolean quiet = pOpts.isQuiet();
        if (agentUrl != null) {
            loadAgent(pVm, pHandler, pOpts, "mode=stop");
            if (!quiet) {
                System.out.println("Stopped Jolokia for " + getProcessDescription(pHandler, pOpts));
            }
            return 0;
        } else {
            if (!quiet) {
                System.out.println("Jolokia is not attached to " + getProcessDescription(pHandler, pOpts));
            }
            return 1;
        }
    }
}
