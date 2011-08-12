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
import java.lang.reflect.Method;
import java.util.*;

import org.jolokia.jvmagent.jdk6.JolokiaServer;
import org.jolokia.jvmagent.jdk6.JvmAgentJdk6;
import org.jolokia.util.ConfigKey;

/**
 * Dispatch for various attach commands
 * 
 * @author roland
 * @since 12.08.11
 */
@SuppressWarnings({"PMD.SystemPrintln"})
public class CommandDispatcher {

    private OptionsAndArgs options;

    CommandDispatcher(OptionsAndArgs pOptions) {
        options = pOptions;
    }

    public int dispatchCommand(Object pVm,VirtualMachineHandler pHandler) {
        String command = options.getCommand();
        try {
            int rc = 0;
            if ("help".equals(command)) {
                printHelp(options.getJarFileName());
            } else if ("start".equals(command)) {
                rc = commandStart(pVm);
            } else if ("status".equals(command)) {
                return commandStatus(pVm);
            } else if ("stop".equals(command)) {
                return commandStop(pVm);
            } else if ("toggle".equals(command)) {
                return commandToggle(pVm);
            } else if ("list".equals(command)) {
                listProcesses(pHandler);
            } else {
                throw new IllegalArgumentException("Unknown command '" + command + "'");
        }
            return rc;
        } catch (InvocationTargetException e) {
            throw new ProcessingException("InvocationTargetException for command '" + command + "'",e,options);
        } catch (NoSuchMethodException e) {
            throw new ProcessingException("Internal: NoSuchMethod for command '" + command + "'",e,options);
        } catch (IllegalAccessException e) {
            throw new ProcessingException("IllegalAccess for command '" + command + "'",e,options);
        }
    }

    // ========================================================================
    // Commands

    /**
     * List all available Java processes
     * @throws IllegalAccessException reflection error
     * @throws NoSuchMethodException reflection error
     * @throws InvocationTargetException reflection error
     * @param pHandler
     */
    private void listProcesses(VirtualMachineHandler pHandler) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        List<VirtualMachineHandler.ProcessDesc> vmDescriptors = pHandler.listProcesses();
        for (VirtualMachineHandler.ProcessDesc descriptor : vmDescriptors) {
            System.out.println(new Formatter().format("%7.7s   %-100.100s",descriptor.getId(),descriptor.getDisplay()));
        }
    }

    /**
     * Load a Jolokia Agent and start it. Whether an agent is started is decided by the existence of the
     * system property {@see JvmAgentJdk6#JOLOKIA_AGENT_URL}.
     *
     * @param pVm the virtual machine
     * @return the exit code (0: success, 1: error)
     */
    private int commandStart(Object pVm) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String agentUrl;
        agentUrl = checkAgentUrl(pVm);
        boolean quiet = options.isQuiet();
        if (agentUrl == null) {
            String agent = options.getJarFilePath();
            loadAgent(pVm, agent, options.toAgentArg());
            if (!quiet) {
                System.out.println("Started Jolokia for PID " + options.getPid());
                System.out.println(checkAgentUrl(pVm));
            }
            return 0;
        } else {
            if (!quiet) {
                System.out.println("Jolokia already attached to " + options.getPid());
                System.out.println(agentUrl);
            }
            return 1;
        }
    }

    /**
     * Stop a Jolokia Agent, but only if it is already running (started with 'start').
     * Whether an agent is started is decided by the existence of the
     * system property {@see JvmAgentJdk6#JOLOKIA_AGENT_URL}.
     *
     * @param pVm the virtual machine
     * @return the exit code (0: success, 1: error)
     * @throws IllegalAccessException if call via reflection fails
     * @throws NoSuchMethodException should not happen since we use well known methods
     * @throws InvocationTargetException exception occured during startup of the agent. You probably need to examine
     *         the stdout of the instrumented process as well for error messages.
     */
    private int commandStop(Object pVm) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String agentUrl = checkAgentUrl(pVm);
        boolean quiet = options.isQuiet();
        if (agentUrl != null) {
            String agent = options.getJarFilePath();
            String agentOpts =  options.toAgentArg();
            loadAgent(pVm, agent, agentOpts.length() != 0 ? agentOpts + ",mode=stop" : "mode=stop");
            if (!quiet) {
                System.out.println("Stopped Jolokia for PID " + options.getPid());
            }
            return 0;
        } else {
            if (!quiet) {
                System.out.println("Jolokia is not attached to PID " + options.getPid());
            }
            return 1;
        }
    }

    /**
     * Check the status of an agent on the target process.  Prints out the information
     * to standard out, except if the '--quiet' is given.
     *
     * @param pVm the virtual machine
     * @return the exit code (0: agent is attached, 1: agent is not attached.)
     */
    private int commandStatus(Object pVm) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String agentUrl = checkAgentUrl(pVm);
        boolean quiet = options.isQuiet();
        if (agentUrl != null) {
            if (!quiet) {
                System.out.println("Jolokia started for PID " + options.getPid());
                System.out.println(agentUrl);
            }
            return 0;
        } else {
            if (!quiet) {
                System.out.println("No Jolokia agent attached to PID " + options.getPid());
            }
            return 1;
        }
    }

    /**
     * Start or stop the agent depending on its current state.
     *
     * @param pVm virtual machine
     * @return 0 if toggling was successful, false otherwise
     */
    private int commandToggle(Object pVm) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        return checkAgentUrl(pVm) == null ?
                commandStart(pVm) :
                commandStop(pVm);
    }

    // =============================================================================================================

    // Check whether an agent is registered by checking the existance of a system property
    private String checkAgentUrl(Object pVm) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Properties systemProperties = getAgentSystemProperties(pVm);
        return systemProperties.getProperty(JvmAgentJdk6.JOLOKIA_AGENT_URL);
    }

    private void loadAgent(Object pVm, String pAgent, String pPid) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class clazz = pVm.getClass();
        Method method = clazz.getMethod("loadAgent",String.class, String.class);
        method.invoke(pVm,pAgent,pPid);
    }

    private Properties getAgentSystemProperties(Object pVm) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class clazz = pVm.getClass();
        Method method = clazz.getMethod("getSystemProperties");
        return (Properties) method.invoke(pVm);
    }

    public static void printHelp(String jar) {
        System.out.println(
"Jolokia Agent Launcher\n" +
"======================\n" +
"\n" +
"Usage: java -jar " + jar + " [options] <command> <pid>\n" +
"\n" +
"where <command> is one of\n" +
"    start     -- Start a Jolokia agent for the given process id\n" +
"    stop      -- Stop a Jolokia agent for the given process id\n" +
"    status    -- Show status of an (potentially) attached agent\n" +
"    toggle    -- Toggle between start/stop (default when no command is given)\n" +
"    list      -- List all attachable Java processes (default when no argument is given)\n" +
"\n" +
"[options] are used for providing runtime information for attaching the agent:\n" +
"\n" +
"    --host <host>                 Hostname or IP address to which to bind on\n" +
"                                  (default: InetAddress.getLocalHost())\n" +
"    --port <port>                 Port to listen on (default: " + JolokiaServer.DEFAULT_PORT + ")\n" +
"    --agentContext <context>      HTTP Context under which the agent is reachable (default: " + ConfigKey.AGENT_CONTEXT.getDefaultValue() + ")\n" +
"    --user <user>                 User used for Basic-Authentication\n" +
"    --password <password>         Password used for Basic-Authentication\n" +
"    --quiet                       No output. \"status\" will exit with code 0 if the agent is running, 1 otherwise\n" +
"    --verbose                     Verbose output\n" +
"    --executor <executor>         Executor policy for HTTP Threads to use (default: single)\n" +
"                                  \"fixed\"  -- Thread pool with a fixed number of threads (default: 5)\n" +
"                                  \"cached\" -- Cached Thread Pool, creates threads on demand\n" +
"                                  \"single\" -- Single Thread\n" +
"    --threadNr <nr threads>       Number of fixed threads if \"fixed\" is used as executor\n" +
"    --backlog <backlog>           How many request to keep in the backlog (default: 10)\n" +
"    --protocol <http|https>       Protocol which must be either \"http\" or \"https\" (default: http)\n" +
"    --keystore <keystore>         Path to keystore (https only)\n" +
"    --keystorePassword <pwd>      Password to the keystore (https only)\n" +
"    --useSslClientAuthentication  Use client certificate authentication (https only)\n" +
"    --config <configfile>         Path to a property file from where to read the configuration\n" +
"    --help                        This help documentation\n" +
"\n" +
"If no <command> is given but only a <pid> the state of the Agent will be toggled\n" +
"between \"start\" and \"stop\"\n" +
"\n" +
"If neither <command> nor <pid> is given, a list of Java processes along with their IDs\n" +
"is printed\n" +
"\n" +
"For more information please visit www.jolokia.org"
                          );
    }
}
