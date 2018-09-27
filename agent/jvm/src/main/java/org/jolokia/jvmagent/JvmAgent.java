package org.jolokia.jvmagent;

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

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.List;

import org.jolokia.backend.MBeanServerHandler;
import org.jolokia.detector.ServerDetector;


/**
 * A JVM level agent using the JDK6 HTTP Server {@link com.sun.net.httpserver.HttpServer} or
 * its SSL variant {@link com.sun.net.httpserver.HttpsServer}.
 *
 * Beside the configuration defined in {@link org.jolokia.config.ConfigKey}, this agent honors the following
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
 * <code>/default-jolokia-agent.properties</code>
 *
 * All configurations will be merged in the following order with the later taking precedence:
 *
 * <ul>
 *   <li>Default properties from <code>/default-jolokia-agent.properties<code>
 *   <li>Configuration from a config file (if given)
 *   <li>Options given on the command line in the form
 *       <code>-javaagent:agent.jar=key1=value1,key2=value2...</code>
 * </ul>
 * @author roland
 * @since Mar 3, 2010
 */
@SuppressWarnings("PMD.SystemPrintln" )
public final class JvmAgent {

    private static JolokiaServer server;

    public static final String JOLOKIA_AGENT_URL = "jolokia.agent";

    private JvmAgent() {}

    /**
     * Entry point for the agent, using command line attach
     * (that is via -javaagent command line argument)
     *
     * @param agentArgs arguments as given on the command line
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        startAgent(new JvmAgentConfig(agentArgs), true /* register and detect lazy */, inst);
    }

    /**
     * Entry point for the agent, using dynamic attach
     * (this is post VM initialisation attachment, via com.sun.attach)
     *
     * @param agentArgs arguments as given on the command line
     */
    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        JvmAgentConfig config = new JvmAgentConfig(agentArgs);
        if (!config.isModeStop()) {
            startAgent(config,false, instrumentation);
        } else {
            stopAgent();
        }
    }

    private static void startAgent(final JvmAgentConfig pConfig, final boolean pLazy, final Instrumentation instrumentation)  {
        // start the JolokiaServer in a new daemon thread
        Thread jolokiaStartThread = new Thread("JolokiaStart") {
            public void run() {
                try {
                    // block until the server supporting early detection is initialized
                    awaitServerInitialization(pConfig, instrumentation);

                    server = new JolokiaServer(pConfig,pLazy);

                    server.start();
                    setStateMarker();

                    System.out.println("Jolokia: Agent started with URL " + server.getUrl());
                } catch (RuntimeException exp) {
                    System.err.println("Could not start Jolokia agent: " + exp);
                } catch (IOException exp) {
                    System.err.println("Could not start Jolokia agent: " + exp);
                }
            }
        };
        jolokiaStartThread.setDaemon(true);
        jolokiaStartThread.start();
    }

    /**
     * Lookup the server detectors and notify detector about the JVM startup
     *
     * @param instrumentation
     * @see ServerDetector#jvmAgentStartup(Instrumentation)
     */
    private static void awaitServerInitialization(JvmAgentConfig pConfig, final Instrumentation instrumentation) {
        List<ServerDetector> detectors = MBeanServerHandler.lookupDetectors();
        for (ServerDetector detector : detectors) {
            detector.jvmAgentStartup(instrumentation);
        }
    }

    private static void stopAgent() {
        try {
            server.stop();
            clearStateMarker();
        } catch (RuntimeException exp) {
            System.err.println("Could not stop Jolokia agent: " + exp);
        }
    }

    private static void setStateMarker() {
        String url = server.getUrl();
        System.setProperty(JOLOKIA_AGENT_URL, url);
    }

    private static void clearStateMarker() {
        System.clearProperty(JOLOKIA_AGENT_URL);
        System.out.println("Jolokia: Agent stopped");
    }
}
