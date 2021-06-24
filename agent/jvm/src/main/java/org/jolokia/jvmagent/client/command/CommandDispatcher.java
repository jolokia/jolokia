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
import java.util.*;

import org.jolokia.jvmagent.client.util.OptionsAndArgs;
import org.jolokia.jvmagent.client.util.VirtualMachineHandlerOperations;

/**
 * Dispatch for various attach commands
 * 
 * @author roland
 * @since 12.08.11
 */
@SuppressWarnings({"PMD.SystemPrintln"})
public class CommandDispatcher {

    private OptionsAndArgs options;

    private static final Map<String,AbstractBaseCommand> COMMANDS = new HashMap<String, AbstractBaseCommand>();

    // Initialize command objects
    static {
        for (AbstractBaseCommand command : new AbstractBaseCommand[] {
                new StartCommand(),
                new StopCommand(),
                new ToggleCommand(),
                new StatusCommand(),
                new ListCommand(),
                new EncryptCommand(),
                new HelpCommand(),
                new VersionCommand()
        }) {
            COMMANDS.put(command.getName(),command);
        }
    }


    /**
     * Dispatcher responsible for the execution of commands
     *
     * @param pOptions the parsed command line and options
     */
    public CommandDispatcher(OptionsAndArgs pOptions) {
        options = pOptions;
    }


    /**
     * Dispatch the command
     *
     * @param pVm the virtual machine to attach to (typeless in order avoid direct references to the sun classes)
     * @param pHandler  handler for listing processes
     * @return the return code (0 or 1)
     */
    public int dispatchCommand(Object pVm, VirtualMachineHandlerOperations pHandler) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String commandName = options.getCommand();
        AbstractBaseCommand command = COMMANDS.get(commandName);
        if (command == null) {
            throw new IllegalArgumentException("Unknown command '" + commandName + "'");
        }
        return command.execute(options,pVm,pHandler);
    }

    /**
     * Get the list of available commands
     *
     * @return set of available commands
     */
    public static Set<String> getAvailableCommands() {
        return Collections.unmodifiableSet(COMMANDS.keySet());
    }

    /**
     * Print out usage message
     */
    public static void printHelp() {
        HelpCommand.printUsage();
    }
}
