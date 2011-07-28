package org.jolokia.jvmagent.jdk6;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.servicetag.SystemEnvironment;

/**
 * Launcher for attaching/detaching a Jolokia agent dynamically to an already
 * running Java process.
 *
 * This launcher tries hard to detect the required classes from tools.jar dynamically. For Mac OSX
 * these classes are already included, for other they are looked up within JAVA_HOME
 * (pointed to by the system property java.home)
 *
 * @author roland
 * @since 28.07.11
 */
public class AgentLauncher {

    /**
     * Main method for attaching agent to a running JVM program
     * @param args command line arguments
     */
    public static void main(String... args) {
        OptionsAndArgs options = null;
        try {
            options = parseArgs(args);
        } catch (IllegalArgumentException exp) {
            System.err.println("Error: " + exp.getMessage() + "\n");
            usage();
            System.exit(1);
        }

        if (options.options.containsKey("help")) {
            usage();
            System.exit(0);
        }

        Object vm = getVirtualMachine(options);
        if (vm == null) {
            System.exit(1);
        }
        int exitCode = 0;
        try {
            if ("start".equals(options.command)) {
                exitCode = commandStart(vm, options);
            } else if ("status".equals(options.command)) {
                exitCode = commandStatus(vm,options);
            } else if ("stop".equals(options.command)) {
                exitCode = commandStop(vm,options);
            } else {
                throw new IllegalArgumentException("Unknown command '" + options.command + "'");
            }
        } catch (Exception exp) {
            printException("Error while processing command '" + options.command + "'",exp,options);
            exitCode = 1;
        }  finally {
            try {
                detachAgent(vm);
            } catch (Exception e) {
                printException("Error while detaching",e,options);
                exitCode = 1;
            }
        }
        System.exit(exitCode);
    }

    // ========================================================================
    // Commands

    private static int commandStart(Object pVm, OptionsAndArgs pOptions) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        String agentUrl = checkAgentUrl(pVm);
        boolean quiet = pOptions.options.containsKey("quiet");
        if (agentUrl == null) {
            String agent = getJarFile().getAbsolutePath();
            loadAgent(pVm, agent, pOptions.toAgentArg());
            if (!quiet) {
                System.out.println("Started Jolokia for PID " + pOptions.pid);
                System.out.println("URL: " + checkAgentUrl(pVm));
            }
            return 0;
        } else {
            if (!quiet) {
                System.out.println("Jolokia already attached to " + pOptions.pid + " with URL " + agentUrl);
            }
            return 1;
        }
    }

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

    private static int commandStatus(Object pVm, OptionsAndArgs pOaa) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        String agentUrl = checkAgentUrl(pVm);
        boolean quiet = pOaa.options.containsKey("quiet");
        if (agentUrl != null) {
            if (!quiet) {
                System.out.println("Jolokia started for PID " + pOaa.pid + " with URL " + agentUrl);
            }
            return 0;
        } else {
            if (!quiet) {
                System.out.println("No Jolokia agent attached to " + pOaa.pid);
            }
            return 1;
        }
    }


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
            throw new RuntimeException(e);
        }
    }

    private static void detachAgent(Object pVm) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Class clazz = pVm.getClass();
        Method method = clazz.getMethod("detach");
        method.invoke(pVm);
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

    private static Object getVirtualMachine(OptionsAndArgs pOptions)  {
        Class vmClass;
        try {
            String vmClassName = "com.sun.tools.attach.VirtualMachine";
            try {
                vmClass = Class.forName(vmClassName);
            } catch (ClassNotFoundException exp) {
                vmClass = lookupInToolsJar(vmClassName);
            }
        } catch (Exception exp) {
            virtualMachineLookupFailed(pOptions, exp);
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
            errMsg = "InvocationTarget: " + e.getCause().getMessage();
            storedExp = e;
        } catch (IllegalAccessException e) {
            errMsg = "IllegalAccess: " + e.getCause().getMessage();
            storedExp = e;
        }
        printException(errMsg, storedExp, pOptions);
        return null;
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
            exp.printStackTrace();
        }
    }

    private static Class lookupInToolsJar(String pVmClassName) throws MalformedURLException, ClassNotFoundException {
        // Try to look up tools.jar within $java.home, otherwise give up
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            File[] toolsJars = new File[] {
                    new File(javaHome + "/lib/tools.jar"),
                    new File(javaHome + "/../lib/tools.jar"),
            };
            for (File toolsJar : toolsJars) {
                if (toolsJar.exists()) {
                    ClassLoader loader = new URLClassLoader(new URL[] {toolsJar.toURI().toURL() },AgentLauncher.class.getClassLoader());
                    return loader.loadClass(pVmClassName);
                }
            }
        } else {
            System.out.println("No Java-Home set");
        }
        throw new RuntimeException("No tools.jar found");
    }


    // ===============================================================
    // Command line handling

    private static OptionsAndArgs parseArgs(String[] pArgs) {
        Map<String,String> config = new HashMap<String, String>();
        String command,pid;

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

        if (arguments.size() != 2) {
            throw new IllegalArgumentException("Command and process id (PID) must be provided as arguments");
        }
        command = arguments.get(0);
        if (command == null) {
            throw new IllegalArgumentException("No command given");
        }
        pid = arguments.get(1);
            if (pid == null) {
                throw new IllegalArgumentException("No process id (PID) given");
            }

        return new OptionsAndArgs(command, pid, config);
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

    private static void usage() {
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
"\n" +
"[options] are used for providing runtime information for attaching the agent:\n" +
"\n" +
"    --host <host>                Hostname or IP address to which to bind on (default: InetAddress.getLocalHost())\n" +
"    --port <port>                Port to listen on (default: " + JvmAgentJdk6.DEFAULT_PORT + ")\n" +
"    --agentContext <context>     HTTP Context under which the agent is reachable (default: " + JvmAgentJdk6.JOLOKIA_CONTEXT + ")\n" +
"    --user <user>                User used for Basic-Authentication\n" +
"    --password <password>        Password used for Basic-Authentication\n" +
"    --quiet                      No output. \"status\" will exit with code 0 if the agent is running, 1 otherwise\n" +
"    --verbose                    Verbose output\n" +
"    --executor <executor>        Executor policy for HTTP Threads to use (default: single)\n" +
"                                 \"fixed\"    Thread pool with a fixed number of threads (default: 5)\n" +
"                                 \"cached\"   Cached Thread Pool, creates threads on demand\n" +
"                                 \"single\"   Single Thread\n" +
"    --threadNr <nr threads>      Number of fixed threads if \"fixed\" is used as executor\n" +
"    --backlog <backlog>          How many request to keep in the backlog (default: 10)\n" +
"    --protocol <http|https>      Protocol which must be either \"http\" or \"https\" (default: http)\n" +
"    --keystore <keystore>        Path to keystore (https only)\n" +
"    --keystorePassword <pwd>     Password to the keystore (https only)\n" +
"    --useSslClientAuthentication Use client certificate authentication (https only)\n" +
"    --config <configfile>        Path to a property file from where to read the configuration\n" +
"    --help                       This help documentation\n" +
"\n" +
"For more information refer to www.jolokia.org"
                          );
    }

    // -------------------------------------------------------------------------------------------------
    // Helper classes

    // Options and arguments
    private static class OptionsAndArgs {
        String command;
        String pid;
        Map<String,String> options;

        boolean quiet;
        boolean verbose;

        private OptionsAndArgs(String pCommand, String pPid, Map<String, String> pOptions) {
            command = pCommand;
            pid = pPid;
            options = pOptions;
            quiet = options.containsKey("quiet");
            verbose = options.containsKey("verbose");
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
    private static class ArgParsed {
        boolean skipNext;
        String  option;
        String  value;

        private ArgParsed(String pOption, String pValue, boolean pSkipNext) {
            skipNext = pSkipNext;
            option = pOption;
            value = pValue;
        }
    }


    // ===================================================================================
    // Available options

    private static Map<String,String> SHORT_OPTS = new HashMap<String, String>();
    private static final Set<String> OPTIONS = new HashSet<String>(Arrays.asList(
                "host", "port", "agentContext", "user", "password",
                "quiet!", "verbose!", "executor", "threadNr",
                "backlog", "protocol", "keystore", "keystorePassword",
                "useSslClientAuthentication!",
                "config", "help!"));

    static {
        String short_opts_def[] = {
            "h", "host",
            "u", "user",
            "p", "password",
            "c", "agentContext",
            "v", "verbose",
            "q", "quiet"
        };

        for (int i = 0; i < short_opts_def.length; i += 2) {
            SHORT_OPTS.put(short_opts_def[i],short_opts_def[i+1]);
        }
    }

}
