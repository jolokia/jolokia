package org.jolokia.jvmagent.jdk6;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

        // Get options first
        OptionsAndArgs options = null;
        try {
            options = parseArgs(args);
        } catch (IllegalArgumentException exp) {
            System.err.println("Error: " + exp.getMessage() + "\n");
            commandHelp();
            System.exit(1);
        }

        // Attach a VirtualMachine to a given PID (if PID is given)
        Object vm = null;
        if (options.pid != null) {
            vm = attachVirtualMachine(options);
            if (vm == null) {
                // Error message has been alread printed
                System.exit(1);
            }
        }

        // Dispatch command
        int exitCode = 0;
        Exception exception = null;
        try {
            exitCode = dispatchCommand(options, vm);
        } catch (RuntimeException e) {
            exception = e;
        } catch (Exception e) {
            exception = e;
        } finally {
            try {
                detachAgent(vm);
            } catch (Exception e) {
                printException("Error while detaching",e,options);
                exitCode = 1;
            }
        }

        // Exit
        if (exception != null) {
            printException("Error while processing command '" + options.command + "'",exception,options);
            System.exit(1);
        } else {
            System.exit(exitCode);
        }
    }

    private static int dispatchCommand(OptionsAndArgs pOptions, Object pVm)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String command = pOptions.command;

        if ("help".equals(command)) {
            return commandHelp();
        } else if ("start".equals(command)) {
            return commandStart(pVm, pOptions);
        } else if ("status".equals(command)) {
            return commandStatus(pVm, pOptions);
        } else if ("stop".equals(command)) {
            return commandStop(pVm, pOptions);
        } else if ("toggle".equals(command)) {
            return checkAgentUrl(pVm) == null ?
                    commandStart(pVm, pOptions) :
                    commandStop(pVm, pOptions);
        } else if ("list".equals(command)) {
            return listProcesses(pOptions);
        } else {
            throw new IllegalArgumentException("Unknown command '" + command + "'");
        }
    }

    private static int listProcesses(OptionsAndArgs pOptions) {
        Class vmClass = lookupVirtualMachineClass(pOptions);
        if (vmClass == null) {
            return 1;
        }
        try {
            Method method = vmClass.getMethod("list");
            List vmDescriptors = (List) method.invoke(null);
            for (Object descriptor : vmDescriptors) {
                Method idMethod = descriptor.getClass().getMethod("id");
                String id = (String) idMethod.invoke(descriptor);
                Method displayMethod = descriptor.getClass().getMethod("displayName");
                String display = (String) displayMethod.invoke(descriptor);
                System.out.println(new Formatter().format("%7.7s   %-100.100s",id,display));
            }
        } catch (NoSuchMethodException e) {
            printException("Internal",e,pOptions);
            return 1;
        } catch (InvocationTargetException e) {
            printException("InvocationTarget", e, pOptions);
            return 1;
        } catch (IllegalAccessException e) {
            printException("IllegalAccess", e, pOptions);
            return 1;
        }
        return 0;
    }

    // ========================================================================
    // Commands

    /**
     * Load a Jolokia Agent and start it. Whether an agent is started is decided by the existence of the
     * system property {@see JvmAgentJdk6#JOLOKIA_AGENT_URL}.
     *
     * @param pVm the virtual machine
     * @param pOptions options as given on the command line
     * @return the exit code (0: success, 1: error)
     * @throws IllegalAccessException if call via reflection fails
     * @throws NoSuchMethodException should not happen since we use well known methods
     * @throws InvocationTargetException exception occured during startup of the agent. You probably need to examine
     *         the stdout of the instrumented process as well for error messages.
     */
    private static int commandStart(Object pVm, OptionsAndArgs pOptions) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String agentUrl = checkAgentUrl(pVm);
        boolean quiet = pOptions.options.containsKey("quiet");
        if (agentUrl == null) {
            String agent = getJarFile().getAbsolutePath();
            loadAgent(pVm, agent, pOptions.toAgentArg());
            if (!quiet) {
                System.out.println("Started Jolokia for PID " + pOptions.pid);
                System.out.println(checkAgentUrl(pVm));
            }
            return 0;
        } else {
            if (!quiet) {
                System.out.println("Jolokia already attached to " + pOptions.pid);
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
     * @param pOptions options as given on the command line
     * @return the exit code (0: success, 1: error)
     * @throws IllegalAccessException if call via reflection fails
     * @throws NoSuchMethodException should not happen since we use well known methods
     * @throws InvocationTargetException exception occured during startup of the agent. You probably need to examine
     *         the stdout of the instrumented process as well for error messages.
     */
    private static int commandStop(Object pVm, OptionsAndArgs pOptions) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String agentUrl = checkAgentUrl(pVm);
        boolean quiet = pOptions.options.containsKey("quiet");
        if (agentUrl != null) {
            String agent = getJarFile().getAbsolutePath();
            String options =  pOptions.toAgentArg();
            loadAgent(pVm, agent, options.length() != 0 ? options + ",mode=stop" : "mode=stop");
            if (!quiet) {
                System.out.println("Stopped Jolokia for PID " + pOptions.pid);
            }
            return 0;
        } else {
            if (!quiet) {
                System.out.println("Jolokia is not attached to PID " + pOptions.pid);
            }
            return 1;
        }

    }

    /**
     * Check the status of an agent on the target process.  Prints out the information
     * to standard out, except if the '--quiet' is given.
     *
     * @param pVm the virtual machine
     * @param pOptions options as given on the command line
     * @return the exit code (0: agent is attached, 1: agent is not attached.)
     * @throws IllegalAccessException if call via reflection fails
     * @throws NoSuchMethodException should not happen since we use well known methods
     * @throws InvocationTargetException exception occured during startup of the agent. You probably need to examine
     *         the stdout of the instrumented process as well for error messages.
     */
    private static int commandStatus(Object pVm, OptionsAndArgs pOptions) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String agentUrl = checkAgentUrl(pVm);
        boolean quiet = pOptions.options.containsKey("quiet");
        if (agentUrl != null) {
            if (!quiet) {
                System.out.println("Jolokia started for PID " + pOptions.pid);
                System.out.println(agentUrl);
            }
            return 0;
        } else {
            if (!quiet) {
                System.out.println("No Jolokia agent attached to PID " + pOptions.pid);
            }
            return 1;
        }
    }

    // ===============================================================================================

    // Check whether an agent is registered by checking the existance of a system property
    private static String checkAgentUrl(Object pVm) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Properties systemProperties = getAgentSystemProperties(pVm);
        return systemProperties.getProperty(JvmAgentJdk6.JOLOKIA_AGENT_URL);
    }

    // Lookup the JAR File from where this class is loaded
    private static File getJarFile() {
        try {
            return new File(JvmAgentJdk6.class
                                    .getProtectionDomain()
                                    .getCodeSource()
                                    .getLocation()
                                    .toURI());
        } catch (URISyntaxException e) {
            System.err.println("Error: Cannot lookup jar for this class: " + e);
            throw new IllegalStateException(e);
        }
    }

    private static void detachAgent(Object pVm) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        if (pVm != null) {
            Class clazz = pVm.getClass();
            Method method = clazz.getMethod("detach");
            method.invoke(pVm);
        }
    }

    private static void loadAgent(Object pVm, String pAgent, String pPid) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class clazz = pVm.getClass();
        Method method = clazz.getMethod("loadAgent",String.class, String.class);
        method.invoke(pVm,pAgent,pPid);
    }

    private static Properties getAgentSystemProperties(Object pVm) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class clazz = pVm.getClass();
        Method method = clazz.getMethod("getSystemProperties");
        return (Properties) method.invoke(pVm);
    }

    /**
     * Lookup and create a {@link com.sun.tools.attach.VirtualMachine} via reflection. First, a direct
     * lookup via {@link Class#forName(String)} is done, which will succeed for JVM on OS X, since tools.jar
     * is bundled there together with classes.zip. Next, tools.jar is tried to be found (by examine <code>java.home</code>)
     * and an own classloader is created for looking up the VirtualMachine.
     *
     * If lookup fails, a message is printed out (except when '--quiet' is provided)
     *
     * @param pOptions parsed command line options
     * @return the create virtual machine of <code>null</code> if none could be created
     */
    private static Object attachVirtualMachine(OptionsAndArgs pOptions)  {
        Class vmClass = lookupVirtualMachineClass(pOptions);
        if (vmClass == null) {
            return null;
        }

        Exception storedExp;
        String errMsg;
        try {
            Method method = vmClass.getMethod("attach",String.class);
            return method.invoke(null,pOptions.pid);
        } catch (NoSuchMethodException e) {
            errMsg = "Internal: No method 'attach' found on " + vmClass;
            storedExp = e;
        } catch (InvocationTargetException e) {
            errMsg = e.getTargetException().getMessage();
            storedExp = e;
        } catch (IllegalAccessException e) {
            errMsg = "IllegalAccess: " + e.getCause().getMessage();
            storedExp = e;
        }
        printException(errMsg, storedExp, pOptions);
        return null;
    }

    private static Class lookupVirtualMachineClass(OptionsAndArgs pOptions) {
        try {
            String vmClassName = "com.sun.tools.attach.VirtualMachine";
            try {
                return Class.forName(vmClassName);
            } catch (ClassNotFoundException exp) {
                return lookupInToolsJar(vmClassName);
            }
        } catch (Exception exp) {
            virtualMachineLookupFailed(pOptions, exp);
            return null;
        }
    }

    private static void virtualMachineLookupFailed(OptionsAndArgs pOptions, Exception exp) {
        if (!pOptions.quiet) {
            System.err.println(
"Cannot find classes from tools.jar. The heuristics for loading tools.jar which contains\n" +
"essential classes for attaching to a running JVM could locate the necessary jar file.\n" +
"\n" +
"Please call this launcher with a qualified classpath on the command line like\n" +
"\n" +
"   java -cp path/to/tools.jar:" + getJarFile().getName() + " " + AgentLauncher.class.getName() + " [options] <command> <ppid>\n"                                                            );
        }
        if (pOptions.verbose) {
            System.err.println("Stacktrace: ");
            exp.printStackTrace(System.err);
        }
    }

    private static Class lookupInToolsJar(String pVmClassName) throws MalformedURLException, ClassNotFoundException {
        // Try to look up tools.jar within $java.home, otherwise give up
        String javaHome = System.getProperty("java.home");
        String extraInfo;
        if (javaHome != null) {
            extraInfo = "JAVA_HOME is " + javaHome;
            File[] toolsJars = new File[] {
                    new File(javaHome + "/../lib/tools.jar"),
                    new File(javaHome + "/lib/tools.jar")
            };
            for (File toolsJar : toolsJars) {
                if (toolsJar.exists()) {
                    ClassLoader loader = new URLClassLoader(new URL[] {toolsJar.toURI().toURL() },AgentLauncher.class.getClassLoader());
                    return loader.loadClass(pVmClassName);
                }
            }
        } else {
            extraInfo = "No JAVA_HOME set";
        }
        throw new ClassNotFoundException("No tools.jar found (" + extraInfo + ")");
    }


    // ===============================================================
    // Command line handling

    /**
     * Parse a list of arguments. Options start with '--' (long form) or '-' (short form) and are
     * defined in {@see OPTIONS} and {@see SHORT_OPTS}. For options with arguments, the argument can
     * bei either provided in the form '--option=value' or '--option value'. Everything which is
     * not an option is considered to be an argument. Exactly two arguments are allowed: The command
     * (first) and the PID (second).
     *
     * @param pArgs arguments as given on the command line
     * @return parse structure
     * @throws IllegalArgumentException if parsing fails
     */
    private static OptionsAndArgs parseArgs(String[] pArgs) {
        Map<String,String> config = new HashMap<String, String>();

        // Parse options
        List<String> arguments = new ArrayList<String>();
        for (int i = 0; i < pArgs.length; i++) {
            String arg = pArgs[i];
            if (arg.startsWith("-")) {
                ArgParsed argParsed = parseArgument(pArgs[i], i + 1 <= pArgs.length - 1 ? pArgs[i + 1] : null);
                if (argParsed.skipNext) {
                    i++;
                }
                config.put(argParsed.option,argParsed.value);
            }  else {
                arguments.add(arg);
            }
        }
        String command = arguments.size() > 0 ? arguments.get(0) : null;
        String pid = arguments.size() > 1 ? arguments.get(1) : null;

        return new OptionsAndArgs(command,pid,config);
    }

    private static final Pattern ARGUMENT_PATTERN_WITH_EQUAL = Pattern.compile("([^=]+)=(.*)");

    private static ArgParsed parseArgument(String pArg, String pNextArgument) {
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
                if (pNextArgument == null && value == null) {
                    throw new IllegalArgumentException("Option '" + opt + "' requires an argument");
                }
                return value != null ? new ArgParsed(opt, value, false) : new ArgParsed(opt,pNextArgument,true);
            } else if (OPTIONS.contains(opt + "!")) {
                return new ArgParsed(opt,"true",false);
            } else {
                throw new IllegalArgumentException("Unknown option '" + opt + "'");
            }
        } else if (pArg.startsWith("-")) {
            // Short option
            String opt = pArg.substring(1);
            String longOpt = SHORT_OPTS.get(opt);
            if (longOpt == null) {
                throw new IllegalArgumentException("No short option '" + opt + "' known");
            }
            return parseArgument("--" + longOpt,pNextArgument);
        } else {
            throw new IllegalArgumentException("No option '" + pArg + "' quiet known");
        }
    }

    private static void printException(String pMessage, Exception pException, OptionsAndArgs pOaa) {
        if (!pOaa.quiet) {
            System.err.println(pMessage + ": " + pException);
        }
        if (pOaa.verbose) {
            pException.printStackTrace(System.err);
        }
    }

    private static int commandHelp() {
        String jar = getJarFile().getName();
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
"    --port <port>                 Port to listen on (default: " + JvmAgentJdk6.DEFAULT_PORT + ")\n" +
"    --agentContext <context>      HTTP Context under which the agent is reachable (default: " + JvmAgentJdk6.JOLOKIA_CONTEXT + ")\n" +
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
        return 0;
    }

    // -------------------------------------------------------------------------------------------------
    // Helper classes

    // Options and arguments
    private static final class OptionsAndArgs {
        private String command;
        private String pid;
        private Map<String,String> options;

        private boolean quiet;
        private boolean verbose;

        private OptionsAndArgs(String pCommand, String pPid, Map<String, String> pOptions) {
            command = pCommand;
            pid = pPid;
            options = pOptions;
            quiet = options.containsKey("quiet");
            verbose = options.containsKey("verbose");

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
                if (command == null) {
                    throw new IllegalArgumentException("No command given");
                }
                if (pid == null) {
                    throw new IllegalArgumentException("No process id (PID) given");
                }
            }
        }

        public String toAgentArg() {
            StringBuilder arg = new StringBuilder();
            for (Map.Entry<String,String> entry : options.entrySet()) {
                if (!entry.getKey().equals("quiet") && !entry.getKey().equals("verbose")) {
                    arg.append(entry.getKey()).append("=").append(entry.getValue()).append(",");
                }
            }
            return arg.length() > 0 ? arg.substring(0,arg.length() - 1) : "";
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

}
