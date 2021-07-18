package org.jolokia.jvmagent.client;

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

import org.jolokia.jvmagent.client.util.*;
import org.jolokia.jvmagent.client.command.CommandDispatcher;

/**
 * Launcher for attaching/detaching a Jolokia agent dynamically to an already
 * running Java process.
 *
 * This launcher tries hard to detect the required classes from tools.jar dynamically. For Mac OSX
 * these classes are already included, for other they are looked up within JAVA_HOME
 * (pointed to by the system property java.home). Classes from tools.jar are never
 * referenced directly but looked up via reflection.
 *
 * @author roland, Greg Bowyer
 * @since 28.07.11
 */
@SuppressWarnings({"PMD.AvoidDuplicateLiterals","PMD.SystemPrintln"})
public final class AgentLauncher {

    private AgentLauncher() { }

    /**
     * Main method for attaching agent to a running JVM program. Use '--help' for a usage
     * explanation.
     *
     * @param args command line arguments
     */
    // We use processing exception thrown here for better error reporting, not flow control.
    @SuppressWarnings("PMD.ExceptionAsFlowControl")
    public static void main(String... args) {
        OptionsAndArgs options;
        try {
            options = new OptionsAndArgs(CommandDispatcher.getAvailableCommands(),args);
            VirtualMachineHandlerOperations vmHandler = PlatformUtils.createVMAccess(options);
            CommandDispatcher dispatcher = new CommandDispatcher(options);

            // Attach a VirtualMachine to a given PID (if PID is given)
            Object vm = options.needsVm() ? vmHandler.attachVirtualMachine() : null;

            // Dispatch command
            int exitCode = 0;
            try {
                exitCode = dispatcher.dispatchCommand(vm,vmHandler);
            } catch (InvocationTargetException e) {
                throw new ProcessingException("InvocationTargetException",e,options);
            } catch (NoSuchMethodException e) {
                throw new ProcessingException("Internal: NoSuchMethod",e,options);
            } catch (IllegalAccessException e) {
                throw new ProcessingException("IllegalAccess",e,options);
            } finally {
                if (vm != null) {
                    vmHandler.detachAgent(vm);
                }
            }
            System.exit(exitCode);

        } catch (IllegalArgumentException exp) {
            System.err.println("Error: " + exp.getMessage() + "\n");
            CommandDispatcher.printHelp();
            System.exit(1);
        } catch (ProcessingException exp) {
            exp.printErrorMessage();
            System.exit(1);
        }
    }
}
