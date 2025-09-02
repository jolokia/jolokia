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

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import org.jolokia.server.core.config.SystemPropertyMode;
import org.jolokia.server.core.util.EscapeUtil;

/**
 * Holds all Http-Server and Jolokia configuration.
 *
 * Default values are first loaded from the <code>default-jolokia-agent.properties</code>
 * from the class path (top-level). All default values are defined within this file.
 *
 * @author roland
 * @since 13.08.11
 */
public class JvmAgentConfig extends JolokiaServerConfig {

    // Validated properties
    private boolean isStopMode;

    /**
     * Constructor which also specifies whether initialization should be done lazy or not
     *
     * @param pArgs arguments as given on the command line
     */
    public JvmAgentConfig(String pArgs) {
        this(split(pArgs));
    }

    /**
     * Constructor with a preparsed configuration
     *
     * @param pConfig config map with key value pairs
     */
    public JvmAgentConfig(Map<String,String> pConfig) {
        Map<String,String> defaultConfig = getDefaultConfig();

        // If the key 'config' in the configuration file point to another properties file, read this in, too.
        if (pConfig.containsKey("config")) {
            defaultConfig.putAll(readConfig(pConfig.get("config")));
        }
        // config read from "config=/file" option can be overridden by other options specified for agent
        init(pConfig, defaultConfig, SystemPropertyMode.FALLBACK);

        // Special mode used by the client in order to indicate whether to stop/start the server.
        initMode(pConfig);
    }


    /**
     * The mode is 'stop' indicates that the server should be stopped when used in dynamic mode
     * @return the running mode
     */
    public boolean isModeStop() {
        return isStopMode;
    }

    // ==========================================================================================================

    private void initMode(Map<String, String> agentConfig) {
        String mode = agentConfig.get("mode");
        if (mode != null && !mode.equals("start") && !mode.equals("stop")) {
            throw new IllegalArgumentException("Invalid running mode '" + mode + "'. Must be either 'start' or 'stop'");
        }
        isStopMode = "stop".equals(mode);
    }

    // ======================================================================================
    // Parse argument

    // Split arguments into a map
    private static Map<String, String> split(String pAgentArgs) {
        Map<String,String> ret = new HashMap<>();
        if (pAgentArgs != null && !pAgentArgs.isEmpty()) {
            for (String arg : EscapeUtil.splitAsArray(pAgentArgs, EscapeUtil.CSV_ESCAPE, ",")) {
                String[] prop = arg.split("=",2);
                if (prop.length != 2) {
                    throw new IllegalArgumentException("jolokia: Invalid option '" + arg + "'");
                } else {
                    ret.put(prop[0],prop[1]);
                }
            }
        }
        return ret;
    }

    private Map<String, String> readConfig(String pFilename) {
        File file = new File(pFilename);
        try {
            InputStream is = new FileInputStream(file);
            return readPropertiesFromInputStream(is,pFilename);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("jolokia: Can not find configuration file " + pFilename,e);
        }
    }
}
