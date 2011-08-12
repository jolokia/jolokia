package org.jolokia.jvmagent.jdk6.client;

import java.io.File;
import java.net.URISyntaxException;

import org.jolokia.jvmagent.jdk6.JvmAgentJdk6;

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
    public static void main(String... args) {
        OptionsAndArgs options = null;
        try {
            options = new OptionsAndArgs(args);
            VirtualMachineHandler vmHandler = new VirtualMachineHandler(options);
            CommandDispatcher dispatcher = new CommandDispatcher(vmHandler,options);

            // Attach a VirtualMachine to a given PID (if PID is given)
            Object vm = vmHandler.attachVirtualMachine();

            // Dispatch command
            int exitCode = 0;
            try {
                exitCode = dispatcher.dispatchCommand(vm);
            } finally {
                vmHandler.detachAgent(vm);
            }
            System.exit(exitCode);

        } catch (IllegalArgumentException exp) {
            System.err.println("Error: " + exp.getMessage() + "\n");
            CommandDispatcher.printHelp(OptionsAndArgs.lookupJarFile().getName());
            System.exit(1);
        } catch (ProcessingException exp) {
            exp.printErrorMessage();
            System.exit(1);
        }
    }
}
