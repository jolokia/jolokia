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
 * Toggle between "start" and "stop" depending on the existance of the system
 * property {@link org.jolokia.jvmagent.JvmAgent#JOLOKIA_AGENT_URL}
 *
 * @author roland
 * @since 06.10.11
 */
public class ToggleCommand extends AbstractBaseCommand {

    // commands to delegate to
    private StartCommand startCommand = new StartCommand();
    private StopCommand stopCommand = new StopCommand();

    /** {@inheritDoc} */
    @Override
    String getName() {
        return "toggle";
    }

    /** {@inheritDoc} */
    @Override
    int execute(OptionsAndArgs pOpts, Object pVm, VirtualMachineHandlerOperations pHandler) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        return checkAgentUrl(pVm, pHandler) == null ?
                startCommand.execute(pOpts, pVm, pHandler) :
                stopCommand.execute(pOpts,pVm,pHandler);
    }

}
