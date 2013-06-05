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

import org.jolokia.Version;
import org.jolokia.jvmagent.client.util.OptionsAndArgs;
import org.jolokia.jvmagent.client.util.VirtualMachineHandler;

/**
 * Print out the version of the agent
 *
 * @author roland
 * @since 07.10.11
 */
public class VersionCommand extends AbstractBaseCommand {
    @Override
    String getName() {
        return "version";
    }

    @Override
    @SuppressWarnings({"PMD.SystemPrintln"})
    int execute(OptionsAndArgs pOpts, Object pVm, VirtualMachineHandler pHandler) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        StringBuilder version = new StringBuilder("Jolokia JVM Agent ").append(Version.getAgentVersion());
        if (pOpts.isVerbose()) {
            version.append(" (Protocol: ").append(Version.getProtocolVersion()).append(")");
        }
        System.out.println(version.toString());
        return 0;
    }
}
