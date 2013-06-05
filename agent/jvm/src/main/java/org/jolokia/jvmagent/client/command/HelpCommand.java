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
import org.jolokia.jvmagent.client.util.VirtualMachineHandler;
import org.jolokia.config.ConfigKey;

/**
 * Print out usage information
 *
 * @author roland
 * @since 06.10.11
 */
public class HelpCommand extends AbstractBaseCommand {

    /** {@inheritDoc} */
    @Override
    String getName() {
        return "help";
    }

    /** {@inheritDoc} */
    @Override
    int execute(OptionsAndArgs pOpts, Object pVm, VirtualMachineHandler pHandler) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        printUsage();
        return 0;
    }

    /**
     * Print out usage
     */
    @SuppressWarnings({"PMD.SystemPrintln","PMD.AvoidDuplicateLiterals"})
    static void printUsage() {
        String jar = OptionsAndArgs.lookupJarFile().getName();
        System.out.println(
"Jolokia Agent Launcher\n" +
"======================\n" +
"\n" +
"Usage: java -jar " + jar + " [options] <command> <pid/regexp>\n" +
"\n" +
"where <command> is one of\n" +
"    start     -- Start a Jolokia agent for the process specified\n" +
"    stop      -- Stop a Jolokia agent for the process specified\n" +
"    status    -- Show status of an (potentially) attached agent\n" +
"    toggle    -- Toggle between start/stop (default when no command is given)\n" +
"    list      -- List all attachable Java processes (default when no argument is given at all)\n" +
"\n" +
"[options] are used for providing runtime information for attaching the agent:\n" +
"\n" +
"    --host <host>                  Hostname or IP address to which to bind on\n" +
"                                   (default: InetAddress.getLocalHost())\n" +
"    --port <port>                  Port to listen on (default: 8778)\n" +
"    --agentContext <context>       HTTP Context under which the agent is reachable (default: " + ConfigKey.AGENT_CONTEXT.getDefaultValue() + ")\n" +
"    --user <user>                  User used for Basic-Authentication\n" +
"    --password <password>          Password used for Basic-Authentication\n" +
"    --quiet                        No output. \"status\" will exit with code 0 if the agent is running, 1 otherwise\n" +
"    --verbose                      Verbose output\n" +
"    --executor <executor>          Executor policy for HTTP Threads to use (default: single)\n" +
"                                    \"fixed\"  -- Thread pool with a fixed number of threads (default: 5)\n" +
"                                    \"cached\" -- Cached Thread Pool, creates threads on demand\n" +
"                                    \"single\" -- Single Thread\n" +
"    --threadNr <nr threads>        Number of fixed threads if \"fixed\" is used as executor\n" +
"    --backlog <backlog>            How many request to keep in the backlog (default: 10)\n" +
"    --protocol <http|https>        Protocol which must be either \"http\" or \"https\" (default: http)\n" +
"    --keystore <keystore>          Path to keystore (https only)\n" +
"    --keystorePassword <pwd>       Password to the keystore (https only)\n" +
"    --useSslClientAuthentication   Use client certificate authentication (https only)\n" +
"    --debug                        Switch on agent debugging\n" +
"    --debugMaxEntries <nr>         Number of debug entries to keep in memory which can be fetched from the Jolokia MBean\n" +
"    --maxDepth <depth>             Maximum number of levels for serialization of beans (default: " + ConfigKey.MAX_DEPTH.getDefaultValue() + ")\n" +
"    --maxCollectionSize <size>     Maximum number of element in collections to keep when serializing the response (default: " + ConfigKey.MAX_COLLECTION_SIZE.getDefaultValue() + ")\n" +
"    --maxObjects <nr>              Maximum number of objects to consider for serialization (default: " + ConfigKey.MAX_OBJECTS + ")\n" +
"    --policyLocation <url>         Location of a Jolokia policy file\n" +
"    --mbeanQualifier <qualifier>   Qualifier to use when registering Jolokia internal MBeans\n" +
"    --canonicalNaming <t|f>        whether to use canonicalName for ObjectNames in 'list' or 'search' (default: true)\n" +
"    --includeStackTrace <t|f>      whether to include StackTraces for error messages (default: true)\n" +
"    --serializeException <t|f>     whether to add a serialized version of the exception in the Jolokia response (default: false)\n" +
"    --config <configfile>          Path to a property file from where to read the configuration\n" +
"    --help                         This help documentation\n" +
"    --version                      Version of this agent\n" +
"\n" +
"<pid/regexp> can be either a numeric process id or a regular expression. A regular expression is matched\n" +
"against the processes' names (ignoring case) and must be specific enough to select exactly one process.\n" +
"\n" +
"If no <command> is given but only a <pid> the state of the Agent will be toggled\n" +
"between \"start\" and \"stop\"\n" +
"\n" +
"If neither <command> nor <pid> is given, a list of Java processes along with their IDs\n" +
"is printed\n" +
"\n" +
"There are several possible reasons, why attaching to a process can fail:\n" +
"   * The UID of this launcher must be the very *same*as the process to attach too. It not sufficient to be root.\n" +
"   * The JVM must have HotSpot enabled and be a JVM 1.6 or larger.\n" +
"   * It must be a Java process ;-)\n" +
"\n" +
"For more documentation please visit www.jolokia.org"
                          );
    }
}
