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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import org.jolokia.server.core.config.ConfigKey;
import org.jolokia.server.core.detector.ServerDetector;
import org.jolokia.server.core.detector.ServerDetectorLookup;
import org.jolokia.server.core.service.impl.CachingServerDetectorLookup;
import org.jolokia.server.core.service.impl.ClasspathServerDetectorLookup;
import org.jolokia.server.core.service.impl.StdoutLogHandler;


/**
 * A JVM level agent using the JDK11+ HTTP Server {@link com.sun.net.httpserver.HttpServer} or
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

    // info to preserve on server restart without restarting entire JVM
    private static Instrumentation instrumentation;
    private static JvmAgentConfig config;
    private static boolean lazy;
    private static JolokiaWatcher jolokiaWatchThread;

    // CRCs of watched files
    private static final Map<String, Long> crcs = new HashMap<>();

    // System property used for communicating the agent's state
    public static final String JOLOKIA_AGENT_URL = "jolokia.agent";

    // This Java agent classes is supposed to be used by the Java attach API only
    private JvmAgent() {}

    /**
     * Entry point for the agent, using command line attach
     * (that is via -javaagent command line argument)
     *
     * @param agentArgs arguments as given on the command line
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        startAgent(new JvmAgentConfig(agentArgs),true /* lazy */, inst);
    }

    /**
     * Entry point for the agent, using dynamic attach.
     * (this is a post VM initialisation attachment, via com.sun.attach)
     *
     * @param agentArgs arguments as given on the command line
     */
    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        JvmAgentConfig config = new JvmAgentConfig(agentArgs);
        if (!config.isModeStop()) {
            startAgent(config,false, instrumentation);
        } else {
            stopAgent(true);
        }
    }

    private static void startAgent(final JvmAgentConfig pConfig, final boolean pLazy, final Instrumentation instrumentation)  {
        JvmAgent.instrumentation = instrumentation;
        JvmAgent.config = pConfig;
        JvmAgent.lazy = pLazy;

        // start the JolokiaServer in a new daemon thread
        Thread jolokiaStartThread = new Thread("JolokiaStart") {
            public void run() {
                try {
                    // block until the server supporting early detection is initialized
                    ServerDetectorLookup lookup = new CachingServerDetectorLookup(new ClasspathServerDetectorLookup());
                    ClassLoader loader = awaitServerInitialization(instrumentation, lookup);
                    pConfig.setClassLoader(loader);

                    server = new JolokiaServer(pConfig, lookup);
                    synchronized (this) {
                        server.start(pLazy);
                        setStateMarker();
                        configureWatcher(server, pConfig);
                    }

                    System.out.println("Jolokia: Agent started with URL " + server.getUrl());
                } catch (RuntimeException | IOException exp) {
                    System.err.println("Could not start Jolokia agent: " + exp);
                }
            }
        };
        jolokiaStartThread.setDaemon(true);

        // Ensure LogManager is initialized before starting the JolokiaStart thread.
        // https://github.com/jolokia/jolokia/issues/535 - sun.net.httpserver.ServerImpl constructor may also
        // concurrently lead to LogManager initialization
        System.getLogger("org.jolokia.agent");

        jolokiaStartThread.start();
    }

    /**
     * Lookup the server detectors and notify detector about the JVM startup
     *
     * @param instrumentation instrumentation used for accessing services
     * @see ServerDetector#jvmAgentStartup(Instrumentation)
     */
    private static ClassLoader awaitServerInitialization(final Instrumentation instrumentation, ServerDetectorLookup lookup) {
        Set<ServerDetector> detectors = lookup.lookup(new StdoutLogHandler());

        // if some detector (only one!) gives us a ClassLoader, we can use it instead of getClass().getClassLoader()
        // to perform Jolokia Service Manager initialization
        ServerDetector activeDetector = null;
        ClassLoader highOrderClassLoader = null;
        for (ServerDetector detector : detectors) {
            ClassLoader cl = detector.jvmAgentStartup(instrumentation);
            if (cl != null) {
                if (highOrderClassLoader != null) {
                    System.err.printf("Invalid ServerDetector configuration. Detector \"%s\" already provided" +
                        " a classloader and different detector (\"%s\") overrides it.",
                        activeDetector, detector);
                    throw new RuntimeException("Invalid ServerDetector configuration");
                } else {
                    highOrderClassLoader = cl;
                    activeDetector = detector;
                }
            }
        }

        return highOrderClassLoader;
    }

    private static void stopAgent(boolean waitForWatcher) {
        try {
            if (server != null) {
                synchronized (JvmAgent.class) {
                    server.stop();
                    clearStateMarker();
                    stopWatcher(waitForWatcher);
                    server = null;
                }
            }
        } catch (RuntimeException exp) {
            System.err.println("Could not stop Jolokia agent: " + exp);
            exp.printStackTrace();
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

    /**
     * If needed, configure a certificate/key watcher for HTTPS server
     */
    private static void configureWatcher(JolokiaServer server, JvmAgentConfig pConfig) {
        if (pConfig.useHttps() && pConfig.useCertificateReload() > 0) {
            try {
                List<File> files = server.getWatchedFiles();
                crcs.clear();
                for (File file : files) {
                    crcs.put(file.getCanonicalPath(), calculateCrc(file.getPath()));
                }
                JolokiaWatcher jolokiaWatchThread = new JolokiaWatcher(crcs, pConfig.useCertificateReload());
                jolokiaWatchThread.setDaemon(true);
                jolokiaWatchThread.start();
                System.out.println("Jolokia: Registered watcher for certificate changes (poller: "
                    + pConfig.useCertificateReload() + "s, files: "
                    + files.stream().map(File::getName).collect(Collectors.joining(", ")) + ")");
                JvmAgent.jolokiaWatchThread = jolokiaWatchThread;
            } catch (IOException e) {
                System.err.println("Jolokia: FileSystem watch service unavailable: " + e.getMessage());
            }
        }
    }

    private static long calculateCrc(String file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            CRC32 crc = new CRC32();
            while ((read = fis.read(buffer)) > 0) {
                crc.update(buffer, 0, read);
            }
            return crc.getValue();
        } catch (IOException e) {
            return -1;
        }
    }

    private static void stopWatcher(boolean waitForWatcher) {
        if (jolokiaWatchThread != null) {
            jolokiaWatchThread.setRunning(false);
            if (waitForWatcher) {
                try {
                    jolokiaWatchThread.join();
                    System.out.println("Jolokia: Stopped certificate watcher");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            jolokiaWatchThread = null;
            server.clearWatchedFiles();
        }
    }

    /**
     * A thread to watch for certificate changes.
     */
    private static class JolokiaWatcher extends Thread {
        private final Map<String, Long> crcs;
        private final int interval;
        private volatile boolean running = true;

        public JolokiaWatcher(Map<String, Long> crcs, int interval) {
            super("JolokiaCertificateWatcher");
            this.crcs = crcs;
            this.interval = interval;
        }

        public void run() {
            while (running) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(interval);
                    boolean change = false;
                    List<String> files = new ArrayList<>(crcs.keySet());
                    for (String f : files) {
                        if (calculateCrc(f) != crcs.get(f)) {
                            change = true;
                            break;
                        }
                    }

                    if (change) {
                        // additional wait for certificates to be written completely (...)
                        //noinspection BusyWait
                        Thread.sleep(interval);
                        System.out.println("Jolokia: Certificate(s) updated, restarting Jolokia agent");
                        stopAgent(false);
                        startAgent(JvmAgent.config, JvmAgent.lazy, JvmAgent.instrumentation);
                    }
                } catch (InterruptedException e) {
                    System.out.println("Jolokia: JolokiaCertificateWatcher stopped");
                }
            }
        }

        public void setRunning(boolean running) {
            this.running = running;
        }
    }
}
