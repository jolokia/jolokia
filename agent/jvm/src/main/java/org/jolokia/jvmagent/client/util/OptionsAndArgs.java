package org.jolokia.jvmagent.client.util;

import java.io.File;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.*;

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
 * Class representing options and arguments known to the client launcher. It also knows how
 * to parse the command line.
 *
 * @author roland
 * @since 12.08.11
 */
final public class OptionsAndArgs {

    // ===================================================================================
    // Available options

    private static final Map<String,String> SHORT_OPTS = new HashMap<String, String>();
    private static final Set<String> OPTIONS = new HashSet<String>(Arrays.asList(
                // JVM Agent options:
                "host", "port", "agentContext", "user", "password",
                "quiet!", "verbose!", "executor", "threadNr",
                "backlog", "protocol", "keystore", "keystorePassword",
                "useSslClientAuthentication!",
                // Jolokia options:
                "historyMaxEntries","debug!","debugMaxEntries",
                "dispatcherClasses", "maxDepth", "maxCollectionSize",
                "maxObjects", "policyLocation", "mbeanQualifier",
                // Others:
                "config", "help!"));

    static {
        String shortOptsDef[] = {
            "h", "help",
            "u", "user",
            "p", "password",
            "c", "agentContext",
            "v", "verbose",
            "q", "quiet"
        };

        for (int i = 0; i < shortOptsDef.length; i += 2) {
            SHORT_OPTS.put(shortOptsDef[i],shortOptsDef[i+1]);
        }
    }

    // Launcher command
    private String command;

    // Either pid or processPattern must be set, but not both
    // Process id.
    private String pid;

    // Pattern for matching a process pattern
    private Pattern processPattern;

    private Map<String,String> options;

    private boolean quiet;
    private boolean verbose;

    // Jar file where this class is in
    private File jarFile;


    /**
     * Parse a list of arguments. Options start with '--' (long form) or '-' (short form) and are
     * defined in {@see OPTIONS} and {@see SHORT_OPTS}. For options with arguments, the argument can
     * bei either provided in the form '--option=value' or '--option value'. Everything which is
     * not an option is considered to be an argument. Two arguments are allowed: The command
     * (first) and the PID (second). Any non numeric PID is considered to be a pattern. Either {@link #getPid()} or
     * {@link #getProcessPattern()} is set.
     * <p/>
     * If no PID/pattern and no command is given the "list" command is implied. If as first argument a pure numeric value
     * or a pattern (which must not be equal to a valid command) is given, then "toggle" is infered with the given
     * PID/pattern.
     *
     * @param pCommands set of commands which are known
     * @param pArgs arguments as given on the command line
     * @throws IllegalArgumentException if parsing fails
     */
    public OptionsAndArgs(Set<String> pCommands,String ... pArgs) {
        options = new HashMap<String, String>();

        // Parse options
        List<String> arguments = new ArrayList<String>();
        for (int i = 0; i < pArgs.length; i++) {
            String arg = pArgs[i];
            if (arg.startsWith("-")) {
                ArgParsed argParsed = parseArgument(pArgs[i], i + 1 <= pArgs.length - 1 ? pArgs[i + 1] : null);
                if (argParsed.skipNext) {
                    i++;
                }
                options.put(argParsed.option, argParsed.value);
            }  else {
                arguments.add(arg);
            }
        }
        command = arguments.size() > 0 ? arguments.get(0) : null;
        String pidArg = arguments.size() > 1 ? arguments.get(1) : null;

        quiet = options.containsKey("quiet");
        verbose = options.containsKey("verbose");
        jarFile = lookupJarFile();
        initCommand(pCommands,pidArg);
    }


    /**
     * Convert options to agent readable options (i.e. a single string with options separated by commas)
     *
     * @return agent string
     */
    public String toAgentArg() {
        StringBuilder arg = new StringBuilder();
        for (Map.Entry<String,String> entry : options.entrySet()) {
            if (!entry.getKey().equals("quiet") && !entry.getKey().equals("verbose")) {
                arg.append(entry.getKey()).append("=").append(entry.getValue()).append(",");
            }
        }
        return arg.length() > 0 ? arg.substring(0,arg.length() - 1) : "";
    }

    /**
     * Process id as given as argument (if any). If a pattern for matching the process name is used, this
     * method returns null.
     *
     * @return process id or null
     */
    public String getPid() {
        return pid;
    }

    /**
     * A pattern used for matching a process name. If  {@link #getPid()} return a non-null value,
     * this method returns always null
     *
     * @return pattern to match a process name or null
     */
    public Pattern getProcessPattern() {
        return processPattern;
    }

    /**
     * The command given as argument
     *
     * @return command
     */
    public String getCommand() {
        return command;
    }

    /**
     * Whether the program should be silent
     * @return true if quiet mode is selected
     */
    public boolean isQuiet() {
        return quiet;
    }

    /**
     * Verbose output if this is true
     *
     * @return true if verbose output is requested
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * Path to this agents jar file
     *
     * @return full path to jar file
     */
    public String getJarFilePath() {
        return jarFile.getAbsolutePath();
    }

    /**
     * Name of the agents jar file
     *
     * @return short name of jar file containing this agent.
     */
    public String getJarFileName() {
        return jarFile.getName();
    }

    /**
     * Lookup the JAR File from where this class is loaded
     *
     * @return File pointint to the JAR-File from where this class was loaded.
     */
    public static File lookupJarFile() {
        try {
            return new File(OptionsAndArgs.class
                                    .getProtectionDomain()
                                    .getCodeSource()
                                    .getLocation()
                                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Error: Cannot lookup jar for this class: " + e,e);
        }
    }


    // ===============================================================
    // Command line handling

    private static final Pattern ARGUMENT_PATTERN_WITH_EQUAL = Pattern.compile("([^=]+)=(.*)");

    private ArgParsed parseArgument(String pArg, String pNextArgument) {
        if (pArg.startsWith("--")) {
            // Long option
            String opt = pArg.substring(2);
            String value = null;

            // Check for format 'key=value' as argument
            Matcher matcher = ARGUMENT_PATTERN_WITH_EQUAL.matcher(opt);
            if (matcher.matches()) {
                opt = matcher.group(1);
                value = matcher.group(2);
            }
            if (OPTIONS.contains(opt)) {
                // Option with argument
                if (value == null && (pNextArgument == null || pNextArgument.startsWith("-"))) {
                    throw new IllegalArgumentException("Option '" + opt + "' requires an argument");
                }
                return value != null ? new ArgParsed(opt, value, false) : new ArgParsed(opt,pNextArgument,true);
            } else if (OPTIONS.contains(opt + "!")) {
                return new ArgParsed(opt,"true",false);
            } else {
                throw new IllegalArgumentException("Unknown option '" + opt + "'");
            }
        } else {
            // Short option
            String opt = pArg.substring(1);
            String longOpt = SHORT_OPTS.get(opt);
            if (longOpt == null) {
                throw new IllegalArgumentException("No short option '" + opt + "' known");
            }
            return parseArgument("--" + longOpt,pNextArgument);
        }
    }

    // Initialise default command and validate
    private void initCommand(Set<String> pCommands, String pArg) {
        String process = pArg;
        // Special cases first
        if (options.containsKey("help")) {
            command = "help";
        } else if (command != null && process == null && !pCommands.contains(command)) {
            process = command;
            command = "toggle";
        } else  if (command == null && process == null) {
            command = "list";
        } else {
            // Ok, from here on "command" and "pid" are required
            // command == null and pid != null is never possible, hence command can not be null here
            if (!"list".equals(command)) {
                if (process == null) {
                    throw new IllegalArgumentException("No process id (PID) or pattern given");
                }
            }
        }
        // Check whether pPidArg is a pattern or a numeric id
        if (process != null) {
            if (process.matches("^\\d+$")) {
                pid = process;
            } else {
                try {
                    processPattern = Pattern.compile(process,Pattern.CASE_INSENSITIVE);
                } catch (PatternSyntaxException exp) {
                    throw new IllegalArgumentException("Invalid pattern '" + process + "' for matching process names");
                }
            }
        }
    }




    // A parsed argument
    private static final class ArgParsed {
        private boolean skipNext;
        private String  option;
        private String  value;

        private ArgParsed(String pOption, String pValue, boolean pSkipNext) {
            skipNext = pSkipNext;
            option = pOption;
            value = pValue;
        }
    }



}
