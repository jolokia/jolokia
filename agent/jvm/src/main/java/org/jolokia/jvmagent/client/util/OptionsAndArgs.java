package org.jolokia.jvmagent.client.util;

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

import java.io.File;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.*;

import org.jolokia.config.ConfigKey;
import org.jolokia.util.EscapeUtil;

/**
 * Class representing options and arguments known to the client launcher. It also knows how
 * to parse the command line.
 *
 * @author roland
 * @since 12.08.11
 */
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public final class OptionsAndArgs {

    // ===================================================================================
    // Available options

    private static final Map<String,String> SHORT_OPTS = new HashMap<String, String>();
    private static final Set<String> OPTIONS = new HashSet<String>(Arrays.asList(
            // JVM Agent options:
            "host", "port", "agentContext", "user", "password",
            "quiet!", "verbose!", "version!", "executor", "threadNamePrefix", "threadNr",
            "backlog", "hide!", "protocol","authMode","authClass",
            "authUrl", "authPrincipalSpec", "authIgnoreCerts!",
            //https options:
            "keystore", "keystorePassword", "useSslClientAuthentication!",
            "secureSocketProtocol", "keyStoreType", "keyManagerAlgorithm", "trustManagerAlgorithm",
            "caCert", "serverCert", "serverKey", "serverKeyAlgorithm", "clientPrincipal", "extractClientCheck",
            "sslProtocol", "sslCipherSuite",
            // Jolokia options:
            "historyMaxEntries", "debug!", "debugMaxEntries",
            "logHandlerClass", "dispatcherClasses", "maxDepth", "maxCollectionSize",
            "maxObjects", "restrictorClass", "policyLocation", "mbeanQualifier",
            "canonicalNaming", "includeStackTrace", "serializeException",
            "discoveryEnabled", "discoveryAgentUrl", "agentId", "agentDescription",
            // Others:
            "config", "help!"));

    private static final Set<String> LIST_OPTIONS = new HashSet<String>(Arrays.asList(
            "clientPrincipal", "sslProtocol", "sslCipherSuite"));

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

    // Command which require a PID as argument
    private static final Set<String> COMMANDS_REQUIRING_PID =
            new HashSet<String>(Arrays.asList("start","stop","toggle","status"));

    // Launcher command
    private String command;

    // Extra arguments
    private List<String> extraArgs;

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
    public OptionsAndArgs(Set<String> pCommands, String ... pArgs) {
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
                String optionsKey =
                        argParsed.option  +
                        (LIST_OPTIONS.contains(argParsed.option) ? getNextListIndexSuffix(options, argParsed.option) : "");
                options.put(optionsKey, argParsed.value);
            }  else {
                arguments.add(arg);
            }
        }
        command = arguments.size() > 0 ? arguments.remove(0) : null;
        String[] args = arguments.size() > 0 ? arguments.toArray(new String[0]) : new String[0];

        init(pCommands, args);
    }

    /**
     * Convert options to agent readable options (i.e. a single string with options separated by commas)
     *
     * @return agent string
     */
    public String toAgentArg() {
        StringBuilder arg = new StringBuilder();
        for (Map.Entry<String,String> entry : options.entrySet()) {
            String key = entry.getKey();
            if (!key.equals("quiet") && !key.equals("verbose")) {
                arg.append(key).append("=").append(EscapeUtil.escape(entry.getValue(),EscapeUtil.CSV_ESCAPE,",")).append(",");
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
        String arg = extraArgs.size() > 0 ? extraArgs.get(0) : null;
        return arg != null && arg.matches("^\\d+$") ? arg : null;
    }

    /**
     * A pattern used for matching a process name. If  {@link #getPid()} return a non-null value,
     * this method returns always null
     *
     * @return pattern to match a process name or null
     */
    public Pattern getProcessPattern() {
        String arg = extraArgs.size() > 0 ? extraArgs.get(0) : null;
        try {
            return arg != null && getPid() == null ?
                    Pattern.compile(arg, Pattern.CASE_INSENSITIVE)
                    : null;
        } catch (PatternSyntaxException exp) {
            throw new IllegalArgumentException("Invalid pattern '" + arg + "' for matching process names", exp);
        }

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
     * Get extra arguments in addition to the command, or an empty list
     */
    public List<String> getExtraArgs() {
        return extraArgs;
    }

    /**
     * Whether the program should be silent
     * @return true if quiet mode is selected
     */
    public boolean isQuiet() {
        return quiet;
    }

    /**
     * Get the configured port
     */
    public String getPort() {
        String port = options.get("port");
        return port != null ? port : "8778";
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
     * Return <code>true</code> if this command required a attached VM or <code>false</code> otherwise
     *
     * @return true if the command requires an attached VM
     */
    public boolean needsVm() {
        return COMMANDS_REQUIRING_PID.contains(command) || "list".equals(command);
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
     * @return File pointing to the JAR-File from where this class was loaded.
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
        return pArg.startsWith("--") ?
                parseLongOption(pArg, pNextArgument) :
                parseShortOption(pArg, pNextArgument);
    }

    private ArgParsed parseShortOption(String pArg, String pNextArgument) {
        // Short option
        String opt = pArg.substring(1);
        String longOpt = SHORT_OPTS.get(opt);
        if (longOpt == null) {
            throw new IllegalArgumentException("No short option '" + opt + "' known");
        }
        return parseArgument("--" + longOpt,pNextArgument);
    }

    private ArgParsed parseLongOption(String pArg, String pNextArgument) {
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
            verifyOptionWithArgument(opt, value, pNextArgument);
            return value != null ? new ArgParsed(opt, value, false) : new ArgParsed(opt,pNextArgument,true);
        } else if (OPTIONS.contains(opt + "!")) {
            return new ArgParsed(opt,"true",false);
        } else {
            throw new IllegalArgumentException("Unknown option '" + opt + "'");
        }
    }

    // check for the next key with a suffix like ".1" which is not already set
    private String getNextListIndexSuffix(Map<String, String> options, String key) {
        if (!options.containsKey(key)) {
            return "";
        } else {
            int i = 1;
            while (options.containsKey(key + "." + i)) {
                i++;
            }
            return "." + i;
        }
    }

    private void verifyOptionWithArgument(String pOpt, String pValue, String pNextArgument) {
        // Option with argument
        if (pValue == null && (pNextArgument == null || pNextArgument.startsWith("-"))) {
            throw new IllegalArgumentException("Option '" + pOpt + "' requires an argument");
        }
    }

    // Initialise default command and validate
    private void init(Set<String> pCommands, String ... pArgs) {
        quiet = options.containsKey("quiet");
        verbose = options.containsKey("verbose");
        jarFile = lookupJarFile();

        // Special cases first
        extraArgs = checkCommandAndArgs(pCommands, pArgs);
    }

    private void verifyCommandAndArgs(String pCommand, List<String> pArgs) {
        if (COMMANDS_REQUIRING_PID.contains(pCommand) && pArgs.size() == 0) {
            throw new IllegalArgumentException("No process id (PID) or pattern given");
        };
    }

    private List<String> checkCommandAndArgs(Set<String> pCommands, String ... pArgs) {
        List<String> ret = new ArrayList<String>(Arrays.asList(pArgs));
        if (options.containsKey("help")) {
            command = "help";
        } else if (options.containsKey("version")) {
            command = "version";
        } else if (command != null && pArgs.length == 0 && !pCommands.contains(command)) {
            ret.add(command);
            command = "toggle";
        } else if (command == null && pArgs.length == 0) {
            command = "list";
        }
        verifyCommandAndArgs(command,ret);
        return ret;
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
