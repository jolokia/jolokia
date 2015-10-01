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

import org.jolokia.util.EscapeUtil;

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
     * Constructor which parser an agent argument string
     *
     * @param pArgs arguments glued together as provided on the commandline
     *        for an agent parameter
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
        super(pConfig);
    }

    @Override
    /** {@inheritDoc} */
    protected void init(Map<String, String> pConfig) {
        super.init(pConfig);
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


    /**
     * Beside reading the default configuration from an internal property file,
     * also add extra configuration given in an external properties where the path
     * to this property file is given under the key "config"
     *
     * @param pConfig the configuration provided during construction
     * @return the default configuration used as fallback
     */
    @Override
    protected Map<String, String> getDefaultConfig(Map<String,String> pConfig) {
        Map<String,String> config = super.getDefaultConfig(pConfig);
        if (pConfig.containsKey("config")) {
            config.putAll(readConfig(pConfig.get("config")));
        }
        return config;
    }

    // ======================================================================================
    // Parse argument

    // Split arguments into a map
    private static Map<String, String> split(String pAgentArgs) {
        Map<String,String> ret = new HashMap<String, String>();
        if (pAgentArgs != null && pAgentArgs.length() > 0) {
            for (String arg : EscapeUtil.splitAsArray(pAgentArgs, EscapeUtil.CSV_ESCAPE, ",")) {
                String[] prop = arg.split("=",2);
                if (prop == null || prop.length != 2) {
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
