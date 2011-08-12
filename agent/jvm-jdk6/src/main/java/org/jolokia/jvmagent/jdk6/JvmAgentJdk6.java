package org.jolokia.jvmagent.jdk6;

import java.io.*;
import java.util.*;

import org.jolokia.util.ConfigKey;

/*
 *  Copyright 2009-2010 Roland Huss
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
 * A JVM level agent using the JDK6 HTTP Server {@link com.sun.net.httpserver.HttpServer} or
 * its SSL variant {@link com.sun.net.httpserver.HttpsServer}.
 *
 * Beside the configuration defined in {@link ConfigKey}, this agent honors the following
 * additional configuration keys:
 *
 * <ul>
 *  <li><strong>host</strong> : Host address to bind to
 *  <li><strong>port</strong> : Port to listen on
 *  <li><strong>backlog</strong> : max. nr of requests queued up before they get rejected
 *  <li><strong>config</strong> : path to a properties file containing configuration
 *  <li>....</li>
 * </ul>
 *
 * Configuration will be also looked up from a properties file found in the class path as
 * <code>/jolokia-agent.properties</code>
 *
 * All configurations will be merged in the following order with the later taking precedence:
 *
 * <ul>
 *   <li>Default properties from <code>/jolokia-agent.properties<code>
 *   <li>Configuration from a config file (if given)
 *   <li>Options given on the command line in the form
 *       <code>-javaagent:agent.jar=key1=value1,key2=value2...</code>
 * </ul>
 * @author roland
 * @since Mar 3, 2010
 */
@SuppressWarnings("PMD.SystemPrintln" )
public final class JvmAgentJdk6 {

    private static JolokiaServer server;

    public static final String JOLOKIA_AGENT_URL = "jolokia.agent";

    private JvmAgentJdk6() {}

    /**
     * Entry point for the agent, using command line attach
     * (that is via -javagent command line argument)
     *
     * @param agentArgs arguments as given on the command line
     */
    public static void premain(String agentArgs) {
        try {
            startAgent(parseArgs(agentArgs));
        } catch(IOException ioe) {
            System.err.println("Jolokia: Cannot create HTTP-Server: " + ioe);
        }
    }

    /**
     * Entry point for the agent, using dynamic attach
     * (this is post VM initialisation attachment, via com.sun.attach)
     *
     * @param agentArgs arguments as given on the command line
     */
    public static void agentmain(String agentArgs) {
        try {
            Map<String,String> agentConfig = parseArgs(agentArgs);
            if ("stop".equals(agentConfig.get("mode"))) {
                stopAgent();
            } else {
                startAgent(agentConfig);
            }
        } catch (IOException ioe) {
            System.err.println("Jolokia: Error starting agent: " + ioe);
        }
    }

    private static void startAgent(Map<String, String> agentConfig) throws IOException {
        server = new JolokiaServer(agentConfig);

        String url = server.getUrl();
        System.setProperty(JOLOKIA_AGENT_URL, url);
        System.out.println("Jolokia: Agent started with URL " + url);

        server.start();
    }

    private static void stopAgent() {
        System.clearProperty(JOLOKIA_AGENT_URL);
        System.out.println("Jolokia: Agent stopped");
        server.stop();
    }

    // ==============================================================================================

    private static Map<String, String> parseArgs(String pAgentArgs) {
        Map<String,String> ret = new HashMap<String, String>();
        if (pAgentArgs != null && pAgentArgs.length() > 0) {
            for (String arg : pAgentArgs.split(",")) {
                String[] prop = arg.split("=");
                if (prop == null || prop.length != 2) {
                    System.err.println("jolokia: Invalid option '" + arg + "'. Ignoring");
                } else {
                    ret.put(prop[0],prop[1]);
                }
            }
        }
        Map<String,String> config = getDefaultConfig();
        if (ret.containsKey("config")) {
            Map<String,String> userConfig = readConfig(ret.get("config"));
            config.putAll(userConfig);
            config.putAll(ret);
            return config;
        } else {
            config.putAll(ret);
            return config;
        }
    }

    private static Map<String, String> readConfig(String pFilename) {
        File file = new File(pFilename);
        try {
            InputStream is = new FileInputStream(file);
            return readPropertiesFromInputStream(is,pFilename);
        } catch (FileNotFoundException e) {
            System.err.println("jolokia: Configuration file " + pFilename + " does not exist");
            return new HashMap<String, String>();
        }
    }

    private static Map<String, String> getDefaultConfig() {
        InputStream is =
                Thread.currentThread().getContextClassLoader().getResourceAsStream("jolokia-agent.properties");
        return readPropertiesFromInputStream(is,"jolokia-agent.properties");
    }

    private static Map<String, String> readPropertiesFromInputStream(InputStream pIs,String pLabel) {
        Map ret = new HashMap<String, String>();
        if (pIs == null) {
            return ret;
        }
        Properties props = new Properties();
        try {
            props.load(pIs);
            ret.putAll(props);
        } catch (IOException e) {
            System.err.println("jolokia: Cannot load default properties " + pLabel + " : " + e);
        }
        return ret;
    }


}
