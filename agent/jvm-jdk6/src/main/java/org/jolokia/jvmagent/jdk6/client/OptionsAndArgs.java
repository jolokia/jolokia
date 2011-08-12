package org.jolokia.jvmagent.jdk6.client;

import java.io.File;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
final class OptionsAndArgs {

    // ===================================================================================
    // Available options

    private static final Map<String,String> SHORT_OPTS = new HashMap<String, String>();
    private static final Set<String> OPTIONS = new HashSet<String>(Arrays.asList(
                "host", "port", "agentContext", "user", "password",
                "quiet!", "verbose!", "executor", "threadNr",
                "backlog", "protocol", "keystore", "keystorePassword",
                "useSslClientAuthentication!",
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

    private String command;
    private String pid;
    private Map<String,String> options;

    private boolean quiet;
    private boolean verbose;

    // Jar file where this class is in
    private File jarFile;

    /**
     * Parse a list of arguments. Options start with '--' (long form) or '-' (short form) and are
     * defined in {@see OPTIONS} and {@see SHORT_OPTS}. For options with arguments, the argument can
     * bei either provided in the form '--option=value' or '--option value'. Everything which is
     * not an option is considered to be an argument. Exactly two arguments are allowed: The command
     * (first) and the PID (second).
     *
     * @param pArgs arguments as given on the command line
     * @throws IllegalArgumentException if parsing fails
     */
    public OptionsAndArgs(String ... pArgs) {
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
        pid = arguments.size() > 1 ? arguments.get(1) : null;

        quiet = options.containsKey("quiet");
        verbose = options.containsKey("verbose");
        jarFile = lookupJarFile();
        initCommand();
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

    public String getPid() {
        return pid;
    }

    public String getCommand() {
        return command;
    }

    public boolean isQuiet() {
        return quiet;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public String getJarFilePath() {
        return jarFile.getAbsolutePath();
    }

    public String getJarFileName() {
        return jarFile.getName();
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
    private void initCommand() {
        // Special cases first
        if (options.containsKey("help")) {
            command = "help";
        } else if (command != null && pid == null && command.matches("^[0-9]+$")) {
            pid = command;
            command = "toggle";
        } else  if (command == null && pid == null) {
            command = "list";
        } else {
            // Ok, from here on "command" and "pid" are required
            // command == null and pid != null is never possible, hence command can not be null here
            if (!"list".equals(command)) {
                if (pid == null) {
                    throw new IllegalArgumentException("No process id (PID) given");
                }
                if (!pid.matches("^[0-9]+$")) {
                    throw new IllegalArgumentException("Process id (PID) is not numeric");
                }
            }
        }
    }


    // Lookup the JAR File from where this class is loaded
    static File lookupJarFile() {
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
